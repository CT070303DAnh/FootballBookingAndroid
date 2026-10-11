package com.example.footballbooking.ui.customer.availability;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.data.repository.AvailabilityRepository;
import com.example.footballbooking.utils.Resource;

import java.util.List;

public class AvailabilityViewModel extends ViewModel {

    private final AvailabilityRepository repository = new AvailabilityRepository();
    private final MutableLiveData<Resource<List<Pitch>>> availablePitches = new MutableLiveData<>();
    private long generation;

    public LiveData<Resource<List<Pitch>>> getAvailablePitches() {
        return availablePitches;
    }

    public void load(String date, TimeSlot slot) {
        final long request = ++generation;
        availablePitches.setValue(Resource.loading(null));
        repository.findAvailablePitches(date, slot)
                .addOnSuccessListener(pitches -> {
                    if (request == generation) {
                        availablePitches.setValue(Resource.success(pitches));
                    }
                })
                .addOnFailureListener(error -> {
                    if (request == generation) {
                        String message = error.getMessage() == null
                                ? "Không tải được sân còn trống"
                                : error.getMessage();
                        availablePitches.setValue(Resource.error(message, null));
                    }
                });
    }
}
