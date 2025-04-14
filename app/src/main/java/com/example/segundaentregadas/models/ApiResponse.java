package com.example.segundaentregadas.models;


public class ApiResponse {
    private boolean success;
    private String message;
    private User user;
    private String foto_url;


    public ApiResponse() {}

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFotoUrl() {
        return foto_url;
    }
}
