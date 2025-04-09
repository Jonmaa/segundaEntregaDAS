package com.example.segundaentregadas.models;

public class FotoResponse {
    private boolean success;
    private String url;
    private String message;

    // Getters y Setters (obligatorios)
    public boolean isSuccess() { return success; }
    public String getUrl() { return url; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "FotoResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}