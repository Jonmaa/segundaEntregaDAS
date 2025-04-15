package com.example.segundaentregadas;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
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
import com.example.segundaentregadas.models.ApiResponse;
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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.google.gson.JsonObject;

public class PerfilActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private String currentPhotoPath;
    private ImageView imgPerfil;
    private TextView tvNombre, tvEmail;
    private Button btnCambiarFoto;
    private int userId;
    private Button btnEditarNombre;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        imgPerfil = findViewById(R.id.imgPerfil);
        tvNombre = findViewById(R.id.tvNombre);
        tvEmail = findViewById(R.id.tvEmail);
        btnCambiarFoto = findViewById(R.id.btnCambiarFoto);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userId = prefs.getInt("user_id", 0);
        String nombre = prefs.getString("nombre", "");
        String email = prefs.getString("email", "");
        String fotoUrl = prefs.getString("foto_url", "");
        Log.d("PerfilActivity", "User ID: " + userId);
        Log.d("PerfilActivity", "Nombre: " + nombre);
        Log.d("PerfilActivity", "Email: " + email);
        Log.d("PerfilActivity", "Foto URL: " + fotoUrl);

        tvNombre.setText("Nombre: " + nombre);
        tvEmail.setText("Email: " + email);

        Glide.get(this).clearMemory();
        new Thread(() -> {
            Glide.get(this).clearDiskCache();
        }).start();

        // Cargar foto de perfil si existe
        if (!fotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(fotoUrl + "?t=" + System.currentTimeMillis())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .placeholder(R.drawable.default_profile)
                    .into(imgPerfil);
        }

        btnCambiarFoto.setOnClickListener(v -> {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_IMAGE_CAPTURE);
            }
        });

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            finish();
        });

        btnEditarNombre = findViewById(R.id.btnEditarNombre);

        btnEditarNombre.setOnClickListener(v -> {
            showEditNameDialog();
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

            // Continuar solo si se creó el archivo
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.segundaentregadas.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void showEditNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cambiar nombre");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(tvNombre.getText().toString().replace("Nombre: ", ""));
        builder.setView(input);

        builder.setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    updateUserName(newName);
                }
            }
        });

        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void updateUserName(String newName) {
        // Actualizar nombre en la base de datos local
        ContentValues values = new ContentValues();
        values.put("_id", userId);
        values.put("nombre", newName);

        // Actualizar en la base de datos
        getContentResolver().update(
                Uri.parse("content://com.example.segundaentregadas.userprovider/users/" + userId),
                values,
                null,
                null
        );

        // Actualizar nombre en preferencias
        SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
        editor.putString("nombre", newName);
        editor.apply();

        // Actualizar TextView
        tvNombre.setText("Nombre: " + newName);

        Toast.makeText(PerfilActivity.this, "Nombre actualizado correctamente", Toast.LENGTH_SHORT).show();
    }

    private File createImageFile() throws IOException {

        // Crear un nombre de archivo único
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

            uploadImageToServer();
        }
    }

    private void uploadImageToServer() {
        File file = new File(currentPhotoPath);

        // Comprimir para que no de problemas de tamaño
        File compressedFile = compressImage(file);

        if (!compressedFile.exists()) {
            runOnUiThread(() ->
                    Toast.makeText(this, "Error: Archivo no encontrado", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        // Crear el cuerpo de la solicitud
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), compressedFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("foto", compressedFile.getName(), requestFile);
        RequestBody userIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(userId));

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<FotoResponse> call = apiService.subirFotoPerfil(body, userIdBody);

        call.enqueue(new Callback<FotoResponse>() {
            @Override
            public void onResponse(Call<FotoResponse> call, Response<FotoResponse> response) {

                // Actualizar UI si success
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        FotoResponse fotoResponse = response.body();
                        if (fotoResponse.isSuccess()) {
                            String imageUrl = fotoResponse.getUrl();

                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                // Actualizar SharedPreferences
                                SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
                                editor.putString("foto_url", imageUrl);
                                editor.apply();

                                // Limpiar la cache de Glide para esta imagen específica
                                Glide.get(PerfilActivity.this).clearMemory();
                                new Thread(() -> {
                                    Glide.get(PerfilActivity.this).clearDiskCache();
                                }).start();

                                // Cargar la imagen con un timestamp para evitar cache
                                runOnUiThread(() -> {
                                    Glide.with(PerfilActivity.this)
                                            .load(imageUrl + "?t=" + System.currentTimeMillis())
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .skipMemoryCache(true)
                                            .into(imgPerfil);

                                    Toast.makeText(PerfilActivity.this, "Foto actualizada correctamente", Toast.LENGTH_SHORT).show();
                                });
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
                        Toast.makeText(PerfilActivity.this, "Error del servidor: " + errorBody, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(PerfilActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<FotoResponse> call, Throwable t) {
                Log.e("PerfilActivity", "Network error", t);
                runOnUiThread(() ->
                        Toast.makeText(PerfilActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    private File compressImage(File originalFile) {
        try {
            // Corregir la rotación
            File rotatedFile = rotateImageIfNeeded(originalFile);

            // Crear bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(rotatedFile.getAbsolutePath(), bmOptions);

            int photoWidth = bmOptions.outWidth;
            int photoHeight = bmOptions.outHeight;
            int scaleFactor = Math.max(1, Math.min(photoWidth/1200, photoHeight/1200));

            // Decodificar el bitmap con la escala
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;

            Bitmap bitmap = BitmapFactory.decodeFile(rotatedFile.getAbsolutePath(), bmOptions);

            File compressedFile = new File(getCacheDir(), "compressed_" + originalFile.getName());
            FileOutputStream fos = new FileOutputStream(compressedFile);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
            fos.flush();
            fos.close();

            return compressedFile;

        } catch (Exception e) {
            return originalFile; // En caso de fallo devolver la imagen sin comprimir
        }
    }

    private File rotateImageIfNeeded(File imageFile) {

        // Al cargar la foto en el perfil siempre sale rotada 90º a la izquierda
        try {
            // Obtener el bitmap original
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

            // Obtener la orientación de la imagen
            android.media.ExifInterface exif = new android.media.ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL);

            // Rotación según la orientación
            int rotationAngle = 0;
            switch (orientation) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                    rotationAngle = 90;
                    break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                    rotationAngle = 180;
                    break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                    rotationAngle = 270;
                    break;
            }

            // Si la imagen necesita rotación
            if (rotationAngle != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationAngle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                        matrix, true);

                // Guardar la imagen rotada en un nuevo archivo
                File rotatedFile = new File(getCacheDir(), "rotated_" + imageFile.getName());
                FileOutputStream fos = new FileOutputStream(rotatedFile);
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();

                return rotatedFile;
            }

            return imageFile;
        } catch (Exception e) {
            return imageFile; // Devolver el archivo original en caso de error
        }
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