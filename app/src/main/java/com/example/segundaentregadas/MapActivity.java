package com.example.segundaentregadas;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.example.segundaentregadas.models.ApiResponse;
import com.example.segundaentregadas.models.FotoResponse;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapActivity extends AppCompatActivity {
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private Button btnLogout;
    private float lastTouchX, lastTouchY;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int REQUEST_IMAGE_PICK = 3;
    private AlertDialog currentDialog;
    private GeoPoint selectedLocation;
    private Uri selectedImageUri;
    private static final String TAG = "MapActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Configuración inicial de osmdroid
        Configuration.getInstance().setUserAgentValue(getPackageName());

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK); // Estilo de mapa
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        // Botón de Cerrar Sesión
        btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(MapActivity.this)
                    .setTitle("Cerrar Sesión")
                    .setMessage("¿Estás seguro?")
                    .setPositiveButton("Sí", (dialog, which) -> cerrarSesion())
                    .setNegativeButton("No", null)
                    .show();
        });

        Button btnVerPerfil = findViewById(R.id.btnVerPerfil);
        btnVerPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, PerfilActivity.class);

            // Pasa los datos del usuario (ejemplo usando SharedPreferences)
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            intent.putExtra("nombre", prefs.getString("nombre", ""));
            intent.putExtra("email", prefs.getString("email", ""));

            startActivity(intent);
        });

        // Solicitar permisos si no están concedidos
        requestPermissionsIfNecessary();

        // Configurar la ubicación del usuario
        setupLocationOverlay();

        // Configurar listeners del mapa
        setupMapListeners();

        // Cargar todos los lugares desde el servidor
        cargarLugares();
    }

    private void cargarLugares() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.obtenerLugares().enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray lugaresArray = response.body();
                        for (int i = 0; i < lugaresArray.size(); i++) {
                            JsonObject lugar = lugaresArray.get(i).getAsJsonObject();
                            String nombre = lugar.get("nombre").getAsString();
                            String descripcion = lugar.get("descripcion").getAsString();
                            double latitud = lugar.get("latitud").getAsDouble();
                            double longitud = lugar.get("longitud").getAsDouble();
                            String imagenUrl = lugar.has("imagen_url") ? lugar.get("imagen_url").getAsString() : null;

                            GeoPoint location = new GeoPoint(latitud, longitud);
                            addMarkerToMap(nombre, descripcion, location, imagenUrl);
                        }
                        Log.d(TAG, "Lugares cargados correctamente: " + lugaresArray.size());
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar lugares: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Error en respuesta: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                Log.e(TAG, "Error de red al cargar lugares: " + t.getMessage());
            }
        });
    }

    private void setupMapListeners() {
        // Replace the current implementation with this
        mapView.getOverlays().add(new org.osmdroid.views.overlay.gestures.RotationGestureOverlay(mapView));

        // Add this custom overlay for long press detection
        mapView.getOverlays().add(new org.osmdroid.views.overlay.Overlay() {
            @Override
            public boolean onLongPress(MotionEvent e, MapView mapView) {
                // Convert screen coordinates to map coordinates
                IGeoPoint point = mapView.getProjection().fromPixels((int)e.getX(), (int)e.getY());
                showAddPointOfInterestDialog(new GeoPoint(point.getLatitude(), point.getLongitude()));
                return true;
            }
        });

        // This will force the map to recognize the overlay we just added
        mapView.invalidate();
    }

    private void showAddPointOfInterestDialog(GeoPoint location) {
        selectedLocation = location;
        selectedImageUri = null;

        // Create dialog layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_poi, null);
        EditText etName = dialogView.findViewById(R.id.etPoiName);
        EditText etDescription = dialogView.findViewById(R.id.etPoiDescription);
        Button btnSelectImage = dialogView.findViewById(R.id.btnSelectImage);
        final ImageView imgPreview = dialogView.findViewById(R.id.imgPreview);

        btnSelectImage.setOnClickListener(v -> {
            // Open gallery to select image
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_IMAGE_PICK);
        });

        currentDialog = new AlertDialog.Builder(this)
                .setTitle("Añadir punto de interés")
                .setView(dialogView)
                .setPositiveButton("Guardar", null) // Set null initially
                .setNegativeButton("Cancelar", null)
                .create();

        currentDialog.show();

        // Override the positive button
        currentDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(MapActivity.this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedImageUri != null) {
                uploadImageAndSavePoi(selectedImageUri, name, description, location);
            } else {
                savePoi(name, description, location, null);
            }
            currentDialog.dismiss();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            Log.d(TAG, "Selected image URI: " + selectedImageUri);

            // Use the class-level dialog reference
            if (currentDialog != null && currentDialog.isShowing()) {
                ImageView imgPreview = currentDialog.findViewById(R.id.imgPreview);
                if (imgPreview != null) {
                    imgPreview.setImageURI(selectedImageUri);
                    imgPreview.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void uploadImageAndSavePoi(Uri imageUri, String name, String description, GeoPoint location) {
        try {
            // Convert URI to file
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            File tempFile = File.createTempFile("poi_image", ".jpg", getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            inputStream.close();

            // Compress image
            File compressedFile = compressImage(tempFile);

            // Upload image
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), compressedFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("imagen", compressedFile.getName(), requestFile);

            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            apiService.subirImagenLugar(body).enqueue(new Callback<FotoResponse>() {
                @Override
                public void onResponse(Call<FotoResponse> call, Response<FotoResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        String imageUrl = response.body().getUrl();
                        savePoi(name, description, location, imageUrl);
                    } else {
                        Toast.makeText(MapActivity.this, "Error al subir la imagen", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<FotoResponse> call, Throwable t) {
                    Toast.makeText(MapActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Network error: ", t);
                }
            });
        } catch (IOException e) {
            Toast.makeText(this, "Error al procesar la imagen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error processing image: ", e);
        }
    }

    private File compressImage(File originalFile) {
        try {
            // Create bitmap from file
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(originalFile.getAbsolutePath(), bmOptions);

            // Calculate target size
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

            Log.d(TAG, "Compression: Original=" + originalFile.length() + " bytes, Compressed=" + compressedFile.length() + " bytes");

            return compressedFile;
        } catch (Exception e) {
            Log.e(TAG, "Image compression failed", e);
            return originalFile; // Return original if compression fails
        }
    }

    private void savePoi(String name, String description, GeoPoint location, String imageUrl) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", 0);

        if (userId == 0) {
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObject lugarData = new JsonObject();
        lugarData.addProperty("nombre", name);
        lugarData.addProperty("descripcion", description);
        lugarData.addProperty("latitud", location.getLatitude());
        lugarData.addProperty("longitud", location.getLongitude());
        lugarData.addProperty("imagen_url", imageUrl != null ? imageUrl : "");
        lugarData.addProperty("usuario_id", userId);

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.agregarLugar(lugarData).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(MapActivity.this, "Punto de interés guardado", Toast.LENGTH_SHORT).show();
                    addMarkerToMap(name, description, location, imageUrl);
                } else {
                    Toast.makeText(MapActivity.this, "Error al guardar punto de interés", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error saving POI: " + (response.body() != null ? response.body().getMessage() : "Unknown error"));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Toast.makeText(MapActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Network error when saving POI: ", t);
            }
        });
    }

    private void addMarkerToMap(String title, String description, GeoPoint location, String imageUrl) {
        Marker marker = new Marker(mapView);
        marker.setPosition(location);
        marker.setTitle(title);
        marker.setSnippet(description);

        try {
            // Always use a consistent drawable resource that definitely exists
            int drawableResource = R.drawable.map_marker_good;

            android.graphics.drawable.Drawable originalIcon = getResources().getDrawable(drawableResource);
            android.graphics.drawable.BitmapDrawable bd = (android.graphics.drawable.BitmapDrawable) originalIcon;
            Bitmap bitmap = bd.getBitmap();

            // Scale to make it smaller
            float scale = 0.1f;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,
                    (int)(bitmap.getWidth() * scale),
                    (int)(bitmap.getHeight() * scale),
                    true);

            android.graphics.drawable.Drawable scaledIcon = new android.graphics.drawable.BitmapDrawable(
                    getResources(), scaledBitmap);

            marker.setIcon(scaledIcon);
        } catch (Exception e) {
            // If any error occurs with the custom icon, use default marker
            Log.e(TAG, "Error setting marker icon: " + e.getMessage());
            // marker will use its default icon
        }

        mapView.getOverlays().add(marker);
        mapView.invalidate(); // Refresh map
    }

    private void requestPermissionsIfNecessary() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE, // Necesario para caché de mapas
                Manifest.permission.READ_EXTERNAL_STORAGE   // Necesario para seleccionar fotos
        };

        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, permissions[2]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private void setupLocationOverlay() {
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        // Centrar mapa en la ubicación del usuario con zoom
        locationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint myLocation = locationOverlay.getMyLocation();
            if (myLocation != null) {
                mapView.getController().animateTo(myLocation);
                mapView.getController().setZoom(18); // Nivel de zoom (mayor = más cercano)
            }
        }));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupLocationOverlay(); // Reintentar si se conceden los permisos
            }
        }
    }

    private void cerrarSesion() {
        // Aquí puedes limpiar cualquier dato de sesión (ej: SharedPreferences)
        // Ejemplo:
        //getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();

        // Redirige a LoginActivity y cierra esta actividad
        Intent intent = new Intent(MapActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Limpia el stack
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume(); // Necesario para osmdroid
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause(); // Necesario para osmdroid
    }
}