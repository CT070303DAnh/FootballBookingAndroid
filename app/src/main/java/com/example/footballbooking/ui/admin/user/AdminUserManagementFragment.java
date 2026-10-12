package com.example.footballbooking.ui.admin.user;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.remote.api.AdminUserApi;
import com.example.footballbooking.databinding.DialogAdminUserFormBinding;
import com.example.footballbooking.databinding.FragmentAdminUserManagementBinding;
import com.example.footballbooking.ui.admin.AdminViewModel;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminUserManagementFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private FragmentAdminUserManagementBinding binding;
    private AdminViewModel viewModel;
    private AdminUserAdapter adapter;
    private AlertDialog approvalDialog;
    private AlertDialog detailsDialog;
    private AlertDialog userFormDialog;
    private TextView detailsContent;

    private final List<User> allUsers = new ArrayList<>();
    private final List<User> filteredUsers = new ArrayList<>();
    private int currentPage;
    private String searchQuery = "";
    private String selectedRole = "all";
    private String selectedStatus = "all";
    private User selectedHistoryUser;
    private String pendingOwnerAction = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminUserManagementBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
        setupFilters();
        setupRecyclerView();
        setupPagination();
        binding.btnAddUser.setOnClickListener(v -> showUserForm(null));
        observeViewModel();
        viewModel.loadAllUsers();
    }

    private void setupFilters() {
        String[] roles = {"Tất cả vai trò", "Người dùng", "Chủ sân chờ duyệt", "Chủ sân", "Quản trị viên"};
        String[] statuses = {"Tất cả trạng thái", "Đang hoạt động", "Đã khóa"};

        binding.spinnerRole.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, roles));
        binding.spinnerRole.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void onSelected(int position) {
                selectedRole = position == 1 ? Constants.ROLE_CUSTOMER
                        : position == 2 ? Constants.ROLE_OWNER_PENDING
                        : position == 3 ? Constants.ROLE_OWNER
                        : position == 4 ? Constants.ROLE_ADMIN : "all";
                applyFilters();
            }
        });

        binding.spinnerStatus.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, statuses));
        binding.spinnerStatus.setOnItemSelectedListener(new SimpleSelectionListener() {
            @Override public void onSelected(int position) {
                selectedStatus = position == 1 ? Constants.STATUS_ACTIVE
                        : position == 2 ? Constants.STATUS_BLOCKED : "all";
                applyFilters();
            }
        });

        binding.etUserSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void setupRecyclerView() {
        adapter = new AdminUserAdapter(requireContext(), (user, newStatus) ->
                showStatusConfirmation(user, newStatus),
                this::showOwnerApprovalDialog,
                this::showOwnerRejectionDialog,
                this::showUserDetails,
                this::showUserEditDialog,
                this::showUserDeleteDialog);
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUsers.setAdapter(adapter);
    }

    private void setupPagination() {
        binding.btnPreviousPage.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                renderPage();
            }
        });
        binding.btnNextPage.setOnClickListener(v -> {
            if ((currentPage + 1) * PAGE_SIZE < filteredUsers.size()) {
                currentPage++;
                renderPage();
            }
        });
    }

    private void observeViewModel() {
        viewModel.getOwnerApprovalResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            adapter.setApprovalInProgress(resource.isLoading());
            if (!resource.isLoading()) {
                String message = resource.isSuccess()
                        ? pendingOwnerAction : "Thao tác thất bại: " + resource.message;
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                pendingOwnerAction = "";
                viewModel.clearOwnerApprovalResult();
            }
        });

        viewModel.getAllUsers().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.tvEmpty.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.progressBar.setVisibility(View.GONE);
                    allUsers.clear();
                    if (resource.data != null) allUsers.addAll(resource.data);
                    applyFilters();
                    break;
                case ERROR:
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Không tải được người dùng: " + resource.message,
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        viewModel.getActionResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            adapter.setStatusChangeInProgress(resource.isLoading());
            if (!resource.isLoading()) {
                Toast.makeText(requireContext(), resource.isSuccess()
                                ? "Đã cập nhật trạng thái tài khoản"
                                : "Thao tác thất bại: " + resource.message,
                        Toast.LENGTH_LONG).show();
                viewModel.clearActionResult();
            }
        });

        viewModel.getUserCrudResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            adapter.setCrudInProgress(resource.isLoading());
            if (userFormDialog != null && userFormDialog.isShowing()) {
                userFormDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setEnabled(!resource.isLoading());
            }
            if (!resource.isLoading()) {
                if (resource.isSuccess()) {
                    if (userFormDialog != null && userFormDialog.isShowing()) {
                        userFormDialog.dismiss();
                    }
                    Toast.makeText(requireContext(), "Đã cập nhật tài khoản", Toast.LENGTH_LONG).show();
                    // Đồng bộ lại danh sách để thao tác thêm/sửa/xóa phản ánh ngay trên UI.
                    viewModel.loadAllUsers();
                } else if (resource.isError()) {
                    Toast.makeText(requireContext(), "Thao tác thất bại: " + resource.message,
                            Toast.LENGTH_LONG).show();
                }
                viewModel.clearUserCrudResult();
            }
        });

        viewModel.getUserHistory().observe(getViewLifecycleOwner(), resource -> {
            if (detailsDialog == null || !detailsDialog.isShowing() || resource == null) return;
            if (resource.isLoading()) {
                setDetailsText(buildProfileText(selectedHistoryUser) + "\n\nĐang tải lịch sử đặt sân...");
            } else if (resource.isError()) {
                setDetailsText(buildProfileText(selectedHistoryUser)
                        + "\n\nKhông tải được lịch sử: " + resource.message);
            } else {
                setDetailsText(buildProfileText(selectedHistoryUser)
                        + "\n\n" + buildHistoryText(resource.data));
            }
        });
    }

    private void applyFilters() {
        filteredUsers.clear();
        for (User user : allUsers) {
            if (!"all".equals(selectedRole) && !selectedRole.equals(user.getRole())) continue;
            String status = user.getStatus() == null ? Constants.STATUS_ACTIVE : user.getStatus();
            if (!"all".equals(selectedStatus) && !selectedStatus.equals(status)) continue;
            if (!searchQuery.isEmpty()) {
                String searchable = (safe(user.getDisplayName()) + " "
                        + safe(user.getEmail()) + " " + safe(user.getPhoneNumber())).toLowerCase(Locale.ROOT);
                if (!searchable.contains(searchQuery)) continue;
            }
            filteredUsers.add(user);
        }
        currentPage = Math.min(currentPage, Math.max(0, pageCount() - 1));
        renderPage();
    }

    private int pageCount() {
        return Math.max(1, (filteredUsers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void renderPage() {
        int from = Math.min(currentPage * PAGE_SIZE, filteredUsers.size());
        int to = Math.min(from + PAGE_SIZE, filteredUsers.size());
        adapter.submitList(new ArrayList<>(filteredUsers.subList(from, to)));
        boolean empty = filteredUsers.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tvEmpty.setText(allUsers.isEmpty() ? "Không có người dùng nào" : "Không có kết quả phù hợp");
        binding.tvPageInfo.setText("Trang " + (currentPage + 1) + " / " + pageCount()
                + "  (" + filteredUsers.size() + " tài khoản)");
        binding.btnPreviousPage.setEnabled(currentPage > 0);
        binding.btnNextPage.setEnabled((currentPage + 1) * PAGE_SIZE < filteredUsers.size());
    }

    private void showStatusConfirmation(User user, String newStatus) {
        boolean blocking = Constants.STATUS_BLOCKED.equals(newStatus);
        EditText reasonInput = new EditText(requireContext());
        reasonInput.setHint(blocking ? "Nhập lý do khóa (bắt buộc)" : "Lý do mở khóa (không bắt buộc)");
        reasonInput.setSingleLine(false);
        reasonInput.setMaxLines(3);
        reasonInput.setPadding(32, 8, 32, 8);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(blocking ? "Khóa tài khoản?" : "Mở khóa tài khoản?")
                .setMessage((safe(user.getDisplayName()).isEmpty() ? safe(user.getEmail()) : user.getDisplayName()))
                .setView(reasonInput)
                .setPositiveButton(blocking ? "Khóa" : "Mở khóa", null)
                .setNegativeButton("Hủy", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonInput.getText() == null ? "" : reasonInput.getText().toString().trim();
            if (blocking && reason.isEmpty()) {
                reasonInput.setError("Vui lòng nhập lý do khóa");
                return;
            }
            if (reason.length() > 500) {
                reasonInput.setError("Tối đa 500 ký tự");
                return;
            }
            dialog.dismiss();
            viewModel.updateUserStatus(user.getUid(), newStatus, reason);
        }));
        dialog.show();
    }

    private void showUserEditDialog(User user) {
        showUserForm(user);
    }

    private void showUserDeleteDialog(User user) {
        if (Constants.ROLE_ADMIN.equals(user.getRole())) {
            Toast.makeText(requireContext(), "Không thể xóa tài khoản Admin", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa tài khoản?")
                .setMessage("Tài khoản \"" + safe(user.getDisplayName())
                        + "\" sẽ bị xóa khỏi Firebase Authentication và hồ sơ Firestore."
                        + " Lịch sử đặt sân vẫn được giữ lại.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa vĩnh viễn", (dialog, which) -> viewModel.deleteUser(user.getUid()))
                .show();
    }

    private void showUserForm(@Nullable User editing) {
        if (userFormDialog != null && userFormDialog.isShowing()) return;

        boolean creating = editing == null;
        DialogAdminUserFormBinding form = DialogAdminUserFormBinding.inflate(getLayoutInflater());
        form.etAdminUserName.setText(creating ? "" : safe(editing.getDisplayName()));
        form.etAdminUserEmail.setText(creating ? "" : safe(editing.getEmail()));
        form.etAdminUserPhone.setText(creating ? "" : safe(editing.getPhoneNumber()));
        form.etAdminUserPassword.setText("");
        form.etAdminUserPassword.setHint(creating ? "Mật khẩu bắt buộc" : "Mật khẩu mới (không bắt buộc)");

        String[] roleLabels = {"Người dùng", "Chủ sân", "Admin"};
        String[] roleValues = {
                Constants.ROLE_CUSTOMER,
                Constants.ROLE_OWNER,
                Constants.ROLE_ADMIN
        };
        form.spinnerAdminUserRole.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, roleLabels));

        if (!creating) {
            // Tài khoản chủ sân cũ đang chờ duyệt được hiển thị ở lựa chọn Chủ sân.
            String editingRole = Constants.ROLE_OWNER_PENDING.equals(editing.getRole())
                    ? Constants.ROLE_OWNER : editing.getRole();
            form.spinnerAdminUserRole.setSelection(indexOf(roleValues, editingRole));
        }

        userFormDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(creating ? "Thêm tài khoản" : "Sửa tài khoản")
                .setView(form.getRoot())
                .setNegativeButton("Hủy", null)
                .setPositiveButton(creating ? "Tạo tài khoản" : "Lưu thay đổi", null)
                .create();

        userFormDialog.setOnShowListener(ignored -> userFormDialog
                .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String name = textOf(form.etAdminUserName);
                    String email = textOf(form.etAdminUserEmail);
                    String phone = textOf(form.etAdminUserPhone);
                    String password = textOf(form.etAdminUserPassword);
                    String role = roleValues[form.spinnerAdminUserRole.getSelectedItemPosition()];
                    // Tài khoản mới luôn hoạt động; khi sửa thì giữ nguyên trạng thái hiện tại.
                    String status = creating || editing.getStatus() == null
                            ? Constants.STATUS_ACTIVE : editing.getStatus();

                    if (name.isEmpty()) {
                        form.etAdminUserName.setError("Vui lòng nhập họ tên");
                        return;
                    }
                    if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                        form.etAdminUserEmail.setError("Email không hợp lệ");
                        return;
                    }
                    if (creating && password.length() < 6) {
                        form.etAdminUserPassword.setError("Mật khẩu tối thiểu 6 ký tự");
                        return;
                    }
                    if (!password.isEmpty() && password.length() < 6) {
                        form.etAdminUserPassword.setError("Mật khẩu tối thiểu 6 ký tự");
                        return;
                    }

                    AdminUserApi.UserPayload payload = new AdminUserApi.UserPayload(
                            email, password.isEmpty() ? null : password,
                            name, phone, role, status);
                    if (creating) viewModel.createUser(payload);
                    else viewModel.updateUser(editing.getUid(), payload);
                }));
        userFormDialog.show();
    }

    private int indexOf(String[] values, String value) {
        if (value != null) {
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) return i;
            }
        }
        return 0;
    }

    private String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showOwnerApprovalDialog(User user) {
        if (approvalDialog != null && approvalDialog.isShowing()) return;
        approvalDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Duyệt tài khoản chủ sân?")
                .setMessage("Cấp quyền quản lý sân cho " + safe(user.getDisplayName())
                        + " (" + safe(user.getEmail()) + ").")
                .setPositiveButton("Duyệt", (dialog, which) -> {
                    pendingOwnerAction = "Đã duyệt tài khoản chủ sân";
                    viewModel.approveOwner(user.getUid());
                })
                .setNegativeButton("Để sau", null)
                .show();
    }

    private void showOwnerRejectionDialog(User user) {
        EditText reasonInput = new EditText(requireContext());
        reasonInput.setHint("Lý do từ chối (bắt buộc)");
        reasonInput.setSingleLine(false);
        reasonInput.setMaxLines(3);
        reasonInput.setPadding(32, 8, 32, 8);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Từ chối đăng ký chủ sân?")
                .setMessage("Tài khoản sẽ trở về vai trò Người dùng.")
                .setView(reasonInput)
                .setPositiveButton("Từ chối", null)
                .setNegativeButton("Hủy", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonInput.getText() == null ? "" : reasonInput.getText().toString().trim();
            if (reason.isEmpty()) {
                reasonInput.setError("Vui lòng nhập lý do");
                return;
            }
            if (reason.length() > 500) {
                reasonInput.setError("Tối đa 500 ký tự");
                return;
            }
            dialog.dismiss();
            pendingOwnerAction = "Đã từ chối đăng ký chủ sân";
            viewModel.rejectOwner(user.getUid(), reason);
        }));
        dialog.show();
    }

    private void showUserDetails(User user) {
        selectedHistoryUser = user;
        detailsContent = new TextView(requireContext());
        detailsContent.setTextColor(android.graphics.Color.WHITE);
        detailsContent.setTextSize(14);
        detailsContent.setPadding(32, 8, 32, 8);
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(detailsContent);
        detailsDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chi tiết tài khoản")
                .setView(scrollView)
                .setPositiveButton("Đóng", null)
                .create();
        detailsDialog.setOnShowListener(dialog -> {
            setDetailsText(buildProfileText(user) + "\n\nĐang tải lịch sử đặt sân...");
            viewModel.loadUserHistory(user.getUid());
        });
        detailsDialog.show();
    }

    private void setDetailsText(String text) {
        if (detailsContent != null) detailsContent.setText(text);
    }

    private String buildProfileText(User user) {
        if (user == null) return "Không có dữ liệu tài khoản.";
        StringBuilder text = new StringBuilder();
        text.append("Tên: ").append(safe(user.getDisplayName())).append('\n');
        text.append("Email: ").append(safe(user.getEmail())).append('\n');
        text.append("Số điện thoại: ").append(safe(user.getPhoneNumber())).append('\n');
        text.append("Vai trò: ").append(roleLabel(user.getRole())).append('\n');
        text.append("Trạng thái: ").append(Constants.STATUS_ACTIVE.equals(user.getStatus())
                ? "Đang hoạt động" : "Đã khóa").append('\n');
        if (Constants.ROLE_OWNER_PENDING.equals(user.getRole())
                || Constants.OWNER_APPLICATION_REJECTED.equals(user.getOwnerApplicationStatus())) {
            text.append("Trạng thái đăng ký chủ sân: ")
                    .append(safe(user.getOwnerApplicationStatus())).append('\n');
            if (!safe(user.getOwnerDecisionReason()).isEmpty()) {
                text.append("Lý do xử lý: ").append(user.getOwnerDecisionReason()).append('\n');
            }
        }
        if (!safe(user.getStatusReason()).isEmpty()) {
            text.append("Lý do trạng thái: ").append(user.getStatusReason()).append('\n');
        }
        return text.toString().trim();
    }

    private String buildHistoryText(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) return "Lịch sử đặt sân: chưa có dữ liệu.";
        NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        StringBuilder text = new StringBuilder("Lịch sử đặt sân (tối đa 50 đơn gần nhất):\n");
        for (Booking booking : bookings) {
            text.append("\n• ").append(safe(booking.getBookingDate())).append(" ")
                    .append(safe(booking.getStartTime())).append(" - ")
                    .append(safe(booking.getEndTime())).append('\n');
            text.append("  ").append(safe(booking.getPitchName())).append(" | ")
                    .append(statusLabel(booking.getBookingStatus())).append(" | ")
                    .append(paymentLabel(booking.getPaymentStatus())).append('\n');
            text.append("  Tổng tiền: ").append(money.format(booking.getTotalAmount())).append("đ\n");
        }
        return text.toString().trim();
    }

    private String roleLabel(String role) {
        if (Constants.ROLE_ADMIN.equals(role)) return "Quản trị viên";
        if (Constants.ROLE_OWNER.equals(role)) return "Chủ sân";
        if (Constants.ROLE_OWNER_PENDING.equals(role)) return "Chủ sân chờ duyệt";
        return "Người dùng";
    }

    private String statusLabel(String status) {
        if (Constants.STATUS_APPROVED.equals(status)) return "Đã duyệt";
        if (Constants.STATUS_REJECTED.equals(status)) return "Từ chối";
        if (Constants.STATUS_CANCELLED.equals(status)) return "Đã hủy";
        return "Chờ duyệt";
    }

    private String paymentLabel(String status) {
        return Constants.PAYMENT_PAID.equals(status) ? "Đã thanh toán" : "Chưa thanh toán";
    }

    private String safe(String value) { return value == null ? "" : value; }

    private abstract static class SimpleSelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        @Override
        public final void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                         int position, long id) {
            onSelected(position);
        }
        public abstract void onSelected(int position);
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }

    @Override
    public void onDestroyView() {
        if (approvalDialog != null) approvalDialog.dismiss();
        if (detailsDialog != null) detailsDialog.dismiss();
        if (userFormDialog != null) userFormDialog.dismiss();
        approvalDialog = null;
        detailsDialog = null;
        userFormDialog = null;
        detailsContent = null;
        if (binding != null) binding.rvUsers.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }
}
