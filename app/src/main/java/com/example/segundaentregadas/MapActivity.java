package com.example.segundaentregadas;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.example.segundaentregadas.services.MarkerMonitorService;
import com.example.segundaentregadas.receivers.MarkerUpdateReceiver;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import com.bumptech.glide.Glide;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.segundaentregadas.workers.MarkerCheckWorker;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import com.example.segundaentregadas.models.ApiResponse;
import com.example.segundaentregadas.models.FotoResponse;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import androidx.work.WorkManager;
import androidx.work.PeriodicWorkRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import androidx.work.ExistingPeriodicWorkPolicy;
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
    private BroadcastReceiver markerUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Configuración inicial de osmdroid
        Configuration.getInstance().setUserAgentValue(getPackageName());

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

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

            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            intent.putExtra("nombre", prefs.getString("nombre", ""));
            intent.putExtra("email", prefs.getString("email", ""));

            startActivity(intent);
        });

        requestPermissionsIfNecessary();

        setupLocationOverlay();

        setupMapListeners();

        cargarLugares();

        scheduleMarkerChecker();

        startMarkerMonitorService();

        registerMarkerUpdateReceiver();
    }

    private void cargarLugares() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.obtenerLugares().enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                // Si success carga los marcadores en el mapa
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
        mapView.getOverlays().add(new org.osmdroid.views.overlay.gestures.RotationGestureOverlay(mapView));

        // Detectar toque largo para añadir un nuevo punto de interés
        mapView.getOverlays().add(new org.osmdroid.views.overlay.Overlay() {
            @Override
            public boolean onLongPress(MotionEvent e, MapView mapView) {
                // Convertir coordenadas de pantalla a coordenadas geográficas
                IGeoPoint point = mapView.getProjection().fromPixels((int)e.getX(), (int)e.getY());
                showAddPointOfInterestDialog(new GeoPoint(point.getLatitude(), point.getLongitude()));
                return true;
            }
        });

        // Fuerza el mapa a reconocer el overlay de toque largo
        mapView.invalidate();
    }

    private void showAddPointOfInterestDialog(GeoPoint location) {
        selectedLocation = location;
        selectedImageUri = null;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_poi, null);
        EditText etName = dialogView.findViewById(R.id.etPoiName);
        EditText etDescription = dialogView.findViewById(R.id.etPoiDescription);
        Button btnSelectImage = dialogView.findViewById(R.id.btnSelectImage);
        final ImageView imgPreview = dialogView.findViewById(R.id.imgPreview);

        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_IMAGE_PICK);
        });

        currentDialog = new AlertDialog.Builder(this)
                .setTitle("Añadir punto de interés")
                .setView(dialogView)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        currentDialog.show();

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

            // Mostrar vista previa de la imagen seleccionada
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
            // Convertir URI a archivo
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

            // Comprimir imagen para que no de problemas a la hora de subirla
            File compressedFile = compressImage(tempFile);

            // Subir imagen
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), compressedFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("imagen", compressedFile.getName(), requestFile);

            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            apiService.subirImagenLugar(body).enqueue(new Callback<FotoResponse>() {
                @Override
                public void onResponse(Call<FotoResponse> call, Response<FotoResponse> response) {

                    // Si success, guarda el marcador
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
                }
            });
        } catch (IOException e) {
            Toast.makeText(this, "Error al procesar la imagen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File compressImage(File originalFile) {
        try {
            // Crear bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(originalFile.getAbsolutePath(), bmOptions);

            // Calcular factor de escala
            int photoWidth = bmOptions.outWidth;
            int photoHeight = bmOptions.outHeight;
            int scaleFactor = Math.max(1, Math.min(photoWidth/1200, photoHeight/1200));

            // Decodificar el bitmap con la escala
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;

            Bitmap bitmap = BitmapFactory.decodeFile(originalFile.getAbsolutePath(), bmOptions);

            // Crear archivo comprimido
            File compressedFile = new File(getCacheDir(), "compressed_" + originalFile.getName());
            FileOutputStream fos = new FileOutputStream(compressedFile);

            // Comprimir el bitmap
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
            fos.flush();
            fos.close();

            return compressedFile;
        } catch (Exception e) {
            return originalFile; // En caso de fallo devolver la imagen sin comprimirla
        }
    }

    private void savePoi(String name, String description, GeoPoint location, String imageUrl) {

        // Necesita haber un usuario identificado para poder guardar un marcador
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", 0);

        if (userId == 0) {
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show();
            return;
        }

        // Crear objeto JSON para el lugar
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

                // Si success, añade el marcador al mapa
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(MapActivity.this, "Punto de interés guardado", Toast.LENGTH_SHORT).show();
                    addMarkerToMap(name, description, location, imageUrl);
                } else {
                    Toast.makeText(MapActivity.this, "Error al guardar punto de interés", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Toast.makeText(MapActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addMarkerToMap(String title, String description, GeoPoint location, String imageUrl) {
        Marker marker = new Marker(mapView);
        marker.setPosition(location);
        marker.setTitle(title);
        marker.setSnippet(description);

        // Guardar la URL de la imagen en el objeto relacionado del marcador
        if (imageUrl != null && !imageUrl.isEmpty()) {
            marker.setRelatedObject(imageUrl);
        }

        marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker, MapView mapView) {
                showMarkerOptionsDialog(marker);
                return true;
            }
        });

        try {
            int drawableResource = R.drawable.map_marker_good;
            android.graphics.drawable.Drawable originalIcon = getResources().getDrawable(drawableResource);
            android.graphics.drawable.BitmapDrawable bd = (android.graphics.drawable.BitmapDrawable) originalIcon;
            Bitmap bitmap = bd.getBitmap();

            float scale = 0.1f;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,
                    (int)(bitmap.getWidth() * scale),
                    (int)(bitmap.getHeight() * scale),
                    true);

            android.graphics.drawable.Drawable scaledIcon = new android.graphics.drawable.BitmapDrawable(
                    getResources(), scaledBitmap);

            marker.setIcon(scaledIcon);
        } catch (Exception e) {
            Log.e(TAG, "Error setting marker icon: " + e.getMessage());
        }

        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }

    private void showMarkerOptionsDialog(Marker marker) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_marker_details, null);
        TextView titleText = dialogView.findViewById(R.id.markerTitle);
        TextView descriptionText = dialogView.findViewById(R.id.markerDescription);
        ImageView imageView = dialogView.findViewById(R.id.markerImage);

        titleText.setText(marker.getTitle());
        descriptionText.setText(marker.getSnippet());

        // Obtener la URL de la imagen del objeto relacionado
        String imageUrl = null;
        if (marker.getRelatedObject() instanceof String) {
            imageUrl = (String) marker.getRelatedObject();
        }

        // Cargar la imagen si existe
        if (imageUrl != null && !imageUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.no_image)
                    .error(R.drawable.no_image)
                    .into(imageView);
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.GONE);
        }

        builder.setView(dialogView)
                .setPositiveButton("Cómo llegar", (dialog, which) -> {
                    calculateAndShowRoute(marker.getPosition());
                })
                .setNegativeButton("Cerrar", null);

        builder.show();
    }

    private void calculateAndShowRoute(GeoPoint destination) {
        GeoPoint startPoint = locationOverlay.getMyLocation();
        if (startPoint == null) {
            Toast.makeText(this, "No se puede determinar tu ubicación actual", Toast.LENGTH_SHORT).show();
            return;
        }

        // Eliminar rutas anteriores
        List<Overlay> toRemove = new ArrayList<>();
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof Polyline && !(overlay instanceof MyLocationNewOverlay)) {
                toRemove.add(overlay);
            }
        }
        mapView.getOverlays().removeAll(toRemove);

        Toast.makeText(this, "Calculando ruta...", Toast.LENGTH_SHORT).show();

        // Calcular la ruta
        new Thread(() -> {
            RoadManager roadManager = new OSRMRoadManager(this, android.os.Build.MODEL);
            ((OSRMRoadManager)roadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT); // Se puede poner también en bici o coche

            ArrayList<GeoPoint> waypoints = new ArrayList<>();
            waypoints.add(startPoint);
            waypoints.add(destination);

            try {
                Road road = roadManager.getRoad(waypoints);
                Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                roadOverlay.setColor(getResources().getColor(android.R.color.holo_blue_dark));
                roadOverlay.setWidth(10);

                runOnUiThread(() -> {
                    mapView.getOverlays().add(roadOverlay);
                    mapView.invalidate();

                    double distanceKm = road.mLength;
                    double durationMin = (road.mDuration / 60);

                    String message = String.format("Distancia a pie: %.1f km, Tiempo: %.0f min",
                            distanceKm, durationMin);
                    Toast.makeText(MapActivity.this, message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MapActivity.this,
                            "Error al calcular la ruta: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                    drawDirectLine(startPoint, destination);
                });
            }
        }).start();
    }

    private void drawDirectLine(GeoPoint start, GeoPoint end) {
        Polyline line = new Polyline();
        List<GeoPoint> points = new ArrayList<>();
        points.add(start);
        points.add(end);
        line.setPoints(points);
        line.setColor(getResources().getColor(android.R.color.holo_red_light));
        line.setWidth(5);
        mapView.getOverlays().add(line);
        mapView.invalidate();

        // Calcular distancia directa
        double distanceKm = calculateDistance(start, end);
        Toast.makeText(this, "Distancia directa: " + String.format("%.1f", distanceKm) + " km",
                Toast.LENGTH_LONG).show();
    }

    private double calculateDistance(GeoPoint p1, GeoPoint p2) {

        // Calcular distancia entre dos puntos geográficos usando la fórmula del haversine, el método lo ha hecho Copilot
        double lat1 = p1.getLatitude();
        double lon1 = p1.getLongitude();
        double lat2 = p2.getLatitude();
        double lon2 = p2.getLongitude();

        double R = 6371; // Radio de la Tierra en km
        double dLat = Math.toRadians(lat2 - lat1); // Convertir latitud a radianes
        double dLon = Math.toRadians(lon2 - lon1); // Convertir longitud a radianes
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c; // Distancia en km
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
                mapView.getController().setZoom(18);
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

    private void scheduleMarkerChecker() {
        // Definir restricciones para el trabajo
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Que el worker se ejecute cada 15 minutos
        PeriodicWorkRequest markerCheckRequest =
                new PeriodicWorkRequest.Builder(
                        MarkerCheckWorker.class,
                        15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build();

        // Programar el trabajo
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "marker_check_work",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        markerCheckRequest);
    }

    private void cerrarSesion() {
        SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();

        Glide.get(this).clearMemory();
        new Thread(() -> {
            Glide.get(this).clearDiskCache();
        }).start();

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

    private void startMarkerMonitorService() {
        Intent serviceIntent = new Intent(this, MarkerMonitorService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMarkerUpdateReceiver() {

        // Se pone el suppressLint porque si no sale un error de que no se puede usar el flag pero es de otra versión
        markerUpdateReceiver = new MarkerUpdateReceiver();
        IntentFilter filter = new IntentFilter(MarkerMonitorService.ACTION_MARKER_UPDATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(markerUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(markerUpdateReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        if (markerUpdateReceiver != null) {
            unregisterReceiver(markerUpdateReceiver);
        }

        super.onDestroy();
    }
}