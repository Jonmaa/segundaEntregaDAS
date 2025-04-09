package com.example.segundaentregadas.models;

public class RegistroRequest {
    private String nombre;
    private String email;
    private String password;

    public RegistroRequest(String nombre, String email, String password) {
        this.nombre = nombre;
        this.email = email;
        this.password = password;
    }

    // Getters (necesarios para Retrofit)
    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}