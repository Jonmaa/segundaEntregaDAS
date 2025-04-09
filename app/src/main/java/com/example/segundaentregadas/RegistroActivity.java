package com.example.segundaentregadas;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.content.Intent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.segundaentregadas.models.RegistroRequest;
import com.example.segundaentregadas.models.ApiResponse;
import com.example.segundaentregadas.network.ApiClient;
import com.example.segundaentregadas.network.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistroActivity extends AppCompatActivity {
    private EditText etNombre, etEmail, etPassword;
    private TextView tvResult;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        etNombre = findViewById(R.id.etNombre);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvResult = findViewById(R.id.tvResult);
        Button btnRegistrar = findViewById(R.id.btnRegistrar);

        apiService = ApiClient.getClient().create(ApiService.class);

        btnRegistrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nombre = etNombre.getText().toString();
                String email = etEmail.getText().toString();
                String password = etPassword.getText().toString();

                if (nombre.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(RegistroActivity.this, "Rellena todos los campos", Toast.LENGTH_SHORT).show();
                    return;
                }

                RegistroRequest registroRequest = new RegistroRequest(nombre, email, password);
                Call<ApiResponse> call = apiService.registrarUsuario(registroRequest);

                call.enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            ApiResponse apiResponse = response.body();
                            if (apiResponse.isSuccess()) {
                                //tvResult.setText("Registro exitoso");
                                Toast.makeText(RegistroActivity.this, "Registro exitoso", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RegistroActivity.this, LoginActivity.class));
                                finish();
                            } else {
                                tvResult.setText("Error: " + apiResponse.getMessage());
                            }
                            //tvResult.setText(apiResponse.isSuccess() ? "Registro exitoso" : "Error: " + apiResponse.getMessage());
                        } else {
                            tvResult.setText("Error en el servidor");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> call, Throwable t) {
                        tvResult.setText("Error de conexión: " + t.getMessage());
                    }
                });
            }
        });
    }
}