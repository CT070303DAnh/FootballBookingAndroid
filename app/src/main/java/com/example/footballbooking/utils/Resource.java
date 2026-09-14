package com.example.footballbooking.utils;

/**
 * Generic state wrapper cho tất cả LiveData trong ViewModel.
 *
 * CÁCH DÙNG trong Fragment/Activity:
 * viewModel.loginResult.observe(this, resource -> {
 *     switch (resource.status) {
 *         case LOADING: showLoading(); break;
 *         case SUCCESS: navigateToHome(resource.data); break;
 *         case ERROR:   showError(resource.message); break;
 *     }
 * });
 */
public class Resource<T> {

    public enum Status { SUCCESS, ERROR, LOADING }

    public final Status status;
    public final T data;
    public final String message;

    private Resource(Status status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public static <T> Resource<T> success(T data) {
        return new Resource<>(Status.SUCCESS, data, null);
    }

    public static <T> Resource<T> error(String message, T data) {
        return new Resource<>(Status.ERROR, data, message);
    }

    public static <T> Resource<T> loading(T data) {
        return new Resource<>(Status.LOADING, data, null);
    }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError()   { return status == Status.ERROR; }
    public boolean isLoading() { return status == Status.LOADING; }
}
