package com.example.footballbooking.ui.admin.pitch;

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

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.FragmentAdminPitchModerationBinding;
import com.example.footballbooking.ui.admin.AdminViewModel;
import com.example.footballbooking.utils.Resource;

public class AdminPitchModerationFragment extends Fragment {

    private FragmentAdminPitchModerationBinding binding;
    private AdminViewModel viewModel;
    private AdminPitchModerationAdapter adapter;

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

        setupRecyclerView();
        observeViewModel();

        viewModel.loadAllPitchesForModeration();
    }

    private void setupRecyclerView() {
        adapter = new AdminPitchModerationAdapter(requireContext(), (pitch, newStatus) -> {
            viewModel.updatePitchStatus(pitch.getPitchId(), newStatus);
        });
        binding.rvPitches.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPitches.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getAllPitches().observe(getViewLifecycleOwner(), resource -> {
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
