package com.netstress.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final int SHIZUKU_CODE = 1001;
    private static final int NOTIF_CODE = 1002;
    
    private Button btnShizuku, btnStart, btnStop;
    private EditText etTargetIp;
    private TextView tvStatus;
    private boolean hasShizukuPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Minta izin notifikasi (Wajib buat Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_CODE);
            }
        }

        btnShizuku = findViewById(R.id.btnShizuku);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        etTargetIp = findViewById(R.id.etTargetIp);
        tvStatus = findViewById(R.id.tvStatus);

        btnShizuku.setOnClickListener(v -> {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Shizuku mati! Buka app Shizuku dulu.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_CODE);
            } else {
                activateFeature();
            }
        });

        Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
            if (requestCode == SHIZUKU_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
                activateFeature();
            }
        });

        btnStart.setOnClickListener(v -> {
            String ip = etTargetIp.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Masukkan IP target!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasShizukuPermission) {
                Toast.makeText(this, "Klik 'Aktifkan Fitur' dulu!", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, StressService.class);
            intent.putExtra("TARGET_IP", ip);
            
            // Pake ContextCompat biar aman di semua versi Android
            ContextCompat.startForegroundService(this, intent);
            
            tvStatus.setText("Status: STRIKING");
            tvStatus.setTextColor(0xFF00FFCC);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, StressService.class));
            tvStatus.setText("Status: READY");
            tvStatus.setTextColor(0xFF33FF33);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        });
    }

    private void activateFeature() {
        hasShizukuPermission = true;
        btnShizuku.setText("FITUR AKTIF ✓");
        btnShizuku.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF33FF33));
        Toast.makeText(this, "Akses Shizuku Diberikan!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == SHIZUKU_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                activateFeature();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
