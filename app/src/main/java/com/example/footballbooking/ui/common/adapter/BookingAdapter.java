package com.example.footballbooking.ui.common.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.databinding.ItemBookingHistoryBinding;
import com.example.footballbooking.utils.Constants;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * BookingAdapter — Hiển thị lịch sử đặt sân với màu sắc theo trạng thái.
 * Nút Hủy chỉ hiện khi booking.isCancellable() == true && chưa qua giờ đá.
 */
public class BookingAdapter extends ListAdapter<Booking, BookingAdapter.BookingViewHolder> {

    public interface OnBookingActionListener {
        void onCancelClick(Booking booking);
        void onBookingClick(Booking booking);
    }

    private final OnBookingActionListener listener;
    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final DiffUtil.ItemCallback<Booking> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Booking>() {
                @Override
                public boolean areItemsTheSame(@NonNull Booking o, @NonNull Booking n) {
                    return o.getBookingId() != null && o.getBookingId().equals(n.getBookingId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Booking o, @NonNull Booking n) {
                    return o.getBookingStatus().equals(n.getBookingStatus())
                            && o.getMatchStatus().equals(n.getMatchStatus())
                            && o.getPaymentStatus().equals(n.getPaymentStatus());
                }
            };

    public BookingAdapter(OnBookingActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBookingHistoryBinding binding = ItemBookingHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new BookingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        holder.bind(getItem(position), listener, currencyFmt);
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {
        private final ItemBookingHistoryBinding b;

        BookingViewHolder(ItemBookingHistoryBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(Booking booking, OnBookingActionListener listener,
                  NumberFormat currencyFmt) {

            // --- Tên sân + Giờ ---
            b.tvHistoryPitchName.setText(booking.getPitchName());
            b.tvHistoryTime.setText("🕕 " + booking.getStartTime() + " - " + booking.getEndTime()
                    + "  (" + formatDate(booking.getBookingDate()) + ")");
            b.tvHistoryAddress.setText("📍 " + booking.getPitchAddress());
            b.tvHistoryTotal.setText(currencyFmt.format((long) booking.getTotalAmount()) + "đ");

            // --- Ngày tạo đơn ---
            if (booking.getCreatedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                b.tvBookingDate.setText(sdf.format(booking.getCreatedAt().toDate()));
            }

            // --- Booking Status chip ---
            applyBookingStatusStyle(booking.getBookingStatus());

            // --- Match Status chip ---
            applyMatchStatusStyle(booking.getMatchStatus(), booking.getBookingStatus());

            // --- Payment Status ---
            applyPaymentStatusStyle(booking.getPaymentStatus());

            // --- Nút Hủy ---
            boolean canCancel = booking.isCancellable() && !isPastMatchTime(booking);
            b.btnCancelBooking.setVisibility(canCancel
                    ? android.view.View.VISIBLE : android.view.View.GONE);

            b.btnCancelBooking.setOnClickListener(v -> {
                if (listener != null) listener.onCancelClick(booking);
            });

            b.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onBookingClick(booking);
            });
        }

        private void applyBookingStatusStyle(String status) {
            int bgColor;
            String text;
            switch (status) {
                case Constants.STATUS_APPROVED:
                    bgColor = R.color.status_approved; text = "✓ Đã duyệt"; break;
                case Constants.STATUS_REJECTED:
                    bgColor = R.color.status_rejected; text = "✗ Từ chối"; break;
                case Constants.STATUS_CANCELLED:
                    bgColor = R.color.status_cancelled; text = "Đã hủy"; break;
                default: // pending
                    bgColor = R.color.status_pending; text = "⏳ Chờ duyệt"; break;
            }
            b.chipBookingStatus.setText(text);
            b.chipBookingStatus.setChipBackgroundColorResource(bgColor);
        }

        private void applyMatchStatusStyle(String matchStatus, String bookingStatus) {
            if (!Constants.STATUS_APPROVED.equals(bookingStatus)) {
                b.chipMatchStatus.setVisibility(android.view.View.GONE);
                return;
            }
            b.chipMatchStatus.setVisibility(android.view.View.VISIBLE);
            switch (matchStatus) {
                case Constants.MATCH_PLAYING:
                    b.chipMatchStatus.setText("🔴 Đang đá");
                    b.chipMatchStatus.setChipBackgroundColorResource(R.color.match_playing);
                    break;
                case Constants.MATCH_HALF_TIME:
                    b.chipMatchStatus.setText("⏸ Giữa hiệp");
                    b.chipMatchStatus.setChipBackgroundColorResource(R.color.status_pending);
                    break;
                case Constants.MATCH_FINISHED:
                    b.chipMatchStatus.setText("✓ Đã kết thúc");
                    b.chipMatchStatus.setChipBackgroundColorResource(R.color.status_cancelled);
                    break;
                default: // upcoming
                    b.chipMatchStatus.setText("🔵 Sắp đá");
                    b.chipMatchStatus.setChipBackgroundColorResource(R.color.match_upcoming);
                    break;
            }
        }

        private void applyPaymentStatusStyle(String paymentStatus) {
            switch (paymentStatus) {
                case Constants.PAYMENT_PAID:
                    b.chipPaymentStatus.setText("💳 Đã thanh toán");
                    b.chipPaymentStatus.setChipBackgroundColor(
                            android.content.res.ColorStateList.valueOf(0xCC10B981));
                    break;
                case Constants.PAYMENT_REFUNDED:
                    b.chipPaymentStatus.setText("↩ Đã hoàn tiền");
                    b.chipPaymentStatus.setChipBackgroundColor(
                            android.content.res.ColorStateList.valueOf(0xCC3B82F6));
                    break;
                default: // unpaid
                    b.chipPaymentStatus.setText("⏳ Chưa thanh toán");
                    b.chipPaymentStatus.setChipBackgroundColor(
                            android.content.res.ColorStateList.valueOf(0xCCF59E0B));
                    break;
            }
        }

        /** Format "2025-06-15" → "15/06/2025" */
        private String formatDate(String dateStr) {
            if (dateStr == null) return "";
            try {
                SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date d = in.parse(dateStr);
                return d != null ? out.format(d) : dateStr;
            } catch (Exception e) { return dateStr; }
        }

        private boolean isPastMatchTime(Booking booking) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Date match = sdf.parse(booking.getBookingDate() + " " + booking.getStartTime());
                return match != null && new Date().after(match);
            } catch (Exception e) { return false; }
        }
    }
}
