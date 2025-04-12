package com.example.segundaentregadas.workers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Response;

public class MarkerCheckWorker extends Worker {
    private static final String TAG = "MarkerCheckWorker";
    private static final String CHANNEL_ID = "marker_check_channel";
    private static final int NOTIFICATION_ID = 1001;

    public MarkerCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        Log.d(TAG, "MarkerCheckWorker executing at: " + new Date().toString());

        // Always show notification for testing purposes
        showDebugNotification();

        // Also check for markers
        checkForMarkers();

        return Result.success();
    }

    private void showDebugNotification() {
        Context context = getApplicationContext();

        // Create a timestamp for the notification
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        // Create notification
        String title = "Comprobación de marcadores";
        String message = "Verificando nuevos marcadores a las " + timestamp;

        // Use a random ID to ensure multiple notifications show up
        int notificationId = new Random().nextInt(10000);

        showNotification(title, message, notificationId);
    }

    private void checkForMarkers() {
        Context context = getApplicationContext();
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        try {
            // Make a synchronous API call
            Call<JsonArray> call = apiService.obtenerLugares();
            Response<JsonArray> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                JsonArray markers = response.body();
                int count = markers.size();

                Log.d(TAG, "Found " + count + " markers in total");

                // For testing, show another notification with the count
                showNotification(
                        "Marcadores encontrados",
                        "Se encontraron " + count + " marcadores en total",
                        NOTIFICATION_ID + 1
                );
            } else {
                Log.e(TAG, "API call failed: " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking markers: " + e.getMessage(), e);
        }
    }

    private void showNotification(String title, String message, int notificationId) {
        Context context = getApplicationContext();

        // Create notification channel for Android 8.0+
        createNotificationChannel();

        // Create an Intent to open the MapActivity when notification is tapped
        Intent intent = new Intent(context, MapActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.map_marker_good)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Show the notification
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, builder.build());

        Log.d(TAG, "Notification shown: " + title + " - " + message);
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