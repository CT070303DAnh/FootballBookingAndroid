package com.example.footballbooking.ui.customer.card;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.CardTransaction;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.databinding.ActivityMemberCardBinding;
import com.example.footballbooking.ui.common.adapter.CardTransactionAdapter;
import com.example.footballbooking.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * MemberCardActivity — Màn hình Thẻ Thành Viên Thông Minh (Realtime Firestore).
 *
 * Kết nối 2 chiều với phần mềm POS trên NetBeans (jCardSim):
 * - Lắng nghe realtime số dư, điểm tích lũy, hạng thành viên, trạng thái thẻ.
 * - Lắng nghe realtime lịch sử giao dịch thẻ (có huy hiệu xác thực chữ ký RSA).
 * - Cho phép khách hàng bấm "Báo mất / Khóa thẻ khẩn cấp" ngay trên điện thoại.
 */
public class MemberCardActivity extends AppCompatActivity {

    private ActivityMemberCardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CardTransactionAdapter adapter = new CardTransactionAdapter();
    private final NumberFormat vndFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private ListenerRegistration userListener;
    private ListenerRegistration txListener;
    private String currentUid;
    private String currentCardStatus = Constants.CARD_STATUS_ACTIVE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMemberCardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.rvCardTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCardTransactions.setAdapter(adapter);

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) {
            Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUid = fbUser.getUid();

        binding.btnReportLost.setOnClickListener(v -> showReportLostDialog());

        listenToMemberCardRealtime();
        listenToCardTransactionsRealtime();
    }

    private void listenToMemberCardRealtime() {
        userListener = db.collection(Constants.COL_USERS)
                .document(currentUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;
                    User user = snapshot.toObject(User.class);
                    if (user == null) return;
                    bindCardUi(user);
                });
    }

    private void bindCardUi(User user) {
        String cardId = user.getCardId();
        boolean hasCard = cardId != null && !cardId.trim().isEmpty();

        binding.tvCardId.setText(hasCard ? cardId : "Chưa liên kết thẻ (Cấp thẻ tại quầy POS)");
        binding.tvCardHolder.setText(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
        binding.tvCardBalance.setText(vndFormat.format(user.getCardBalance()) + " đ");
        binding.tvCardPoints.setText(user.getLoyaltyPoints() + " điểm");
        binding.tvCardTier.setText("Hạng: " + formatTier(user.getMemberTier()));

        boolean hasRsa = user.getCardPublicKey() != null && !user.getCardPublicKey().isEmpty();
        binding.tvCardRsaInfo.setText(hasRsa ? "🔐 Bảo vệ RSA-1024" : "🔓 Chưa gắn khóa RSA");

        currentCardStatus = user.getCardStatus() != null ? user.getCardStatus() : Constants.CARD_STATUS_ACTIVE;
        if (!hasCard) {
            binding.tvCardStatusBadge.setText("CHƯA CẤP");
            binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_pending));
            binding.btnReportLost.setEnabled(false);
            binding.btnReportLost.setAlpha(0.5f);
        } else if (Constants.CARD_STATUS_LOST.equalsIgnoreCase(currentCardStatus)) {
            binding.tvCardStatusBadge.setText("ĐÃ BÁO MẤT");
            binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_rejected));
            binding.btnReportLost.setEnabled(false);
            binding.btnReportLost.setText("🔒 Thẻ đã được báo mất — Liên hệ quầy để mở khóa PUK");
        } else if (Constants.CARD_STATUS_LOCKED.equalsIgnoreCase(currentCardStatus)) {
            binding.tvCardStatusBadge.setText("ĐÃ KHÓA");
            binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_rejected));
            binding.btnReportLost.setEnabled(false);
            binding.btnReportLost.setText("🔒 Thẻ đang bị khóa — Liên hệ quầy để mở khóa PUK");
        } else {
            binding.tvCardStatusBadge.setText("HOẠT ĐỘNG");
            binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_approved));
            binding.btnReportLost.setEnabled(true);
            binding.btnReportLost.setAlpha(1f);
            binding.btnReportLost.setText("🔒 Báo mất / Khóa thẻ khẩn cấp");
        }
    }

    private void listenToCardTransactionsRealtime() {
        txListener = db.collection(Constants.COL_CARD_TRANSACTIONS)
                .whereEqualTo("userId", currentUid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    List<CardTransaction> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        CardTransaction tx = doc.toObject(CardTransaction.class);
                        list.add(tx);
                    }
                    Collections.sort(list, (a, b) -> {
                        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                        if (a.getCreatedAt() == null) return -1;
                        if (b.getCreatedAt() == null) return 1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });

                    adapter.setItems(list);
                    binding.tvEmptyTransactions.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void showReportLostDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Khóa thẻ khẩn cấp?")
                .setMessage("Thẻ thành viên của bạn sẽ bị đánh dấu BÁO MẤT ngay lập tức.\n\n"
                        + "Khi kẻ gian mang thẻ đi quẹt tại quầy POS (NetBeans), hệ thống sẽ tự động từ chối và khóa cứng thẻ.")
                .setPositiveButton("Khóa thẻ ngay", (dialog, which) -> reportCardLost())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void reportCardLost() {
        db.collection(Constants.COL_USERS)
                .document(currentUid)
                .update("cardStatus", Constants.CARD_STATUS_LOST)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Đã báo mất và khóa thẻ thành công!", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi khóa thẻ: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private String formatTier(String tier) {
        if ("vip".equalsIgnoreCase(tier)) return "VIP (x2.0 điểm)";
        if ("gold".equalsIgnoreCase(tier)) return "Vàng (x1.5 điểm)";
        if ("silver".equalsIgnoreCase(tier)) return "Bạc (x1.2 điểm)";
        return "Đồng (x1.0 điểm)";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
        if (txListener != null) txListener.remove();
    }
}
