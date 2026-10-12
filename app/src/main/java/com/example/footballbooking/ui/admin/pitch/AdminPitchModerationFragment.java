package com.example.footballbooking.ui.admin.pitch;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.FragmentAdminPitchModerationBinding;
import com.example.footballbooking.ui.admin.AdminViewModel;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminPitchModerationFragment extends Fragment {

    private FragmentAdminPitchModerationBinding binding;
    private AdminViewModel viewModel;
    private AdminPitchModerationAdapter adapter;

    private final List<Pitch> allPitches = new ArrayList<>();
    private final List<String> ownerIds = new ArrayList<>();
    private int currentMode = 0; // 0 = tất cả sân, 1 = theo chủ sân
    private String selectedStatus = "all";
    private String selectedOwnerId = "all";
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminPitchModerationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
        setupTabs();
        setupFilters();
        setupRecyclerView();
        observeViewModel();
        viewModel.loadAllPitchesForModeration();
    }

    private void setupTabs() {
        binding.tabPitchMode.addTab(binding.tabPitchMode.newTab().setText("Tất cả sân"));
        binding.tabPitchMode.addTab(binding.tabPitchMode.newTab().setText("Theo chủ sân"));
        binding.tabPitchMode.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentMode = tab.getPosition();
                binding.spinnerPitchOwner.setVisibility(currentMode == 1 ? View.VISIBLE : View.GONE);
                applyFilters();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setupFilters() {
        String[] statusLabels = {
                "Tất cả trạng thái", "Chờ duyệt", "Đã duyệt", "Từ chối", "Đang khóa", "Đã ẩn"
        };
        String[] statusValues = {
                "all", Constants.PITCH_MODERATION_PENDING, Constants.PITCH_MODERATION_APPROVED,
                Constants.PITCH_MODERATION_REJECTED, Constants.PITCH_MODERATION_SUSPENDED,
                Constants.PITCH_MODERATION_ARCHIVED
        };
        binding.spinnerPitchStatus.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, statusLabels));
        binding.spinnerPitchStatus.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void onSelected(int position) {
                selectedStatus = statusValues[position];
                applyFilters();
            }
        });

        binding.etPitchSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void setupRecyclerView() {
        adapter = new AdminPitchModerationAdapter((pitch, action) -> {
            if (AdminPitchModerationAdapter.ACTION_EDIT.equals(action)) {
                showEditDialog(pitch);
            } else if (AdminPitchModerationAdapter.ACTION_APPROVE.equals(action)) {
                showModerationConfirmation(pitch, Constants.PITCH_MODERATION_APPROVED, "");
            } else if (AdminPitchModerationAdapter.ACTION_REJECT.equals(action)) {
                showReasonDialog(pitch, Constants.PITCH_MODERATION_REJECTED, "Từ chối sân");
            } else if (AdminPitchModerationAdapter.ACTION_SUSPEND.equals(action)) {
                showReasonDialog(pitch, Constants.PITCH_MODERATION_SUSPENDED, "Khóa sân");
            } else if (AdminPitchModerationAdapter.ACTION_ARCHIVE.equals(action)) {
                showReasonDialog(pitch, Constants.PITCH_MODERATION_ARCHIVED, "Ẩn sân");
            }
        });
        binding.rvPitches.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPitches.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getAllPitches().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isLoading()) {
                binding.progressBar.setVisibility(View.VISIBLE);
                return;
            }
            binding.progressBar.setVisibility(View.GONE);
            if (resource.isSuccess()) {
                allPitches.clear();
                if (resource.data != null) allPitches.addAll(resource.data);
                rebuildOwnerSpinner();
                applyFilters();
            } else if (resource.isError()) {
                Toast.makeText(requireContext(), "Không tải được danh sách sân: "
                        + resource.message, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getActionResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isLoading()) {
                binding.progressBar.setVisibility(View.VISIBLE);
            } else {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), resource.isSuccess()
                                ? "Đã cập nhật thông tin sân"
                                : "Thao tác thất bại: " + resource.message,
                        Toast.LENGTH_LONG).show();
                viewModel.clearActionResult();
            }
        });
    }

    private void rebuildOwnerSpinner() {
        Map<String, String> names = new HashMap<>();
        for (Pitch pitch : allPitches) {
            if (pitch.getOwnerId() != null && !pitch.getOwnerId().trim().isEmpty()) {
                String name = safe(pitch.getOwnerName());
                names.put(pitch.getOwnerId(), name.isEmpty() ? pitch.getOwnerId() : name);
            }
        }
        List<String> sortedIds = new ArrayList<>(names.keySet());
        Collections.sort(sortedIds, Comparator.comparing(id -> names.get(id), String.CASE_INSENSITIVE_ORDER));
        ownerIds.clear();
        ownerIds.add("all");
        ownerIds.addAll(sortedIds);

        List<String> labels = new ArrayList<>();
        labels.add("Tất cả chủ sân");
        for (String id : sortedIds) labels.add(names.get(id));

        binding.spinnerPitchOwner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels));
        binding.spinnerPitchOwner.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void onSelected(int position) {
                selectedOwnerId = position < ownerIds.size() ? ownerIds.get(position) : "all";
                applyFilters();
            }
        });
    }

    private void applyFilters() {
        if (adapter == null) return;
        List<Pitch> filtered = new ArrayList<>();
        for (Pitch pitch : allPitches) {
            if (!"all".equals(selectedStatus)
                    && !selectedStatus.equals(pitch.getEffectiveModerationStatus())) continue;
            if (currentMode == 1 && !"all".equals(selectedOwnerId)
                    && !selectedOwnerId.equals(pitch.getOwnerId())) continue;
            if (!searchQuery.isEmpty()) {
                String searchable = (safe(pitch.getName()) + " " + safe(pitch.getAddress()) + " "
                        + safe(pitch.getOwnerName())).toLowerCase(Locale.ROOT);
                if (!searchable.contains(searchQuery)) continue;
            }
            filtered.add(pitch);
        }
        adapter.submitList(filtered);
        binding.tvPitchCount.setText(filtered.size() + " sân phù hợp / " + allPitches.size() + " sân");
        binding.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showModerationConfirmation(Pitch pitch, String status, String reason) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Duyệt sân bóng?")
                .setMessage(safe(pitch.getName()) + " sẽ được hiển thị cho người dùng nếu trạng thái vận hành cho phép.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Duyệt", (dialog, which) ->
                        viewModel.moderatePitch(pitch.getPitchId(), status, reason))
                .show();
    }

    private void showReasonDialog(Pitch pitch, String status, String title) {
        EditText input = new EditText(requireContext());
        input.setHint("Lý do (bắt buộc)");
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setPadding(32, 8, 32, 8);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(safe(pitch.getName()))
                .setView(input)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String reason = input.getText() == null ? "" : input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        input.setError("Vui lòng nhập lý do");
                    } else if (reason.length() > 500) {
                        input.setError("Tối đa 500 ký tự");
                    } else {
                        dialog.dismiss();
                        viewModel.moderatePitch(pitch.getPitchId(), status, reason);
                    }
                }));
        dialog.show();
    }

    private void showEditDialog(Pitch pitch) {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);

        EditText name = field("Tên sân", pitch.getName(), false);
        EditText address = field("Địa chỉ", pitch.getAddress(), false);
        EditText description = field("Mô tả", pitch.getDescription(), true);
        EditText price = field("Giá mỗi giờ", String.valueOf((long) pitch.getBasePrice()), false);
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner type = new Spinner(requireContext());
        type.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item,
                new String[]{"5", "7", "11"}));
        int typeIndex = "7".equals(pitch.getType()) ? 1 : "11".equals(pitch.getType()) ? 2 : 0;
        type.setSelection(typeIndex);

        form.addView(name);
        form.addView(type);
        form.addView(address);
        form.addView(price);
        form.addView(description);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sửa thông tin sân")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    try {
                        String nameValue = name.getText().toString().trim();
                        String addressValue = address.getText().toString().trim();
                        if (nameValue.isEmpty() || addressValue.isEmpty()) {
                            Toast.makeText(requireContext(), "Tên sân và địa chỉ là bắt buộc", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        pitch.setName(nameValue);
                        pitch.setType(type.getSelectedItem().toString());
                        pitch.setAddress(addressValue);
                        pitch.setDescription(description.getText().toString().trim());
                        pitch.setBasePrice(Double.parseDouble(price.getText().toString().trim()));
                        viewModel.updatePitchDetails(pitch);
                    } catch (NumberFormatException error) {
                        Toast.makeText(requireContext(), "Giá sân không hợp lệ", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private EditText field(String hint, String value, boolean multiline) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setSingleLine(!multiline);
        if (multiline) input.setMinLines(3);
        return input;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private abstract static class SimpleSelectionListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        @Override public final void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                   int position, long id) { onSelected(position); }
        public abstract void onSelected(int position);
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
