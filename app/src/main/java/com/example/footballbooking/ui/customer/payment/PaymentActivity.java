package com.example.footballbooking.ui.customer.payment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.footballbooking.BuildConfig;
import com.example.footballbooking.data.remote.RetrofitClient;
import com.example.footballbooking.data.remote.api.PaymentApi;
import com.example.footballbooking.databinding.ActivityPaymentBinding;
import com.example.footballbooking.ui.customer.CustomerMainActivity;
import com.example.footballbooking.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình thanh toán PayOS VietQR.
 * Return/cancel chỉ là tín hiệu điều hướng; webhook mới xác nhận thanh toán.
 */
public class PaymentActivity extends AppCompatActivity {
    private static final String PAYOS_CHECKOUT_HOST = "pay.payos.vn";

    private ActivityPaymentBinding binding;
    private String bookingId;
    private String paymentRef;
    private boolean checkingResult;
    private boolean busy;
    private boolean checkAfterCurrentRequest;
    private boolean reconcileNext;
    private boolean reopenCheckout;
    private int pollCount;
    private Call<PaymentApi.Session> activeCall;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = () -> {
        reconcileNext = false;
        requestSession(false);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupBackNavigation();
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> showExitConfirm());
        setupWebView();

        bookingId = state == null
                ? getIntent().getStringExtra(Constants.EXTRA_BOOKING_ID)
                : state.getString("bookingId");
        paymentRef = state == null ? null : state.getString("paymentRef");
        checkingResult = state != null && state.getBoolean("checkingResult");

        if (getIntent().getData() != null) {
            acceptCallback(getIntent().getData());
        } else if (bookingId == null || !bookingId.matches("[A-Za-z0-9_-]{1,128}")) {
            showResult(false, "Không tìm thấy đơn cần thanh toán.", false);
        } else {
            requestSession(!checkingResult);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptCallback(intent.getData());
    }

    private void setupWebView() {
        WebSettings settings = binding.webViewPayment.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        binding.webViewPayment.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (binding == null || checkingResult) return;
                binding.progressPayment.setProgress(progress);
                binding.progressPayment.setVisibility(progress == 100 ? View.GONE : View.VISIBLE);
            }
        });
        binding.webViewPayment.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) showGatewayError();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) showGatewayError();
            }

            @Override
            public void onReceivedSslError(WebView view,
                                           android.webkit.SslErrorHandler sslHandler,
                                           android.net.http.SslError error) {
                sslHandler.cancel();
                showGatewayError();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("footballbooking".equals(uri.getScheme())) {
                    if (request.isForMainFrame()) acceptCallback(uri);
                    return true;
                }
                if (!"https".equals(uri.getScheme())) {
                    if (request.isForMainFrame()) {
                        showResult(false, "Liên kết thanh toán không hợp lệ. Vui lòng thử lại.",
                                paymentRef != null);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void showGatewayError() {
        checkingResult = true;
        handler.removeCallbacks(poll);
        showResult(false,
                "Không tải được trang PayOS. Nếu đã trừ tiền, hãy kiểm tra trạng thái trước khi thanh toán lại.",
                paymentRef != null);
    }

    private void acceptCallback(Uri uri) {
        if (uri == null || !"footballbooking".equals(uri.getScheme())
                || !"payment_result".equals(uri.getHost())) {
            showResult(false, "Đường dẫn thanh toán không hợp lệ.", false);
            return;
        }

        String ref = uri.getQueryParameter("payment_ref");
        String result = uri.getQueryParameter("result");
        if (ref == null || !ref.matches("[a-f0-9]{32}")
                || (paymentRef != null && !paymentRef.equals(ref))) {
            showResult(false, "Đường dẫn không khớp giao dịch đang mở.", false);
            return;
        }
        if (!"success".equals(result) && !"cancel".equals(result)) {
            showResult(false, "Không xác định được kết quả thanh toán.", false);
            return;
        }

        paymentRef = ref;
        reopenCheckout = "cancel".equals(result);
        checkingResult = !reopenCheckout;
        pollCount = 0;
        reconcileNext = false;
        handler.removeCallbacks(poll);

        if (busy) {
            checkAfterCurrentRequest = true;
            return;
        }
        requestSession(false);
    }

    private void requestSession(boolean create) {
        requestSession(create, false);
    }

    private void requestSession(boolean create, boolean forceRefreshToken) {
        if (binding == null || isFinishing() || busy) return;
        if (!BuildConfig.DEBUG && !"https".equals(Uri.parse(BuildConfig.PAYMENT_BASE_URL).getScheme())) {
            showResult(false, "Kết nối thanh toán chưa được cấu hình an toàn.", false);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showResult(false, "Vui lòng đăng nhập để kiểm tra thanh toán.", false);
            return;
        }
        if (!create && paymentRef == null) {
            showResult(false, "Không tìm thấy giao dịch cần kiểm tra.", false);
            return;
        }

        busy = true;
        final boolean reconcile = !create && reconcileNext;
        reconcileNext = false;
        binding.progressPayment.setVisibility(View.VISIBLE);
        if (!create && !reopenCheckout) {
            showResult(false, "Đang chờ PayOS xác nhận thanh toán…", false);
        }

        user.getIdToken(forceRefreshToken).addOnCompleteListener(task -> {
            if (binding == null || isFinishing()) return;
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (!task.isSuccessful() || current == null || !user.getUid().equals(current.getUid())
                    || task.getResult() == null || task.getResult().getToken() == null) {
                busy = false;
                showResult(false, "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.", false);
                return;
            }

            PaymentApi api = RetrofitClient.getInstance().getPaymentApi();
            String bearer = "Bearer " + task.getResult().getToken();
            activeCall = create ? api.create(bearer, new PaymentApi.CreateRequest(bookingId))
                    : reconcile ? api.reconcile(bearer, paymentRef)
                    : api.status(bearer, paymentRef);
            activeCall.enqueue(new Callback<PaymentApi.Session>() {
                @Override
                public void onResponse(@NonNull Call<PaymentApi.Session> call,
                                       @NonNull Response<PaymentApi.Session> response) {
                    if (binding == null || isFinishing()) return;
                    busy = false;
                    if (response.code() == 401 && !forceRefreshToken) {
                        requestSession(create, true);
                        return;
                    }
                    if (!response.isSuccessful() || response.body() == null) {
                        String message = "Chưa xác minh được thanh toán. Nếu đã trừ tiền, không thanh toán lại ngay.";
                        if (response.errorBody() != null) {
                            try {
                                com.google.gson.JsonObject error =
                                        com.google.gson.JsonParser.parseString(response.errorBody().string())
                                                .getAsJsonObject();
                                if (error.has("detail") && error.get("detail").isJsonPrimitive()) {
                                    message = error.get("detail").getAsString();
                                }
                            } catch (Exception ignored) {
                                // Keep the safe generic message.
                            }
                        }
                        showResult(false, message, paymentRef != null);
                        if (response.code() >= 500) scheduleStatusCheck();
                        return;
                    }
                    if (checkAfterCurrentRequest) {
                        checkAfterCurrentRequest = false;
                        requestSession(false);
                        return;
                    }
                    renderSession(response.body(), create);
                }

                @Override
                public void onFailure(@NonNull Call<PaymentApi.Session> call,
                                      @NonNull Throwable error) {
                    if (binding == null || isFinishing() || call.isCanceled()) return;
                    busy = false;
                    if (checkAfterCurrentRequest) {
                        checkAfterCurrentRequest = false;
                        requestSession(false);
                        return;
                    }
                    showResult(false,
                            "Chưa kết nối được máy chủ. Nếu đã trừ tiền, hãy kiểm tra lại trạng thái trước khi thanh toán tiếp.",
                            paymentRef != null);
                    scheduleStatusCheck();
                }
            });
        });
    }

    private void renderSession(PaymentApi.Session session, boolean create) {
        if (session.bookingId == null || session.paymentRef == null
                || (bookingId != null && !bookingId.equals(session.bookingId))
                || (paymentRef != null && !paymentRef.equals(session.paymentRef))) {
            showResult(false, "Giao dịch không khớp đơn đang kiểm tra.", false);
            return;
        }
        bookingId = session.bookingId;
        paymentRef = session.paymentRef;

        if ("paid".equals(session.status)) {
            goHome();
        } else if ("pending".equals(session.status)
                && (create || reopenCheckout) && session.paymentUrl != null) {
            reopenCheckout = false;
            openPayOsCheckout(session.paymentUrl);
        } else if ("pending".equals(session.status)) {
            checkingResult = true;
            showResult(false,
                    "Đã quay lại từ PayOS. Bạn có thể tiếp tục thanh toán hoặc kiểm tra lại trạng thái.",
                    true);
            scheduleStatusCheck();
        } else if ("review".equals(session.status)) {
            showResult(false,
                    "Giao dịch cần đối soát. Vui lòng liên hệ chủ sân trước khi thanh toán lại.",
                    true);
        } else if ("expired".equals(session.status) || "failed".equals(session.status)
                || "cancelled".equals(session.status)) {
            showResult(false, "Giao dịch đã hết hạn hoặc chưa thành công. Vui lòng thử lại.", true);
        } else {
            showResult(false, "Chưa xác định được trạng thái thanh toán.", true);
        }
    }

    private void openPayOsCheckout(String paymentUrl) {
        Uri url = Uri.parse(paymentUrl);
        if (!"https".equals(url.getScheme()) || url.getUserInfo() != null
                || !PAYOS_CHECKOUT_HOST.equals(url.getHost())) {
            showResult(false, "Đường dẫn PayOS không hợp lệ.", false);
            return;
        }
        checkingResult = false;
        binding.layoutPaymentResult.setVisibility(View.GONE);
        binding.progressPayment.setVisibility(View.VISIBLE);
        binding.webViewPayment.setVisibility(View.VISIBLE);
        binding.webViewPayment.loadUrl(paymentUrl);
    }

    private void scheduleStatusCheck() {
        handler.removeCallbacks(poll);
        if (checkingResult && paymentRef != null && pollCount++ < 12) {
            handler.postDelayed(poll, 10000);
        }
    }

    private void showResult(boolean success, String message, boolean retry) {
        if (binding == null) return;
        binding.btnPaymentResume.setVisibility(View.GONE);
        binding.progressPayment.setVisibility(View.GONE);
        binding.webViewPayment.setVisibility(View.GONE);
        binding.layoutPaymentResult.setVisibility(View.VISIBLE);
        binding.tvPaymentResultIcon.setText(success ? "✅" : "ℹ️");
        binding.tvPaymentResultMessage.setText(message);
        binding.btnPaymentDone.setText(retry ? "Kiểm tra lại" : "Đóng");
        binding.btnPaymentDone.setOnClickListener(v -> {
            if (retry) {
                if (busy) return;
                handler.removeCallbacks(poll);
                pollCount = 0;
                reconcileNext = true;
                requestSession(false);
            } else {
                setResult(success ? RESULT_OK : RESULT_CANCELED);
                finish();
            }
        });
    }

    private void goHome() {
        handler.removeCallbacks(poll);
        Intent intent = new Intent(this, CustomerMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showExitConfirm() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Rời màn hình thanh toán?")
                .setMessage("Bạn có thể kiểm tra thanh toán trong lịch sử đặt sân. Rời màn hình không hủy giao dịch PayOS.")
                .setPositiveButton("Rời khỏi", (d, w) -> finish())
                .setNegativeButton("Tiếp tục", null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("bookingId", bookingId);
        outState.putString("paymentRef", paymentRef);
        outState.putBoolean("checkingResult", checkingResult);
        super.onSaveInstanceState(outState);
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirm();
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
        if (activeCall != null) activeCall.cancel();
        if (binding != null) {
            binding.webViewPayment.stopLoading();
            binding.webViewPayment.setWebChromeClient(null);
            binding.webViewPayment.setWebViewClient(new WebViewClient());
            binding.webViewPayment.destroy();
        }
        binding = null;
        super.onDestroy();
    }
}
