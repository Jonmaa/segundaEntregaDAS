package com.example.segundaentregadas.models;

public class LoginRequest {
    private String email;
    private String password;

    // Constructor
    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // Getters (Retrofit los usa para serializar)
    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}