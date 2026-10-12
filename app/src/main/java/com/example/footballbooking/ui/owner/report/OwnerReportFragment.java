package com.example.footballbooking.ui.owner.report;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.databinding.FragmentAdminReportBinding;
import com.example.footballbooking.ui.owner.OwnerViewModel;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * OwnerReportFragment — Báo cáo doanh thu sân của chủ sân.
 * Tái sử dụng layout fragment_admin_report.xml và logic chart tương tự.
 * Chỉ tính doanh thu từ sân của owner hiện tại.
 */
public class OwnerReportFragment extends Fragment {

    private FragmentAdminReportBinding binding;
    private OwnerViewModel ownerViewModel;

    private final Calendar reportCalendar = Calendar.getInstance();
    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private List<String> revenueBucketLabels = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // Tái sử dụng layout Admin Report
        binding = FragmentAdminReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownerViewModel = new ViewModelProvider(requireActivity()).get(OwnerViewModel.class);

        setupChartStyle();
        // Layout này được dùng chung với Admin; hàng KPI Users/Owners/Pitches
        // không thuộc phạm vi báo cáo của chủ sân.
        binding.layoutAdminKpis.setVisibility(View.GONE);
        binding.layoutOwnerKpis.setVisibility(View.VISIBLE);
        binding.tvRevenueChartTitle.setText("Doanh thu theo mốc 5 ngày (nghìn đồng)");
        binding.tvBookingChartTitle.setText("Số đơn đã thu tiền theo mốc 5 ngày");
        // Chủ sân không cần biểu đồ tỷ lệ trạng thái đơn.
        binding.cardStatusDistribution.setVisibility(View.GONE);
        binding.cardBookingsByDay.setVisibility(View.VISIBLE);
        setupMonthNav();
        observeViewModel();
        ownerViewModel.getOwnerReady().observe(getViewLifecycleOwner(), owner -> {
            if (owner != null) loadCurrentMonth();
        });
    }

    private void setupMonthNav() {
        binding.btnPrevMonth.setOnClickListener(v -> {
            reportCalendar.add(Calendar.MONTH, -1);
            loadMonth();
        });
        binding.btnNextMonth.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            if (reportCalendar.before(now)) {
                reportCalendar.add(Calendar.MONTH, 1);
                loadMonth();
            }
        });
    }

    private void loadCurrentMonth() { loadMonth(); }

    private void loadMonth() {
        String yearMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(reportCalendar.getTime());
        String display = new SimpleDateFormat("'Tháng' M/yyyy", Locale.getDefault())
                .format(reportCalendar.getTime());
        binding.tvReportMonth.setText(display);
        ownerViewModel.loadReportForMonth(yearMonth);
    }

    private void observeViewModel() {
        ownerViewModel.getReportBookings().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess() && resource.data != null) {
                bindData(resource.data);
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void bindData(List<Booking> bookings) {
        double total = ownerViewModel.calcTotalRevenue(bookings);
        binding.tvTotalRevenue.setText(currencyFmt.format((long) total) + "đ");
        binding.tvTotalBookings.setText(ownerViewModel.countRevenueBookings(bookings) + " đơn");
        binding.tvRevenueBookings.setText(ownerViewModel.countRevenueBookings(bookings) + " đơn");
        binding.tvUnpaidBookings.setText(ownerViewModel.countUnpaidApprovedBookings(bookings) + " đơn");

        revenueBucketLabels = ownerViewModel.buildFiveDayBucketLabels(bookings);
        updateBarChart(ownerViewModel.buildFiveDayRevenueBarEntries(bookings));
        updateLineChart(ownerViewModel.buildFiveDayBookingCountEntries(bookings));
        binding.cardStatusDistribution.setVisibility(View.GONE);
        binding.cardBookingsByDay.setVisibility(View.VISIBLE);
    }

    private void setupChartStyle() {
        binding.barChartRevenue.setBackgroundColor(Color.TRANSPARENT);
        binding.barChartRevenue.getDescription().setEnabled(false);
        binding.barChartRevenue.getLegend().setEnabled(false);
        binding.barChartRevenue.setDrawGridBackground(false);
        binding.barChartRevenue.getXAxis().setTextColor(Color.GRAY);
        binding.barChartRevenue.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        binding.barChartRevenue.getXAxis().setDrawGridLines(false);
        configureFiveDayAxis(binding.barChartRevenue.getXAxis());
        binding.barChartRevenue.getAxisLeft().setTextColor(Color.GRAY);
        binding.barChartRevenue.getAxisLeft().setGridColor(0x22FFFFFF);
        binding.barChartRevenue.getAxisRight().setEnabled(false);

        binding.lineChartBookings.setBackgroundColor(Color.TRANSPARENT);
        binding.lineChartBookings.getDescription().setEnabled(false);
        binding.lineChartBookings.getLegend().setEnabled(false);
        binding.lineChartBookings.setDrawGridBackground(false);
        binding.lineChartBookings.getXAxis().setTextColor(Color.GRAY);
        binding.lineChartBookings.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        binding.lineChartBookings.getXAxis().setDrawGridLines(false);
        configureFiveDayAxis(binding.lineChartBookings.getXAxis());
        binding.lineChartBookings.getAxisLeft().setTextColor(Color.GRAY);
        binding.lineChartBookings.getAxisLeft().setGridColor(0x22FFFFFF);
        binding.lineChartBookings.getAxisRight().setEnabled(false);
    }

    private void updateBarChart(List<BarEntry> entries) {
        if (entries.isEmpty()) {
            binding.barChartRevenue.clear();
            configureFiveDayAxis(binding.barChartRevenue.getXAxis());
            return;
        }
        BarDataSet dataSet = new BarDataSet(entries, "Doanh thu");
        dataSet.setColor(0xFFFF9800);  // Màu cam — phân biệt với Admin xanh lá
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(9f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);
        binding.barChartRevenue.setData(barData);
        configureFiveDayAxis(binding.barChartRevenue.getXAxis());
        binding.barChartRevenue.animateY(800, Easing.EaseInOutQuart);
        binding.barChartRevenue.invalidate();
    }

    /** Trục X hiển thị đầy đủ các mốc 5 ngày trong tháng. */
    private void configureFiveDayAxis(XAxis axis) {
        int visibleBucketCount = revenueBucketLabels.size();
        axis.setGranularity(1f);
        axis.setGranularityEnabled(true);
        axis.setAxisMinimum(visibleBucketCount > 0 ? 0.5f : 1f);
        axis.setAxisMaximum(visibleBucketCount > 0 ? visibleBucketCount + 0.5f : 1f);
        axis.setLabelCount(Math.max(1, visibleBucketCount), true);
        axis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = Math.round(value) - 1;
                return index >= 0 && index < revenueBucketLabels.size()
                        ? revenueBucketLabels.get(index) : "";
            }
        });
    }

    private void updateLineChart(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            binding.lineChartBookings.clear();
            configureFiveDayAxis(binding.lineChartBookings.getXAxis());
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Số đơn");
        dataSet.setColor(0xFF60A5FA);
        dataSet.setCircleColor(0xFF60A5FA);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3.5f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(8f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(0x3360A5FA);
        dataSet.setFillAlpha(80);

        binding.lineChartBookings.setData(new LineData(dataSet));
        configureFiveDayAxis(binding.lineChartBookings.getXAxis());
        binding.lineChartBookings.animateX(600);
        binding.lineChartBookings.invalidate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
