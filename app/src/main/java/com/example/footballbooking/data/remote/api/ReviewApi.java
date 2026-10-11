package com.example.footballbooking.data.remote.api;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ReviewApi {
    @POST("reviews")
    Call<CreateResponse> create(@Header("Authorization") String authorization,
                                @Body CreateRequest request);

    @Keep
    class CreateRequest {
        @SerializedName("bookingId") public final String bookingId;
        @SerializedName("rating") public final float rating;
        @SerializedName("comment") public final String comment;

        public CreateRequest(String bookingId, float rating, String comment) {
            this.bookingId = bookingId;
            this.rating = rating;
            this.comment = comment;
        }
    }

    @Keep
    class CreateResponse {
        @SerializedName("reviewId") public String reviewId;
        @SerializedName("pitchId") public String pitchId;
        @SerializedName("rating") public float rating;
        @SerializedName("totalReviews") public int totalReviews;
    }
}
