package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.List;

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

    private static PitchRepository instance;

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
        result.setValue(Resource.loading(null));

        // Detach listener cũ nếu đang có (tránh duplicate)
        detachPitchListListener();

        pitchListListener = mDb.collection(Constants.COL_PITCHES)
                .addSnapshotListener((snapshots, error) -> {
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
                        if (p != null && !Constants.PITCH_MAINTENANCE.equals(p.getStatus())
                                && !Constants.PITCH_CLOSED.equals(p.getStatus())) {
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
        result.setValue(Resource.loading(null));
        detachPitchListListener();

        pitchListListener = mDb.collection(Constants.COL_PITCHES)
                .whereEqualTo("type", type)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<Pitch> pitches = new java.util.ArrayList<>();
                    if (snapshots != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                            Pitch p = doc.toObject(Pitch.class);
                            if (p != null && !Constants.PITCH_MAINTENANCE.equals(p.getStatus())
                                    && !Constants.PITCH_CLOSED.equals(p.getStatus())) {
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
     * Tìm kiếm sân theo prefix tên.
     * Firestore không hỗ trợ LIKE — dùng kỹ thuật range query.
     * Giới hạn: chỉ tìm được prefix (bắt đầu bằng), không phải contains.
     */
    public void searchPitchesByName(String query,
                                    MutableLiveData<Resource<List<Pitch>>> result) {
        if (query == null || query.trim().isEmpty()) {
            getAllPitches(result);
            return;
        }

        result.setValue(Resource.loading(null));
        String normalizedQuery = query.trim();
        // Kỹ thuật: tìm documents có name >= query VÀ name < query + "\uf8ff"
        // "\uf8ff" là ký tự Unicode cao nhất, đảm bảo lấy hết prefix
        String endQuery = normalizedQuery + "\uf8ff";

        mDb.collection(Constants.COL_PITCHES)
                .whereGreaterThanOrEqualTo("name", normalizedQuery)
                .whereLessThanOrEqualTo("name", endQuery)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Pitch> pitches = snapshots.toObjects(Pitch.class);
                    result.setValue(Resource.success(pitches));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(e.getMessage(), null))
                );
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    /** Gọi trong ViewModel.onCleared() để tránh memory leak */
    public void detachPitchListListener() {
        if (pitchListListener != null) {
            pitchListListener.remove();
            pitchListListener = null;
        }
    }
}
