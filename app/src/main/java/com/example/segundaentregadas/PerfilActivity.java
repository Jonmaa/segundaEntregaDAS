package com.example.segundaentregadas;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.FileOutputStream;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.segundaentregadas.models.FotoResponse;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import com.google.gson.JsonObject;
import android.Manifest;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PerfilActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private String currentPhotoPath;
    private ImageView imgPerfil;
    private TextView tvNombre, tvEmail;
    private Button btnCambiarFoto;
    private int userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        // Inicializar vistas
        imgPerfil = findViewById(R.id.imgPerfil);
        tvNombre = findViewById(R.id.tvNombre);
        tvEmail = findViewById(R.id.tvEmail);
        btnCambiarFoto = findViewById(R.id.btnCambiarFoto);

        // Obtener datos del usuario
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userId = prefs.getInt("user_id", 0);
        String nombre = prefs.getString("nombre", "");
        String email = prefs.getString("email", "");
        String fotoUrl = prefs.getString("foto_url", "");

        // Mostrar datos
        tvNombre.setText("Nombre: " + nombre);
        tvEmail.setText("Email: " + email);

        // Cargar foto de perfil si existe
        if (!fotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(fotoUrl)
                    .placeholder(R.drawable.default_profile)
                    .into(imgPerfil);
        }

        // Configurar botón de cambio de foto
        btnCambiarFoto.setOnClickListener(v -> {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_IMAGE_CAPTURE);
            }
        });

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            finish(); // This will close the activity and return to MapActivity
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error al crear el archivo", Toast.LENGTH_SHORT).show();
            }

            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.segundaentregadas.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Mostrar imagen capturada
            Glide.with(this)
                    .load(currentPhotoPath)
                    .into(imgPerfil);

            // Subir al servidor
            uploadImageToServer();
        }
    }

    private void uploadImageToServer() {
        File file = new File(currentPhotoPath);

        // Agregar logs de depuración
        Log.d("PerfilActivity", "Path: " + currentPhotoPath);
        Log.d("PerfilActivity", "File exists: " + file.exists());
        Log.d("PerfilActivity", "File size: " + file.length());
        Log.d("PerfilActivity", "User ID: " + userId);

        File compressedFile = compressImage(file);

        if (!compressedFile.exists()) {
            runOnUiThread(() ->
                    Toast.makeText(this, "Error: Archivo no encontrado", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), compressedFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("foto", compressedFile.getName(), requestFile);
        RequestBody userIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(userId));

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<FotoResponse> call = apiService.subirFotoPerfil(body, userIdBody);

        call.enqueue(new Callback<FotoResponse>() {
            @Override
            public void onResponse(Call<FotoResponse> call, Response<FotoResponse> response) {
                Log.d("PerfilActivity", "Response code: " + response.code());
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        FotoResponse fotoResponse = response.body();
                        Log.d("PerfilActivity", "Success response: " + fotoResponse.toString());

                        if (fotoResponse.isSuccess()) {
                            String imageUrl = fotoResponse.getUrl();

                            // Null check before using the URL
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                // 1. Actualizar SharedPreferences
                                SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
                                editor.putString("foto_url", imageUrl);
                                editor.apply();

                                // 2. Mostrar imagen con Glide (con caché desactivada para forzar actualización)
                                Glide.with(PerfilActivity.this)
                                        .load(imageUrl + "?t=" + System.currentTimeMillis())
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .skipMemoryCache(true)
                                        .into(imgPerfil);

                                Toast.makeText(PerfilActivity.this, "Foto actualizada correctamente", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(PerfilActivity.this, "URL de imagen vacía o nula", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(PerfilActivity.this, "Error: " +
                                            (fotoResponse.getMessage() != null ? fotoResponse.getMessage() : "Desconocido"),
                                    Toast.LENGTH_LONG).show();
                        }
                    } else if (response.errorBody() != null) {
                        String errorBody = response.errorBody().string();
                        Log.e("PerfilActivity", "Error body: " + errorBody);
                        Toast.makeText(PerfilActivity.this, "Error del servidor: " + errorBody, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("PerfilActivity", "Error processing response", e);
                    Toast.makeText(PerfilActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<FotoResponse> call, Throwable t) {
                runOnUiThread(() ->
                        Toast.makeText(PerfilActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    private File compressImage(File originalFile) {
        try {
            // Create bitmap from file
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(originalFile.getAbsolutePath(), bmOptions);

            // Calculate target size (resize if too large)
            int photoWidth = bmOptions.outWidth;
            int photoHeight = bmOptions.outHeight;
            int scaleFactor = Math.max(1, Math.min(photoWidth/1200, photoHeight/1200));

            // Decode the image file into a smaller bitmap
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;

            Bitmap bitmap = BitmapFactory.decodeFile(originalFile.getAbsolutePath(), bmOptions);

            // Create output compressed file
            File compressedFile = new File(getCacheDir(), "compressed_" + originalFile.getName());
            FileOutputStream fos = new FileOutputStream(compressedFile);

            // Compress to JPEG with 70% quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
            fos.flush();
            fos.close();

            Log.d("PerfilActivity", "Compressed Path: " + compressedFile.getAbsolutePath());
            Log.d("PerfilActivity", "Compression: Original=" + originalFile.length() + " bytes, Compressed=" + compressedFile.length() + " bytes");

            return compressedFile;
        } catch (Exception e) {
            Log.e("PerfilActivity", "Image compression failed", e);
            return originalFile; // Return original if compression fails
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(PerfilActivity.this, message, Toast.LENGTH_LONG).show();
            Log.e("PerfilActivity", message);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Se necesita permiso de la cámara", Toast.LENGTH_SHORT).show();
            }
        }
    }
}