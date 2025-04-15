package com.example.segundaentregadas.providers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.segundaentregadas.MapActivity;
import com.example.segundaentregadas.R;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MarkerWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "MarkerWidgetProvider";
    private static final String ACTION_UPDATE_WIDGET = "com.example.segundaentregadas.ACTION_UPDATE_MARKER_WIDGET";
    private static List<MarkerInfo> marcadores = new ArrayList<>();
    private static int currentMarkerIndex = 0;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called for " + appWidgetIds.length + " widgets");

        if (marcadores.isEmpty()) {
            cargarMarcadores(context, appWidgetManager, appWidgetIds);
        } else {
            actualizarWidget(context, appWidgetManager, appWidgetIds);
        }

        setUpdateAlarm(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_UPDATE_WIDGET.equals(intent.getAction())) {
            Log.d(TAG, "Received update widget action");

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, MarkerWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            if (!marcadores.isEmpty()) {
                currentMarkerIndex = (currentMarkerIndex + 1) % marcadores.size();
                actualizarWidget(context, appWidgetManager, appWidgetIds);
            } else {
                cargarMarcadores(context, appWidgetManager, appWidgetIds);
            }

            setUpdateAlarm(context);
        }
    }

    private void cargarMarcadores(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "Cargando marcadores desde la API");

        // Comprobar si hay una sesión iniciada, si no lo está no se cargan los marcadores
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        int userId = prefs.getInt("user_id", 0);

        if (userId == 0) {
            showErrorMessage(context, appWidgetManager, appWidgetIds, "Inicie sesión para ver marcadores");
            return;
        }

        // Cargar marcadores desde la API
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<JsonArray> call = apiService.obtenerLugares();

        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray lugaresArray = response.body();
                        marcadores.clear();

                        for (int i = 0; i < lugaresArray.size(); i++) {
                            JsonObject lugar = lugaresArray.get(i).getAsJsonObject();

                            String nombre = lugar.get("nombre").getAsString();
                            String descripcion = lugar.get("descripcion").getAsString();
                            double latitud = lugar.get("latitud").getAsDouble();
                            double longitud = lugar.get("longitud").getAsDouble();
                            String imagenUrl = lugar.has("imagen_url") ? lugar.get("imagen_url").getAsString() : "";

                            marcadores.add(new MarkerInfo(nombre, descripcion, latitud, longitud, imagenUrl));
                        }

                        Log.d(TAG, "Marcadores cargados: " + marcadores.size());

                        if (marcadores.isEmpty()) {
                            showNoMarkersMessage(context, appWidgetManager, appWidgetIds);
                        } else {
                            currentMarkerIndex = 0;
                            actualizarWidget(context, appWidgetManager, appWidgetIds);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error al procesar marcadores", e);
                        showErrorMessage(context, appWidgetManager, appWidgetIds, "Error al procesar datos");
                    }
                } else {
                    Log.e(TAG, "Error en respuesta: " + response.code());
                    showErrorMessage(context, appWidgetManager, appWidgetIds, "Error al cargar marcadores");
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                Log.e(TAG, "Error de red", t);
                showErrorMessage(context, appWidgetManager, appWidgetIds, "Error de conexión");
            }
        });
    }

    private void actualizarWidget(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (marcadores.isEmpty()) {
            showNoMarkersMessage(context, appWidgetManager, appWidgetIds);
            return;
        }

        MarkerInfo marker = marcadores.get(currentMarkerIndex);
        Log.d(TAG, "Mostrando marcador " + (currentMarkerIndex + 1) + "/" + marcadores.size() + ": " + marker.getNombre());

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.widget_title, marker.getNombre());
        views.setTextViewText(R.id.widget_description, marker.getDescripcion());
        views.setTextViewText(R.id.widget_location, "Lat: " + marker.getLatitud() + ", Lng: " + marker.getLongitud());

        Intent intent = new Intent(context, MapActivity.class);
        PendingIntent pendingIntent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_description, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_location, pendingIntent);

        // Cargar imagen con Glide en caso de que el marcador tenga una URL de imagen
        if (marker.getImagenUrl() != null && !marker.getImagenUrl().isEmpty()) {
            try {

                AppWidgetTarget appWidgetTarget = new AppWidgetTarget(context, R.id.widget_image, views, appWidgetIds) {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        super.onResourceReady(resource, transition);
                    }
                };

                Glide.with(context.getApplicationContext())
                        .asBitmap()
                        .load(marker.getImagenUrl())
                        .into(appWidgetTarget);
            } catch (Exception e) {
                Log.e(TAG, "Error loading image", e);
                views.setImageViewResource(R.id.widget_image, R.drawable.no_image);
            }
        } else {
            views.setImageViewResource(R.id.widget_image, R.drawable.no_image);
        }

        // Actualizar el widget
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void setUpdateAlarm(Context context) {

        // Configurar un alarm manager para actualizar el widget cada 15 segundos
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MarkerWidgetProvider.class);
        intent.setAction(ACTION_UPDATE_WIDGET);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        alarmManager.cancel(pendingIntent);

        alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 15000, pendingIntent);

        Log.d(TAG, "Update 15 seconds");
    }

    private void showNoMarkersMessage(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.widget_title, "No hay marcadores");
        views.setTextViewText(R.id.widget_description, "Añade marcadores en la app");
        views.setTextViewText(R.id.widget_location, "");
        views.setImageViewResource(R.id.widget_image, R.drawable.no_image);

        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void showErrorMessage(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds, String message) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.widget_title, "Error");
        views.setTextViewText(R.id.widget_description, message);
        views.setTextViewText(R.id.widget_location, "");
        views.setImageViewResource(R.id.widget_image, R.drawable.no_image);

        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MarkerWidgetProvider.class);
        intent.setAction(ACTION_UPDATE_WIDGET);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Widget disabled, alarm cancelled");
    }

    // Clase para almacenar información de marcadores
    private static class MarkerInfo {
        private String nombre;
        private String descripcion;
        private double latitud;
        private double longitud;
        private String imagenUrl;

        public MarkerInfo(String nombre, String descripcion, double latitud, double longitud, String imagenUrl) {
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.latitud = latitud;
            this.longitud = longitud;
            this.imagenUrl = imagenUrl;
        }

        public String getNombre() { return nombre; }
        public String getDescripcion() { return descripcion; }
        public double getLatitud() { return latitud; }
        public double getLongitud() { return longitud; }
        public String getImagenUrl() { return imagenUrl; }
    }
}