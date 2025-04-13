package com.example.segundaentregadas.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.segundaentregadas.MapActivity;
import com.example.segundaentregadas.R;
import com.example.segundaentregadas.services.MarkerMonitorService;

public class MarkerUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "MarkerUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (MarkerMonitorService.ACTION_MARKER_UPDATE.equals(intent.getAction())) {
            int newMarkersCount = intent.getIntExtra(MarkerMonitorService.EXTRA_NEW_MARKERS_COUNT, 0);

            Log.d(TAG, "Received broadcast: " + newMarkersCount + " new markers");

            if (newMarkersCount > 0) {

                // Update the app widget if applicable
                Intent updateWidgetIntent = new Intent("com.example.segundaentregadas.ACTION_UPDATE_MARKER_WIDGET");
                updateWidgetIntent.putExtra("new_markers", newMarkersCount);
                context.sendBroadcast(updateWidgetIntent);
            }
        }
    }
}