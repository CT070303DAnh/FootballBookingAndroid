package com.example.footballbooking.ui.customer.pitch;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.repository.PitchRepository;
import com.example.footballbooking.utils.Resource;

import java.util.List;

/**
 * PitchViewModel — Dùng chung cho PitchListFragment, PitchMapFragment,
 * HomeFragment (shared ViewModel qua Activity scope).
 *
 * QUAN TRỌNG: ViewModel này được share qua CustomerMainActivity,
 * nên data không bị reload khi switch tab.
 */
public class PitchViewModel extends ViewModel {

    private final PitchRepository pitchRepository;

    // LiveData các Fragment observe
    private final MutableLiveData<Resource<List<Pitch>>> pitchList = new MutableLiveData<>();
    private final MutableLiveData<Resource<Pitch>> selectedPitch = new MutableLiveData<>();

    // Filter state hiện tại
    private String currentFilter = "all"; // "all", "5", "7", "11"
    private String currentSearchQuery = "";

    public PitchViewModel() {
        pitchRepository = PitchRepository.getInstance();
        // Load danh sách sân ngay khi ViewModel tạo
        loadAllPitches();
    }

    // ============================================================
    // EXPOSE LiveData
    // ============================================================

    public MutableLiveData<Resource<List<Pitch>>> getPitchList() {
        return pitchList;
    }

    public MutableLiveData<Resource<Pitch>> getSelectedPitch() {
        return selectedPitch;
    }

    public String getCurrentFilter() { return currentFilter; }

    // ============================================================
    // ACTIONS
    // ============================================================

    public void loadAllPitches() {
        currentFilter = "all";
        pitchRepository.getAllPitches(pitchList);
    }

    public void filterByType(String type) {
        if (type.equals(currentFilter)) return; // Không reload nếu filter không đổi
        currentFilter = type;
        pitchRepository.getPitchesByType(type, pitchList);
    }

    public void searchPitches(String query) {
        currentSearchQuery = query;
        if (query == null || query.trim().isEmpty()) {
            // Quay về filter hiện tại khi xóa search
            applyCurrentFilter();
        } else {
            pitchRepository.searchPitchesByName(query, pitchList);
        }
    }

    public void loadPitchDetail(String pitchId) {
        pitchRepository.getPitchById(pitchId, selectedPitch);
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private void applyCurrentFilter() {
        if ("all".equals(currentFilter)) {
            pitchRepository.getAllPitches(pitchList);
        } else {
            pitchRepository.getPitchesByType(currentFilter, pitchList);
        }
    }

    /** Cleanup realtime listener khi ViewModel bị destroy */
    @Override
    protected void onCleared() {
        super.onCleared();
        pitchRepository.detachPitchListListener();
    }
}
