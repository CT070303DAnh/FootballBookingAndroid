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

public class OwnerBookingAdapter extends ListAdapter<Booking, OwnerBookingAdapter.OwnerBookingVH> {

    public interface OnOwnerBookingActionListener {
        void onApprove(Booking booking);
        void onReject(Booking booking);
        void onMatchStatusChange(String bookingId, String newStatus);
    }

    private final OnOwnerBookingActionListener listener;
    private final NumberFormat currencyFmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final DiffUtil.ItemCallback<Booking> DIFF = new DiffUtil.ItemCallback<Booking>() {
        @Override
        public boolean areItemsTheSame(@NonNull Booking o, @NonNull Booking n) {
            return o.getBookingId() != null && o.getBookingId().equals(n.getBookingId());
        }
        @Override
        public boolean areContentsTheSame(@NonNull Booking o, @NonNull Booking n) {
            return o.getBookingStatus().equals(n.getBookingStatus())
                    && o.getMatchStatus().equals(n.getMatchStatus());
        }
    };

    public OwnerBookingAdapter(OnOwnerBookingActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public OwnerBookingVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new OwnerBookingVH(ItemAdminBookingCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull OwnerBookingVH holder, int position) {
        holder.bind(getItem(position), listener, currencyFmt);
    }

    static class OwnerBookingVH extends RecyclerView.ViewHolder {
        private final ItemAdminBookingCardBinding b;

        OwnerBookingVH(ItemAdminBookingCardBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(Booking bk, OnOwnerBookingActionListener listener, NumberFormat fmt) {
            String shortId = bk.getBookingId() != null && bk.getBookingId().length() > 8
                    ? "#" + bk.getBookingId().substring(0, 8).toUpperCase()
                    : "#" + bk.getBookingId();
            b.tvAdminBookingId.setText(shortId);

            b.tvAdminCustomerName.setText(bk.getCustomerName());
            b.tvAdminCustomerPhone.setText("📞 " + bk.getCustomerPhone());
            b.tvAdminTotal.setText(fmt.format((long) bk.getTotalAmount()) + "đ");
            b.tvAdminPitchName.setText(bk.getPitchName() + " (" + bk.getPitchType() + " người)");
            b.tvAdminBookingDate.setText(formatDate(bk.getBookingDate()));
            b.tvAdminBookingTime.setText(bk.getStartTime() + " - " + bk.getEndTime());

            if (bk.getNote() != null && !bk.getNote().isEmpty()) {
                b.tvAdminNote.setVisibility(View.VISIBLE);
                b.tvAdminNote.setText("📝 " + bk.getNote());
            } else {
                b.tvAdminNote.setVisibility(View.GONE);
            }

            applyStatusStyle(bk.getBookingStatus());

            boolean isPending = Constants.STATUS_PENDING.equals(bk.getBookingStatus());
            b.layoutAdminActions.setVisibility(isPending ? View.VISIBLE : View.GONE);
            if (isPending) {
                b.btnApprove.setOnClickListener(v -> listener.onApprove(bk));
                b.btnReject.setOnClickListener(v -> listener.onReject(bk));
            }

            boolean isApproved = Constants.STATUS_APPROVED.equals(bk.getBookingStatus());
            b.layoutMatchStatus.setVisibility(isApproved ? View.VISIBLE : View.GONE);
            if (isApproved) {
                b.btnMatchUpcoming.setOnClickListener(v -> listener.onMatchStatusChange(bk.getBookingId(), Constants.MATCH_UPCOMING));
                b.btnMatchPlaying.setOnClickListener(v -> listener.onMatchStatusChange(bk.getBookingId(), Constants.MATCH_PLAYING));
                b.btnMatchFinished.setOnClickListener(v -> listener.onMatchStatusChange(bk.getBookingId(), Constants.MATCH_FINISHED));
            }
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
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat out = new SimpleDateFormat("EEE, dd/MM/yyyy", new Locale("vi", "VN"));
                Date d = in.parse(dateStr);
                return d != null ? out.format(d) : dateStr;
            } catch (Exception e) {
                return dateStr;
            }
        }
    }
}
