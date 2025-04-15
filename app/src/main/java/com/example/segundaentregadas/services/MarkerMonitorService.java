package com.example.segundaentregadas.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;


import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.segundaentregadas.MapActivity;
import com.example.segundaentregadas.R;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import com.example.segundaentregadas.receivers.MarkerUpdateReceiver;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Response;

public class MarkerMonitorService extends Service {
    private static final String TAG = "MarkerMonitorService";
    private static final String CHANNEL_ID = "marker_monitor_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final String PREF_NAME = "marker_prefs";
    private static final String PREF_MARKER_IDS = "marker_ids";

    public static final String ACTION_MARKER_UPDATE = "com.example.segundaentregadas.ACTION_MARKER_UPDATE";
    public static final String EXTRA_NEW_MARKERS_COUNT = "new_markers_count";

    private ScheduledExecutorService scheduler;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "MarkerMonitorService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;

            // Empezar el servicio en primer plano
            startForeground(NOTIFICATION_ID, createNotification("Monitorizando marcadores", "Buscando nuevos marcadores..."));

            // Fijar chequeos cada 5 minutos
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::checkForNewMarkers, 0, 5, TimeUnit.MINUTES);

            Log.d(TAG, "MarkerMonitorService started in foreground mode");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        Log.d(TAG, "MarkerMonitorService destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkForNewMarkers() {
        Log.d(TAG, "Checking for new markers");
        try {
            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            Call<JsonArray> call = apiService.obtenerLugares();
            Response<JsonArray> response = call.execute();

            // Comprobar cuantos marcadores nuevos hay
            if (response.isSuccessful() && response.body() != null) {
                JsonArray markers = response.body();

                // Obtener marcadores actuales
                Set<String> currentMarkerIds = new HashSet<>();
                for (JsonElement element : markers) {
                    if (element.isJsonObject()) {
                        JsonObject marker = element.getAsJsonObject();
                        if (marker.has("id")) {
                            currentMarkerIds.add(marker.get("id").getAsString());
                        }
                    }
                }

                // Obtener marcadores guardados
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                Set<String> savedMarkerIds = prefs.getStringSet(PREF_MARKER_IDS, new HashSet<>());

                // Restas los marcadores actuales a los guardados
                Set<String> newMarkerIds = new HashSet<>(currentMarkerIds);
                newMarkerIds.removeAll(savedMarkerIds);
                int newMarkersCount = newMarkerIds.size();

                // Guardar los marcadores actuales
                prefs.edit().putStringSet(PREF_MARKER_IDS, currentMarkerIds).apply();

                // Mandar notificación
                if (newMarkersCount > 0) {
                    String message = newMarkersCount == 1
                            ? "1 nuevo marcador encontrado"
                            : newMarkersCount + " nuevos marcadores encontrados";

                    // Actualizar la notificación con el nuevo mensaje
                    startForeground(NOTIFICATION_ID, createNotification("Nuevos puntos de interés!", message));

                    // Enviar un broadcast para actualizar la UI
                    Intent updateIntent = new Intent(this, MarkerUpdateReceiver.class);
                    updateIntent.setAction(ACTION_MARKER_UPDATE);
                    updateIntent.putExtra(EXTRA_NEW_MARKERS_COUNT, newMarkersCount);
                    sendBroadcast(updateIntent);

                    Log.d(TAG, message);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for new markers", e);
        }
    }

    private Notification createNotification(String title, String content) {
        Intent intent = new Intent(this, MapActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.icon);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.map_marker_good)
                .setLargeIcon(largeIcon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Canal de Monitoreo de Marcadores",
                    NotificationManager.IMPORTANCE_LOW);

            channel.setDescription("Usado para el servicio de monitoreo de marcadores");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}