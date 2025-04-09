package com.example.segundaentregadas.models;


public class ApiResponse {
    private boolean success;
    private String message;
    private User user; // Opcional: solo si el endpoint devuelve datos de usuario

    // Constructor (puede estar vacío si usas Gson)
    public ApiResponse() {}

    // Getters y Setters (obligatorios para Retrofit)
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
}
