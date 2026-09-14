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
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
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
        setupMonthNav();
        observeViewModel();
        loadCurrentMonth();
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
        binding.tvTotalBookings.setText(bookings.size() + " đơn");

        updateBarChart(ownerViewModel.buildRevenueBarEntries(bookings));
        // PieChart & LineChart ẩn trong owner view (chỉ giữ BarChart)
        binding.pieChartStatus.setVisibility(View.GONE);
        binding.lineChartBookings.setVisibility(View.GONE);
    }

    private void setupChartStyle() {
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
    }

    private void updateBarChart(List<BarEntry> entries) {
        if (entries.isEmpty()) { binding.barChartRevenue.clear(); return; }
        BarDataSet dataSet = new BarDataSet(entries, "Doanh thu");
        dataSet.setColor(0xFFFF9800);  // Màu cam — phân biệt với Admin xanh lá
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(9f);

        binding.barChartRevenue.setData(new BarData(dataSet));
        binding.barChartRevenue.animateY(800, Easing.EaseInOutQuart);
        binding.barChartRevenue.invalidate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
