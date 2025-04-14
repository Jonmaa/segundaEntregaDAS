package com.example.segundaentregadas.network;

import com.example.segundaentregadas.models.ApiResponse;
import com.example.segundaentregadas.models.FotoResponse;
import com.example.segundaentregadas.models.LoginRequest;
import com.example.segundaentregadas.models.RegistroRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {
    @POST("login.php")
    Call<ApiResponse> login(@Body LoginRequest loginRequest);

    @POST("registro.php")
    Call<ApiResponse> registrarUsuario(@Body RegistroRequest registroRequest);

    @Multipart
    @POST("subir_foto_perfil.php")
    Call<FotoResponse> subirFotoPerfil(
            @Part MultipartBody.Part foto,
            @Part("user_id") RequestBody userId
    );

    @Multipart
    @POST("subir_imagen_lugar.php")
    Call<FotoResponse> subirImagenLugar(
            @Part MultipartBody.Part imagen
    );

    @POST("lugares.php")
    Call<ApiResponse> agregarLugar(@Body JsonObject lugarData);

    @GET("lugares.php")
    Call<JsonArray> obtenerLugares();

    @POST("actualizar_usuario.php")
    Call<ApiResponse> actualizarUsuario(@Body JsonObject userData);
}
