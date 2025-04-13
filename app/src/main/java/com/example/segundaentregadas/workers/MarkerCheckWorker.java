package com.example.segundaentregadas.workers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.segundaentregadas.MapActivity;
import com.example.segundaentregadas.R;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Response;

public class MarkerCheckWorker extends Worker {
    private static final String TAG = "MarkerCheckWorker";
    private static final String CHANNEL_ID = "marker_check_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREF_NAME = "marker_check_prefs";
    private static final String PREF_MARKER_IDS = "marker_ids";

    public MarkerCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        Log.d(TAG, "MarkerCheckWorker executing");

        // Check for new markers and show notification if needed
        checkForNewMarkers();

        return Result.success();
    }

    private void checkForNewMarkers() {
        Context context = getApplicationContext();
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        try {
            // Get stored marker IDs from previous check
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            Set<String> savedMarkerIds = prefs.getStringSet(PREF_MARKER_IDS, new HashSet<>());

            // Make a synchronous API call
            Call<JsonArray> call = apiService.obtenerLugares();
            Response<JsonArray> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                JsonArray markers = response.body();
                Set<String> currentMarkerIds = new HashSet<>();
                Set<String> newMarkerIds = new HashSet<>();

                // Extract marker IDs from response
                for (JsonElement element : markers) {
                    if (element.isJsonObject()) {
                        JsonObject marker = element.getAsJsonObject();
                        if (marker.has("id")) {
                            String markerId = marker.get("id").getAsString();
                            currentMarkerIds.add(markerId);

                            // Check if this is a new marker
                            if (!savedMarkerIds.contains(markerId)) {
                                newMarkerIds.add(markerId);
                            }
                        }
                    }
                }

                int newMarkerCount = newMarkerIds.size();
                Log.d(TAG, "Found " + currentMarkerIds.size() + " total markers, " + newMarkerCount + " new");

                // Save current marker IDs for next comparison
                prefs.edit().putStringSet(PREF_MARKER_IDS, currentMarkerIds).apply();

                // Show notification only if there are new markers
                if (newMarkerCount > 0) {
                    showNewMarkersNotification(newMarkerCount);
                }
            } else {
                Log.e(TAG, "API call failed: " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking markers: " + e.getMessage(), e);
        }
    }

    private void showNewMarkersNotification(int newMarkerCount) {
        Context context = getApplicationContext();

        // Create notification channel
        createNotificationChannel();

        // Create intent to open the app when notification is tapped
        Intent intent = new Intent(context, MapActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Format message based on count
        String title = "Nuevos marcadores";
        String message = newMarkerCount == 1
                ? "Se ha añadido 1 marcador nuevo"
                : "Se han añadido " + newMarkerCount + " marcadores nuevos";

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.map_marker_good)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Show notification
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());

        Log.d(TAG, "Notification shown: " + message);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Marker Check Channel";
            String description = "Channel for marker check notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager =
                    getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}