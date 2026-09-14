package com.example.footballbooking.ui.customer.payment;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.example.footballbooking.BuildConfig;
import com.example.footballbooking.databinding.ActivityPaymentBinding;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * PaymentActivity — Tích hợp VNPay Sandbox qua WebView.
 *
 * LUỒNG THANH TOÁN:
 * 1. Activity nhận bookingId + totalAmount từ Intent
 * 2. Build VNPay payment URL với HMAC-SHA512 signature
 * 3. Mở URL trong WebView
 * 4. Override WebViewClient.shouldOverrideUrlLoading() để intercept callback URL
 * 5. Parse kết quả → update paymentStatus trong Firestore
 *
 * URL CALLBACK PATTERN VNPay:
 * vnpay://success?vnp_ResponseCode=00&vnp_TxnRef={bookingId}
 * vnpay://failed?vnp_ResponseCode=24  (User hủy)
 *
 * CHÚ Ý SANDBOX:
 * - Dùng thẻ test: 9704198526191432198, exp 07/15, OTP 123456
 * - Terminal ID & Secret từ https://sandbox.vnpayment.vn/merchantv2
 */
public class PaymentActivity extends AppCompatActivity {

    // VNPay Sandbox constants
    private static final String VNP_TMN_CODE   = "YOUR_TMN_CODE";   // Điền TMN Code từ sandbox
    private static final String VNP_RETURN_URL = "vnpay://payment_result"; // Deep link callback

    private ActivityPaymentBinding binding;
    private String bookingId;
    private long totalAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bookingId   = getIntent().getStringExtra(Constants.EXTRA_BOOKING_ID);
        totalAmount = getIntent().getLongExtra("extra_amount", 0);

        if (bookingId == null || totalAmount == 0) { finish(); return; }

        binding.toolbar.setNavigationOnClickListener(v -> showCancelConfirm());

        setupWebView();
        buildAndLoadPaymentUrl();
    }

    private void setupWebView() {
        WebSettings settings = binding.webViewPayment.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        binding.webViewPayment.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                binding.progressPayment.setProgress(newProgress);
                binding.progressPayment.setVisibility(
                        newProgress == 100 ? View.GONE : View.VISIBLE);
            }
        });

        binding.webViewPayment.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                binding.progressPayment.setVisibility(View.VISIBLE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Intercept callback URL từ VNPay
                if (url.startsWith("vnpay://")) {
                    handlePaymentCallback(url);
                    return true; // Không load URL này vào WebView
                }
                return false; // Cho WebView tự handle
            }
        });
    }

    /**
     * Build VNPay payment URL theo tài liệu kỹ thuật VNPay 2.1.0
     *
     * Tham số bắt buộc:
     * vnp_Version, vnp_Command, vnp_TmnCode, vnp_Amount (×100),
     * vnp_CreateDate, vnp_CurrCode, vnp_IpAddr, vnp_Locale,
     * vnp_OrderInfo, vnp_OrderType, vnp_ReturnUrl, vnp_TxnRef
     */
    private void buildAndLoadPaymentUrl() {
        TreeMap<String, String> params = new TreeMap<>(); // TreeMap tự sort theo key — quan trọng cho HMAC

        String createDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                .format(new Date());

        params.put("vnp_Version",    "2.1.0");
        params.put("vnp_Command",    "pay");
        params.put("vnp_TmnCode",    VNP_TMN_CODE);
        params.put("vnp_Amount",     String.valueOf(totalAmount * 100)); // VNPay nhân 100
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_CurrCode",   "VND");
        params.put("vnp_IpAddr",     "127.0.0.1");
        params.put("vnp_Locale",     "vn");
        params.put("vnp_OrderInfo",  "Dat san bong: " + bookingId);
        params.put("vnp_OrderType",  "other");
        params.put("vnp_ReturnUrl",  VNP_RETURN_URL);
        params.put("vnp_TxnRef",     bookingId);

        // Tính expire date (+15 phút)
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        cal.add(Calendar.MINUTE, 15);
        params.put("vnp_ExpireDate",
                new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(cal.getTime()));

        // Build query string để ký
        StringBuilder queryBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (queryBuilder.length() > 0) queryBuilder.append("&");
            queryBuilder.append(entry.getKey()).append("=").append(entry.getValue());
        }

        // HMAC-SHA512 signature
        String secretKey = BuildConfig.VNPAY_HASH_SECRET;
        String signature = hmacSHA512(secretKey, queryBuilder.toString());
        params.put("vnp_SecureHash", signature);

        // Build final URL
        StringBuilder finalUrl = new StringBuilder(Constants.VNPAY_BASE_URL)
                .append("paymentv2/vpcpay.html?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) finalUrl.append("&");
            try {
                finalUrl.append(entry.getKey()).append("=")
                        .append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (Exception ignored) {
                finalUrl.append(entry.getKey()).append("=").append(entry.getValue());
            }
            first = false;
        }

        binding.webViewPayment.loadUrl(finalUrl.toString());
    }

    /**
     * Xử lý callback URL sau khi user hoàn tất thanh toán.
     * VNPay trả về: vnpay://payment_result?vnp_ResponseCode=00&vnp_TxnRef=...
     *
     * ResponseCode:
     * "00" = Thành công
     * "24" = Khách hủy
     * Khác = Thất bại
     */
    private void handlePaymentCallback(String callbackUrl) {
        try {
            android.net.Uri uri = android.net.Uri.parse(callbackUrl);
            String responseCode = uri.getQueryParameter("vnp_ResponseCode");
            String txnRef       = uri.getQueryParameter("vnp_TxnRef");

            if ("00".equals(responseCode)) {
                // Thanh toán thành công
                updatePaymentInFirestore(txnRef, Constants.PAYMENT_PAID,
                        uri.getQueryParameter("vnp_TransactionNo"));
                showPaymentResult(true, "Thanh toán thành công! ✅");
            } else if ("24".equals(responseCode)) {
                // Khách hủy
                showPaymentResult(false, "Bạn đã hủy thanh toán");
            } else {
                showPaymentResult(false, "Thanh toán thất bại (Code: " + responseCode + ")");
            }
        } catch (Exception e) {
            showPaymentResult(false, "Lỗi xử lý kết quả thanh toán");
        }
    }

    /** Cập nhật trạng thái thanh toán trong Firestore sau khi VNPay callback */
    private void updatePaymentInFirestore(String bookingId, String paymentStatus,
                                          String transactionNo) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("paymentStatus", paymentStatus);
        updates.put("transactionId", transactionNo != null ? transactionNo : "");

        FirebaseFirestore.getInstance()
                .collection(Constants.COL_BOOKINGS)
                .document(bookingId)
                .update(updates)
                .addOnFailureListener(e -> {
                    // Log lỗi nhưng không crash — payment đã thành công
                    android.util.Log.e("Payment", "Failed to update Firestore", e);
                });
    }

    private void showPaymentResult(boolean success, String message) {
        binding.webViewPayment.setVisibility(View.GONE);
        binding.layoutPaymentResult.setVisibility(View.VISIBLE);
        binding.tvPaymentResultIcon.setText(success ? "✅" : "❌");
        binding.tvPaymentResultMessage.setText(message);
        binding.btnPaymentDone.setOnClickListener(v -> {
            setResult(success ? RESULT_OK : RESULT_CANCELED);
            finish();
        });
    }

    private void showCancelConfirm() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hủy thanh toán?")
                .setMessage("Đơn đặt sân vẫn được giữ, bạn có thể thanh toán sau.")
                .setPositiveButton("Rời khỏi", (d, w) -> finish())
                .setNegativeButton("Tiếp tục thanh toán", null)
                .show();
    }

    // ============================================================
    // HMAC-SHA512 Signature
    // ============================================================

    /** Tính chữ ký HMAC-SHA512 theo chuẩn VNPay */
    private static String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            // Convert byte array → hex string
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onBackPressed() {
        if (binding.webViewPayment.canGoBack()) {
            binding.webViewPayment.goBack();
        } else {
            showCancelConfirm();
        }
    }
}
