package com.example.footballbooking.ui.admin.report;

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
import com.example.footballbooking.ui.admin.AdminViewModel;
import com.example.footballbooking.utils.Constants;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AdminReportFragment — Báo cáo doanh thu với MPAndroidChart.
 *
 * CHARTS:
 * 1. BarChart: Doanh thu theo ngày trong tháng
 * 2. PieChart: Tỷ lệ trạng thái đơn (approved/pending/rejected/cancelled)
 * 3. LineChart: Số đơn theo ngày
 *
 * THIẾT KẾ:
 * - Dark theme charts với màu từ colors.xml
 * - Animate khi load lần đầu
 * - Scroll tháng bằng nút < >
 */
public class AdminReportFragment extends Fragment {

    private FragmentAdminReportBinding binding;
    private AdminViewModel adminViewModel;

    private final Calendar reportCalendar = Calendar.getInstance();
    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adminViewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        setupCharts();
        setupMonthNavigation();
        observeViewModel();
        loadCurrentMonth();
        adminViewModel.loadAllUsers();
        adminViewModel.loadAllPitchesForModeration();
    }

    private void setupMonthNavigation() {
        binding.btnPrevMonth.setOnClickListener(v -> {
            reportCalendar.add(Calendar.MONTH, -1);
            loadMonth();
        });
        binding.btnNextMonth.setOnClickListener(v -> {
            // Không cho xem tháng tương lai
            Calendar now = Calendar.getInstance();
            if (reportCalendar.get(Calendar.YEAR) < now.get(Calendar.YEAR)
                    || reportCalendar.get(Calendar.MONTH) < now.get(Calendar.MONTH)) {
                reportCalendar.add(Calendar.MONTH, 1);
                loadMonth();
            }
        });
    }

    private void loadCurrentMonth() {
        loadMonth();
    }

    private void loadMonth() {
        String yearMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(reportCalendar.getTime());
        String displayMonth = new SimpleDateFormat("'Tháng' M/yyyy", Locale.getDefault())
                .format(reportCalendar.getTime());
        binding.tvReportMonth.setText(displayMonth);
        adminViewModel.loadReportForMonth(yearMonth);
    }

    private void observeViewModel() {
        adminViewModel.getReportBookings().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess() && resource.data != null) {
                bindReportData(resource.data);
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_SHORT).show();
            }
        });

        adminViewModel.getAllUsers().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                int userCount = 0;
                int ownerCount = 0;
                for (com.example.footballbooking.data.model.User user : resource.data) {
                    if (Constants.ROLE_CUSTOMER.equals(user.getRole())) userCount++;
                    else if (Constants.ROLE_OWNER.equals(user.getRole()) || Constants.ROLE_OWNER_PENDING.equals(user.getRole())) ownerCount++;
                }
                binding.tvTotalUsers.setText(String.valueOf(userCount));
                binding.tvTotalOwners.setText(String.valueOf(ownerCount));
            }
        });

        adminViewModel.getAllPitches().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                binding.tvTotalPitches.setText(String.valueOf(resource.data.size()));
            }
        });
    }

    private void bindReportData(List<Booking> bookings) {
        // KPIs
        double totalRevenue = adminViewModel.calcTotalRevenue(bookings);
        binding.tvTotalRevenue.setText(currencyFmt.format((long) totalRevenue) + "đ");
        binding.tvTotalBookings.setText(bookings.size() + " đơn");

        // Charts
        updateBarChart(adminViewModel.buildRevenueBarEntries(bookings));
        updatePieChart(adminViewModel.buildStatusDistribution(bookings));
        updateLineChart(bookings);
    }

    // ============================================================
    // CHART SETUP (Dark theme)
    // ============================================================

    private void setupCharts() {
        // BarChart
        binding.barChartRevenue.setBackgroundColor(Color.TRANSPARENT);
        binding.barChartRevenue.getDescription().setEnabled(false);
        binding.barChartRevenue.getLegend().setEnabled(false);
        binding.barChartRevenue.setDrawGridBackground(false);
        binding.barChartRevenue.getXAxis().setTextColor(Color.GRAY);
        binding.barChartRevenue.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        binding.barChartRevenue.getXAxis().setDrawGridLines(false);
        binding.barChartRevenue.getAxisLeft().setTextColor(Color.GRAY);
        binding.barChartRevenue.getAxisLeft().setGridColor(0x22FFFFFF);
        binding.barChartRevenue.getAxisRight().setEnabled(false);
        binding.barChartRevenue.setTouchEnabled(true);
        binding.barChartRevenue.setScaleEnabled(true);
        binding.barChartRevenue.setPinchZoom(false);

        // PieChart
        binding.pieChartStatus.setBackgroundColor(Color.TRANSPARENT);
        binding.pieChartStatus.getDescription().setEnabled(false);
        binding.pieChartStatus.setUsePercentValues(true);
        binding.pieChartStatus.setHoleColor(Color.TRANSPARENT);
        binding.pieChartStatus.setHoleRadius(45f);
        binding.pieChartStatus.setTransparentCircleAlpha(0);
        binding.pieChartStatus.setCenterText("Đơn đặt");
        binding.pieChartStatus.setCenterTextColor(Color.WHITE);
        binding.pieChartStatus.setCenterTextSize(14f);
        binding.pieChartStatus.getLegend().setEnabled(false);

        // LineChart
        binding.lineChartBookings.setBackgroundColor(Color.TRANSPARENT);
        binding.lineChartBookings.getDescription().setEnabled(false);
        binding.lineChartBookings.getLegend().setEnabled(false);
        binding.lineChartBookings.setDrawGridBackground(false);
        binding.lineChartBookings.getXAxis().setTextColor(Color.GRAY);
        binding.lineChartBookings.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        binding.lineChartBookings.getXAxis().setDrawGridLines(false);
        binding.lineChartBookings.getAxisLeft().setTextColor(Color.GRAY);
        binding.lineChartBookings.getAxisLeft().setGridColor(0x22FFFFFF);
        binding.lineChartBookings.getAxisRight().setEnabled(false);
    }

    private void updateBarChart(List<BarEntry> entries) {
        if (entries.isEmpty()) {
            binding.barChartRevenue.clear();
            binding.barChartRevenue.invalidate();
            return;
        }
        BarDataSet dataSet = new BarDataSet(entries, "Doanh thu");
        dataSet.setColor(0xFF4CAF50);          // Xanh lá
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(true);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.8f);

        binding.barChartRevenue.setData(barData);
        binding.barChartRevenue.animateY(800, Easing.EaseInOutQuart);
        binding.barChartRevenue.invalidate();
    }

    private void updatePieChart(Map<String, Integer> statusDist) {
        List<PieEntry> entries = new ArrayList<>();
        int approved  = statusDist.getOrDefault(Constants.STATUS_APPROVED, 0);
        int pending   = statusDist.getOrDefault(Constants.STATUS_PENDING, 0);
        int rejected  = statusDist.getOrDefault(Constants.STATUS_REJECTED, 0);
        int cancelled = statusDist.getOrDefault(Constants.STATUS_CANCELLED, 0);

        if (approved  > 0) entries.add(new PieEntry(approved,  "Duyệt"));
        if (pending   > 0) entries.add(new PieEntry(pending,   "Chờ"));
        if (rejected  > 0) entries.add(new PieEntry(rejected,  "Từ chối"));
        if (cancelled > 0) entries.add(new PieEntry(cancelled, "Hủy"));

        if (entries.isEmpty()) { binding.pieChartStatus.clear(); return; }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(
                0xFF10B981, // xanh lá — approved
                0xFFF59E0B, // vàng — pending
                0xFFEF4444, // đỏ — rejected
                0xFF6B7280  // xám — cancelled
        );
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(11f);
        dataSet.setSliceSpace(3f);

        binding.pieChartStatus.setData(new PieData(dataSet));
        binding.pieChartStatus.animateY(1000, Easing.EaseInOutQuart);
        binding.pieChartStatus.invalidate();

        // Update legend text
        binding.tvLegendApproved.setText("Duyệt: " + approved);
        binding.tvLegendPending.setText("Chờ: " + pending);
        binding.tvLegendRejected.setText("Từ chối: " + (rejected + cancelled));
    }

    private void updateLineChart(List<Booking> bookings) {
        // Đếm số đơn theo ngày
        java.util.Map<Integer, Integer> dailyCount = new java.util.TreeMap<>();
        for (Booking b : bookings) {
            if (b.getBookingDate() == null) continue;
            try {
                int day = Integer.parseInt(b.getBookingDate().split("-")[2]);
                dailyCount.put(day, dailyCount.getOrDefault(day, 0) + 1);
            } catch (Exception ignored) {}
        }

        List<com.github.mikephil.charting.data.Entry> entries = new ArrayList<>();
        for (java.util.Map.Entry<Integer, Integer> e : dailyCount.entrySet()) {
            entries.add(new com.github.mikephil.charting.data.Entry(e.getKey(), e.getValue()));
        }

        if (entries.isEmpty()) { binding.lineChartBookings.clear(); return; }

        LineDataSet dataSet = new LineDataSet(entries, "Số đơn");
        dataSet.setColor(0xFF60A5FA);          // Xanh dương
        dataSet.setCircleColor(0xFF60A5FA);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(9f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Đường cong mượt
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(0x3360A5FA);
        dataSet.setFillAlpha(80);

        binding.lineChartBookings.setData(new LineData(dataSet));
        binding.lineChartBookings.animateX(600);
        binding.lineChartBookings.invalidate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
