package com.example.footballbooking.ui.common.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.databinding.ItemAdminBookingCardBinding;
import com.example.footballbooking.utils.Constants;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * AdminBookingAdapter — Hiển thị danh sách đơn đặt sân cho Admin (Chỉ xem).
 */
public class AdminBookingAdapter
        extends ListAdapter<Booking, AdminBookingAdapter.AdminBookingVH> {

    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final DiffUtil.ItemCallback<Booking> DIFF =
            new DiffUtil.ItemCallback<Booking>() {
                @Override
                public boolean areItemsTheSame(@NonNull Booking o, @NonNull Booking n) {
                    return o.getBookingId() != null && o.getBookingId().equals(n.getBookingId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Booking o, @NonNull Booking n) {
                    return Objects.equals(o.getCustomerName(), n.getCustomerName())
                            && Objects.equals(o.getCustomerPhone(), n.getCustomerPhone())
                            && Objects.equals(o.getPitchName(), n.getPitchName())
                            && Objects.equals(o.getBookingDate(), n.getBookingDate())
                            && Objects.equals(o.getStartTime(), n.getStartTime())
                            && Objects.equals(o.getEndTime(), n.getEndTime())
                            && o.getTotalAmount() == n.getTotalAmount()
                            && Objects.equals(o.getNote(), n.getNote())
                            && Objects.equals(o.getBookingStatus(), n.getBookingStatus())
                            && Objects.equals(o.getMatchStatus(), n.getMatchStatus());
                }
            };

    public AdminBookingAdapter() {
        super(DIFF);
    }

    @NonNull @Override
    public AdminBookingVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AdminBookingVH(
                ItemAdminBookingCardBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AdminBookingVH holder, int position) {
        holder.bind(getItem(position), currencyFmt);
    }

    static class AdminBookingVH extends RecyclerView.ViewHolder {
        private final ItemAdminBookingCardBinding b;

        AdminBookingVH(ItemAdminBookingCardBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(Booking bk, NumberFormat fmt) {
            // Booking ID (hiển thị ngắn)
            String shortId = bk.getBookingId() != null && bk.getBookingId().length() > 8
                    ? "#" + bk.getBookingId().substring(0, 8).toUpperCase()
                    : "#" + bk.getBookingId();
            b.tvAdminBookingId.setText(shortId);

            // Customer info
            b.tvAdminCustomerName.setText(bk.getCustomerName());
            b.tvAdminCustomerPhone.setText("📞 " + bk.getCustomerPhone());

            // Total
            b.tvAdminTotal.setText(fmt.format((long) bk.getTotalAmount()) + "đ");

            // Pitch & time
            b.tvAdminPitchName.setText(bk.getPitchName()
                    + " (" + bk.getPitchType() + " người)");
            b.tvAdminBookingDate.setText(formatDate(bk.getBookingDate()));
            b.tvAdminBookingTime.setText(bk.getStartTime() + " - " + bk.getEndTime());

            // Note
            if (bk.getNote() != null && !bk.getNote().isEmpty()) {
                b.tvAdminNote.setVisibility(View.VISIBLE);
                b.tvAdminNote.setText("📝 " + bk.getNote());
            } else {
                b.tvAdminNote.setVisibility(View.GONE);
            }

            // Booking Status chip
            applyStatusStyle(bk.getBookingStatus());

            // Hide action buttons for Admin (View Only)
            b.layoutAdminActions.setVisibility(View.GONE);
            b.layoutMatchStatus.setVisibility(View.GONE);
        }

        private void applyStatusStyle(String status) {
            int colorRes;
            String label;
            switch (status != null ? status : "") {
                case Constants.STATUS_APPROVED:
                    colorRes = R.color.status_approved; label = "✓ Đã duyệt"; break;
                case Constants.STATUS_REJECTED:
                    colorRes = R.color.status_rejected; label = "✗ Từ chối"; break;
                case Constants.STATUS_CANCELLED:
                    colorRes = R.color.status_cancelled; label = "Đã hủy"; break;
                default:
                    colorRes = R.color.status_pending; label = "⏳ Chờ duyệt"; break;
            }
            b.chipAdminBookingStatus.setText(label);
            b.chipAdminBookingStatus.setChipBackgroundColorResource(colorRes);
        }

        private String formatDate(String dateStr) {
            if (dateStr == null) return "";
            try {
                SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat out = new SimpleDateFormat("EEE, dd/MM/yyyy", new Locale("vi", "VN"));
                Date d = in.parse(dateStr);
                return d != null ? out.format(d) : dateStr;
            } catch (Exception e) { return dateStr; }
        }
    }
}
