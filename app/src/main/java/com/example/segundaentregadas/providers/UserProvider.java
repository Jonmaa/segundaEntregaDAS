package com.example.segundaentregadas.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.segundaentregadas.models.ApiResponse;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import com.google.gson.JsonObject;

import java.util.concurrent.CountDownLatch;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserProvider extends ContentProvider {
    private static final String TAG = "UserProvider";
    private static final String AUTHORITY = "com.example.segundaentregadas.userprovider";
    private static final String USERS_TABLE = "users";
    private static final int USERS = 1;
    private static final int USER_ID = 2;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sUriMatcher.addURI(AUTHORITY, USERS_TABLE, USERS);
        sUriMatcher.addURI(AUTHORITY, USERS_TABLE + "/#", USER_ID);
    }

    private DatabaseHelper dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;

        switch (sUriMatcher.match(uri)) {
            case USERS:
                cursor = db.query(USERS_TABLE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case USER_ID:
                selection = "_id = ?";
                selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };
                cursor = db.query(USERS_TABLE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case USERS:
                return "vnd.android.cursor.dir/" + AUTHORITY + "." + USERS_TABLE;
            case USER_ID:
                return "vnd.android.cursor.item/" + AUTHORITY + "." + USERS_TABLE;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (sUriMatcher.match(uri) != USERS) {
            throw new IllegalArgumentException("Invalid URI for insert");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = db.insert(USERS_TABLE, null, values);

        if (id > 0) {
            Uri returnUri = ContentUris.withAppendedId(Uri.parse("content://" + AUTHORITY + "/" + USERS_TABLE), id);
            getContext().getContentResolver().notifyChange(returnUri, null);
            return returnUri;
        }

        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;

        switch (sUriMatcher.match(uri)) {
            case USERS:
                count = db.delete(USERS_TABLE, selection, selectionArgs);
                break;
            case USER_ID:
                selection = "_id = ?";
                selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };
                count = db.delete(USERS_TABLE, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;

        switch (sUriMatcher.match(uri)) {
            case USERS:
                count = db.update(USERS_TABLE, values, selection, selectionArgs);
                break;
            case USER_ID:
                selection = "_id = ?";
                selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };
                count = db.update(USERS_TABLE, values, selection, selectionArgs);

                // Sync with server if we're updating a user
                if (count > 0 && values.containsKey("nombre")) {
                    syncUserWithServer(values);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    private void syncUserWithServer(ContentValues values) {
        int userId = values.getAsInteger("_id");
        String nombre = values.getAsString("nombre");

        JsonObject userData = new JsonObject();
        userData.addProperty("user_id", userId);
        userData.addProperty("nombre", nombre);

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<ApiResponse> call = apiService.actualizarUsuario(userData);

        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Sync successful: " + nombre);
                } else {
                    Log.e(TAG, "Sync failed: " + (response.body() != null ? response.body().getMessage() : "Unknown error"));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Log.e(TAG, "Network error during sync", t);
            }
        });
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "users.db";
        private static final int DATABASE_VERSION = 1;

        private static final String CREATE_USERS_TABLE =
                "CREATE TABLE " + USERS_TABLE + " (" +
                        "_id INTEGER PRIMARY KEY," +
                        "nombre TEXT," +
                        "email TEXT," +
                        "foto_url TEXT)";

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_USERS_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + USERS_TABLE);
            onCreate(db);
        }
    }
}
