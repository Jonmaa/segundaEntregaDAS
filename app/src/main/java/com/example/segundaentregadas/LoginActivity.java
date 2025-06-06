package com.example.segundaentregadas;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.segundaentregadas.models.ApiResponse;
import com.example.segundaentregadas.models.LoginRequest;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private TextView tvResult;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvResult = findViewById(R.id.tvResult);
        Button btnLogin = findViewById(R.id.btnLogin);

        apiService = ApiClient.getClient().create(ApiService.class);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etEmail.getText().toString();
                String password = etPassword.getText().toString();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Rellena todos los campos", Toast.LENGTH_SHORT).show();
                    return;
                }

                LoginRequest loginRequest = new LoginRequest(email, password);
                Call<ApiResponse> call = apiService.login(loginRequest);

                call.enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {

                        // Si la respuesta es success obtener los datos para guardar en preferencias
                        if (response.isSuccessful() && response.body() != null) {
                            ApiResponse loginResponse = response.body();
                            if (loginResponse.isSuccess()) {
                                int userId = loginResponse.getUser().getId();
                                String nombre = loginResponse.getUser().getNombre();
                                String email = loginResponse.getUser().getEmail();
                                String fotoUrl = loginResponse.getUser().getFotoUrl();

                                SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
                                editor.putInt("user_id", userId);
                                editor.putString("nombre", nombre);
                                editor.putString("email", email);
                                editor.putString("foto_url", fotoUrl);
                                editor.apply();

                                // Guardar los datos en el ContentProvider para luego poder editar el nombre
                                ContentValues values = new ContentValues();
                                values.put("_id", userId);
                                values.put("nombre", nombre);
                                values.put("email", email);
                                values.put("foto_url", fotoUrl);
                                getContentResolver().insert(Uri.parse("content://com.example.segundaentregadas.userprovider/users"), values);

                                startActivity(new Intent(LoginActivity.this, MapActivity.class));
                                finish();
                            } else {
                                tvResult.setText("Error: " + loginResponse.getMessage());
                            }
                        } else {
                            tvResult.setText("Error en la respuesta del servidor");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> call, Throwable t) {
                        tvResult.setText("Error de conexión: " + t.getMessage());
                    }
                });
            }
        });

        Button btnIrARegistro = findViewById(R.id.btnIrARegistro);
        btnIrARegistro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegistroActivity.class));
            }
        });
    }

}