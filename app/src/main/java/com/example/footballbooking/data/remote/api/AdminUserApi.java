package com.example.footballbooking.data.remote.api;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

/** Backend Admin SDK API dùng cho CRUD Firebase Authentication + Firestore profile. */
public interface AdminUserApi {

    @POST("admin/users")
    Call<MutationResponse> create(
            @Header("Authorization") String authorization,
            @Body UserPayload request);

    @PATCH("admin/users/{uid}")
    Call<MutationResponse> update(
            @Header("Authorization") String authorization,
            @Path("uid") String uid,
            @Body UserPayload request);

    @DELETE("admin/users/{uid}")
    Call<MutationResponse> delete(
            @Header("Authorization") String authorization,
            @Path("uid") String uid);

    @Keep
    class UserPayload {
        @SerializedName("email") public String email;
        @SerializedName("password") public String password;
        @SerializedName("displayName") public String displayName;
        @SerializedName("phoneNumber") public String phoneNumber;
        @SerializedName("role") public String role;
        @SerializedName("status") public String status;

        public UserPayload(String email, String password, String displayName,
                           String phoneNumber, String role, String status) {
            this.email = email;
            this.password = password;
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.role = role;
            this.status = status;
        }
    }

    @Keep
    class MutationResponse {
        @SerializedName("ok") public boolean ok;
        @SerializedName("uid") public String uid;
    }
}
