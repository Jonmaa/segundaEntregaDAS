package com.example.segundaentregadas.models;

public class Lugar {
    private int id;
    private String nombre;
    private String descripcion;
    private double latitud;
    private double longitud;
    private String imagen_url;
    private int usuario_id;

    public Lugar(String nombre, String descripcion, double latitud, double longitud,
                 String imagen_url, int usuario_id) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.imagen_url = imagen_url;
        this.usuario_id = usuario_id;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public String getImagenUrl() {
        return imagen_url;
    }

    public int getUsuarioId() {
        return usuario_id;
    }
}
