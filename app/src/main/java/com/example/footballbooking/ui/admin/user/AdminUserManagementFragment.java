package com.example.footballbooking.ui.admin.user;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.User;
import com.example.footballbooking.databinding.FragmentAdminUserManagementBinding;
import com.example.footballbooking.ui.admin.AdminViewModel;
import com.example.footballbooking.utils.Resource;

public class AdminUserManagementFragment extends Fragment {

    private FragmentAdminUserManagementBinding binding;
    private AdminViewModel viewModel;
    private AdminUserAdapter adapter;

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

        // Sử dụng chung ViewModel của activity
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        setupRecyclerView();
        observeViewModel();

        viewModel.loadAllUsers();
    }

    private void setupRecyclerView() {
        adapter = new AdminUserAdapter(requireContext(), (user, newStatus) -> {
            viewModel.updateUserStatus(user.getUid(), newStatus);
        });
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUsers.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getAllUsers().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.tvEmpty.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.progressBar.setVisibility(View.GONE);
                    if (resource.data != null && !resource.data.isEmpty()) {
                        binding.tvEmpty.setVisibility(View.GONE);
                        adapter.submitList(resource.data);
                    } else {
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        adapter.submitList(null);
                    }
                    break;
                case ERROR:
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Lỗi: " + resource.message, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        viewModel.getActionResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.ERROR) {
                Toast.makeText(requireContext(), "Thất bại: " + resource.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
