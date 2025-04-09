package com.example.segundaentregadas.network;

import com.example.segundaentregadas.models.ApiResponse;
import com.example.segundaentregadas.models.LoginRequest;
import com.example.segundaentregadas.models.RegistroRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("login.php")
    Call<ApiResponse> login(@Body LoginRequest loginRequest);

    @POST("registro.php")
    Call<ApiResponse> registrarUsuario(@Body RegistroRequest registroRequest);
}
