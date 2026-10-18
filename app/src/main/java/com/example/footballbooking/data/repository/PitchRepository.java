package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * PitchRepository — Quản lý dữ liệu sân bóng từ Firestore.
 *
 * ĐẶC BIỆT: Dùng addSnapshotListener() thay vì get() để lắng nghe
 * realtime updates — khi admin cập nhật sân, UI tự động refresh.
 */
public class PitchRepository {

    private final FirebaseFirestore mDb;

    // Lưu listener để detach khi ViewModel bị destroyed (tránh memory leak)
    private ListenerRegistration pitchListListener;
    private long listGeneration;
    private String activeSearchQuery = "";

    private static PitchRepository instance;

    /** A ViewModel owns this instance and its listeners. */
    public static PitchRepository createScoped() { return new PitchRepository(); }

    private PitchRepository() {
        mDb = FirebaseFirestore.getInstance();
    }

    public static synchronized PitchRepository getInstance() {
        if (instance == null) instance = new PitchRepository();
        return instance;
    }

    // ============================================================
    // LẤY DANH SÁCH TẤT CẢ SÂN (Realtime)
    // ============================================================

    /**
     * Lắng nghe realtime danh sách sân (không có bộ lọc).
     * Chỉ lấy sân có status != "maintenance" — khách không thấy sân đang bảo trì.
     *
     * @param result LiveData để push dữ liệu về Fragment
     */
    public void getAllPitches(MutableLiveData<Resource<List<Pitch>>> result) {
        activeSearchQuery = "";
        getAllPitchesInternal(result);
    }

    private void getAllPitchesInternal(MutableLiveData<Resource<List<Pitch>>> result) {
        result.setValue(Resource.loading(null));

        // Detach listener cũ nếu đang có (tránh duplicate)
        detachPitchListListener();
        final long generation = listGeneration;

        pitchListListener = mDb.collection(Constants.COL_PITCHES)
                .addSnapshotListener((snapshots, error) -> {
                    if (generation != listGeneration) return;
                    if (error != null) {
                        result.setValue(Resource.error("Lỗi tải danh sách sân: " + error.getMessage(), null));
                        return;
                    }
                    if (snapshots == null || snapshots.isEmpty()) {
                        result.setValue(Resource.success(new java.util.ArrayList<>())); // empty list
                        return;
                    }
                    List<Pitch> pitches = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                        Pitch p = doc.toObject(Pitch.class);
                        if (isDiscoverable(p) && matchesSearch(p)) {
                            pitches.add(p);
                        }
                    }
                    pitches.sort((p1, p2) -> Float.compare(p2.getRating(), p1.getRating()));
                    result.setValue(Resource.success(pitches));
                });
    }

    // ============================================================
    // LỌC THEO LOẠI SÂN
    // ============================================================

    /**
     * Lấy danh sách sân theo loại (5/7/11 người).
     * @param type "5", "7", hoặc "11" — dùng Constants.PITCH_TYPE_*
     */
    public void getPitchesByType(String type, MutableLiveData<Resource<List<Pitch>>> result) {
        activeSearchQuery = "";
        result.setValue(Resource.loading(null));
        detachPitchListListener();
        final long generation = listGeneration;

        pitchListListener = mDb.collection(Constants.COL_PITCHES)
                .whereEqualTo("type", type)
                .addSnapshotListener((snapshots, error) -> {
                    if (generation != listGeneration) return;
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<Pitch> pitches = new java.util.ArrayList<>();
                    if (snapshots != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                            Pitch p = doc.toObject(Pitch.class);
                            if (isDiscoverable(p) && matchesSearch(p)) {
                                pitches.add(p);
                            }
                        }
                    }
                    pitches.sort((p1, p2) -> Float.compare(p2.getRating(), p1.getRating()));
                    result.setValue(Resource.success(pitches));
                });
    }

    // ============================================================
    // LẤY CHI TIẾT 1 SÂN (Một lần, không realtime)
    // ============================================================

    public void getPitchById(String pitchId, MutableLiveData<Resource<Pitch>> result) {
        result.setValue(Resource.loading(null));

        mDb.collection(Constants.COL_PITCHES)
                .document(pitchId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        result.setValue(Resource.success(doc.toObject(Pitch.class)));
                    } else {
                        result.setValue(Resource.error("Không tìm thấy sân", null));
                    }
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(e.getMessage(), null))
                );
    }

    // ============================================================
    // TÌM KIẾM SÂN THEO TÊN
    // ============================================================

    /**
     * Tìm kiếm sân theo một phần tên hoặc địa chỉ.
     *
     * Firestore range query phân biệt hoa/thường và chỉ tìm được prefix.
     * Với quy mô hiện tại của ứng dụng, ta dùng snapshot realtime của danh sách
     * sân rồi lọc ở client để hỗ trợ tìm gần đúng tiếng Việt: không phân biệt
     * hoa/thường, dấu và vị trí của từ khóa.
     */
    public void searchPitchesByName(String query,
                                    MutableLiveData<Resource<List<Pitch>>> result) {
        activeSearchQuery = normalizeSearchText(query);
        getAllPitchesInternal(result);
    }

    private boolean isDiscoverable(Pitch pitch) {
        return pitch != null
                && pitch.isApprovedForDiscovery()
                && !Constants.PITCH_MAINTENANCE.equals(pitch.getStatus())
                && !Constants.PITCH_CLOSED.equals(pitch.getStatus());
    }

    private boolean matchesSearch(Pitch pitch) {
        if (activeSearchQuery.isEmpty()) return true;
        String searchableText = normalizeSearchText(
                safe(pitch.getName()) + " " + safe(pitch.getAddress())
        );
        return searchableText.contains(activeSearchQuery);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Chuẩn hóa để tìm kiếm tiếng Việt không phân biệt hoa thường và dấu. */
    static String normalizeSearchText(String value) {
        if (value == null) return "";

        String normalized = Normalizer.normalize(
                value.trim().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.replace('đ', 'd').replaceAll("\\s+", " ");
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    /** Gọi trong ViewModel.onCleared() để tránh memory leak */
    public void detachPitchListListener() {
        listGeneration++;
        if (pitchListListener != null) {
            pitchListListener.remove();
            pitchListListener = null;
        }
    }
}
