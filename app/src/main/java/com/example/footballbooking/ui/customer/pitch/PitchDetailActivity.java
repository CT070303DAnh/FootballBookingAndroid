package com.example.footballbooking.ui.customer.pitch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.ActivityPitchDetailBinding;
import com.example.footballbooking.ui.customer.booking.BookingActivity;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * PitchDetailActivity — Chi tiết sân: Gallery, Thông tin, Reviews, Nút đặt sân.
 */
public class PitchDetailActivity extends AppCompatActivity {

    private ActivityPitchDetailBinding binding;
    private PitchViewModel pitchViewModel;
    private String pitchId;
    private Pitch currentPitch;
    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPitchDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        pitchId = getIntent().getStringExtra(Constants.EXTRA_PITCH_ID);

        if (pitchId == null) {
            finish();
            return;
        }

        pitchViewModel = new ViewModelProvider(this).get(PitchViewModel.class);

        setupToolbar();
        setupClickListeners();
        observeViewModel();
        pitchViewModel.loadPitchDetail(pitchId);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupClickListeners() {
        binding.btnBookNow.setOnClickListener(v -> {
            if (currentPitch == null) return;
            if (!Constants.PITCH_AVAILABLE.equals(currentPitch.getStatus())) {
                Snackbar.make(v, "Sân hiện không khả dụng", Snackbar.LENGTH_SHORT).show();
                return;
            }
            // Mở BookingActivity với pitchId
            Intent intent = new Intent(this, BookingActivity.class);
            intent.putExtra(Constants.EXTRA_PITCH_ID, currentPitch.getPitchId());
            startActivity(intent);
        });
    }

    private void observeViewModel() {
        pitchViewModel.getSelectedPitch().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.btnBookNow.setEnabled(false);
                    break;
                case SUCCESS:
                    if (resource.data != null) {
                        currentPitch = resource.data;
                        bindPitchData(currentPitch);
                    }
                    break;
                case ERROR:
                    Snackbar.make(binding.getRoot(),
                            "Lỗi: " + resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void bindPitchData(Pitch pitch) {
        // Collapsing toolbar title
        binding.collapsingToolbar.setTitle(pitch.getName());

        // Banner image
        String imageUrl = (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty())
                ? pitch.getImageUrls().get(0) : null;
        Glide.with(this)
                .load(imageUrl)
                .placeholder(com.example.footballbooking.R.drawable.bg_home_header)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(binding.ivPitchBanner);

        // Basic info
        binding.tvPitchName.setText(pitch.getName());
        binding.tvAddress.setText("📍 " + pitch.getAddress());
        binding.tvDescription.setText(pitch.getDescription());

        // Price
        String priceText = currencyFmt.format((long) pitch.getBasePrice()) + "đ/h";
        binding.tvPrice.setText(priceText);
        binding.tvBottomPrice.setText(priceText);

        // Rating
        binding.ratingBar.setRating(pitch.getRating());
        binding.tvRating.setText(String.format(Locale.getDefault(),
                "%.1f (%d đánh giá)", pitch.getRating(), pitch.getTotalReviews()));

        // Type badge
        binding.chipPitchType.setText("Sân " + pitch.getType() + " người");

        // Status
        if (Constants.PITCH_AVAILABLE.equals(pitch.getStatus())) {
            binding.chipStatus.setText("✓ Còn sân");
            binding.chipStatus.setChipBackgroundColorResource(
                    com.example.footballbooking.R.color.status_approved);
            binding.btnBookNow.setEnabled(true);
        } else {
            binding.chipStatus.setText("Bảo trì / Đóng");
            binding.chipStatus.setChipBackgroundColorResource(
                    com.example.footballbooking.R.color.status_cancelled);
            binding.btnBookNow.setEnabled(false);
            binding.btnBookNow.setText("Sân không khả dụng");
        }

        // Amenities chips
        binding.chipGroupAmenities.removeAllViews();
        if (pitch.getAmenities() != null) {
            for (String amenity : pitch.getAmenities()) {
                Chip chip = new Chip(this);
                chip.setText(amenity);
                chip.setClickable(false);
                chip.setChipBackgroundColorResource(
                        com.example.footballbooking.R.color.background_surface);
                chip.setTextColor(getColor(
                        com.example.footballbooking.R.color.text_secondary));
                binding.chipGroupAmenities.addView(chip);
            }
        }
    }
}
