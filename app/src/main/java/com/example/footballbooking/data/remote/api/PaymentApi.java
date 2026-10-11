package com.example.footballbooking.data.remote.api;

import androidx.annotation.Keep;
import com.google.gson.annotations.SerializedName;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface PaymentApi {
    @POST("payments/payos/sessions")
    Call<Session> create(@Header("Authorization") String authorization, @Body CreateRequest request);
    @GET("payments/payos/sessions/{paymentRef}")
    Call<Session> status(@Header("Authorization") String authorization, @Path("paymentRef") String paymentRef);
    @POST("payments/payos/sessions/{paymentRef}/reconcile")
    Call<Session> reconcile(@Header("Authorization") String authorization, @Path("paymentRef") String paymentRef);

    @Keep
    class CreateRequest {
        @SerializedName("bookingId") public final String bookingId;
        public CreateRequest(String bookingId) { this.bookingId = bookingId; }
    }
    @Keep
    class Session {
        @SerializedName("bookingId") public String bookingId;
        @SerializedName("paymentRef") public String paymentRef;
        @SerializedName("status") public String status;
        @SerializedName("paymentUrl") public String paymentUrl;
        @SerializedName("checkoutUrl") public String checkoutUrl;
        @SerializedName("qrCode") public String qrCode;
        @SerializedName("accountName") public String accountName;
        @SerializedName("accountNumber") public String accountNumber;
        @SerializedName("amount") public long amount;
    }
}
