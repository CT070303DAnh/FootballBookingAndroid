package com.example.footballbooking.ui.common.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.CardTransaction;
import com.example.footballbooking.databinding.ItemCardTransactionBinding;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị lịch sử giao dịch thẻ thông minh (đồng bộ từ NetBeans + jCardSim).
 */
public class CardTransactionAdapter extends RecyclerView.Adapter<CardTransactionAdapter.TxViewHolder> {

    private final List<CardTransaction> items = new ArrayList<>();
    private final NumberFormat vndFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public void setItems(List<CardTransaction> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCardTransactionBinding binding = ItemCardTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TxViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TxViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class TxViewHolder extends RecyclerView.ViewHolder {
        private final ItemCardTransactionBinding binding;

        TxViewHolder(ItemCardTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CardTransaction tx) {
            String type = tx.getType() != null ? tx.getType() : "";
            switch (type) {
                case "topup":
                    binding.tvTxTitle.setText("💰 Nạp tiền vào thẻ");
                    binding.tvTxAmount.setText("+" + vndFormat.format(tx.getAmount()) + " đ");
                    binding.tvTxAmount.setTextColor(
                            ContextCompat.getColor(itemView.getContext(), R.color.status_approved));
                    break;
                case "pay_booking":
                    binding.tvTxTitle.setText("⚽ Check-in & Thanh toán sân");
                    binding.tvTxAmount.setText("-" + vndFormat.format(tx.getAmount()) + " đ");
                    binding.tvTxAmount.setTextColor(
                            ContextCompat.getColor(itemView.getContext(), R.color.accent));
                    break;
                case "pay_service":
                    binding.tvTxTitle.setText("🥤 Mua dịch vụ sân bóng");
                    binding.tvTxAmount.setText("-" + vndFormat.format(tx.getAmount()) + " đ");
                    binding.tvTxAmount.setTextColor(
                            ContextCompat.getColor(itemView.getContext(), R.color.accent));
                    break;
                case "redeem_reward":
                    binding.tvTxTitle.setText("🎁 Đổi quà tích điểm");
                    binding.tvTxAmount.setText(tx.getPointsDelta() + " điểm");
                    binding.tvTxAmount.setTextColor(
                            ContextCompat.getColor(itemView.getContext(), R.color.status_pending));
                    break;
                default:
                    binding.tvTxTitle.setText("💳 Giao dịch thẻ");
                    binding.tvTxAmount.setText(vndFormat.format(tx.getAmount()) + " đ");
                    break;
            }

            binding.tvTxDesc.setText(tx.getDescription() != null ? tx.getDescription() : "");

            if (tx.getCreatedAt() != null) {
                binding.tvTxTime.setText(dateFormat.format(tx.getCreatedAt().toDate()));
            } else {
                binding.tvTxTime.setText("Vừa xong");
            }

            int pts = tx.getPointsDelta();
            if (pts > 0) {
                binding.tvTxPoints.setVisibility(View.VISIBLE);
                binding.tvTxPoints.setText("+" + pts + " điểm");
                binding.tvTxPoints.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.primary_light));
            } else if (pts < 0 && !"redeem_reward".equals(type)) {
                binding.tvTxPoints.setVisibility(View.VISIBLE);
                binding.tvTxPoints.setText(pts + " điểm");
                binding.tvTxPoints.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.status_pending));
            } else {
                binding.tvTxPoints.setVisibility(View.GONE);
            }

            binding.tvTxRsaBadge.setVisibility(tx.hasRsaSignature() ? View.VISIBLE : View.GONE);
        }
    }
}
