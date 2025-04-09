package com.example.segundaentregadas;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class MapActivity extends AppCompatActivity {
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private Button btnLogout;

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

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
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cerrarSesion();
            }
        });

        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(MapActivity.this)
                    .setTitle("Cerrar Sesión")
                    .setMessage("¿Estás seguro?")
                    .setPositiveButton("Sí", (dialog, which) -> cerrarSesion())
                    .setNegativeButton("No", null)
                    .show();
        });

        // Solicitar permisos si no están concedidos
        requestPermissionsIfNecessary();

        // Configurar la ubicación del usuario
        setupLocationOverlay();
    }

    private void requestPermissionsIfNecessary() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE // Necesario para caché de mapas
        };

        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED) {
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