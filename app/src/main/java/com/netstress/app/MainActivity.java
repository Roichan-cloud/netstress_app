package com.netstress.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final int SHIZUKU_CODE = 1001;
    private static final int NOTIF_CODE = 1002;
    
    private Button btnShizuku, btnStart, btnStop, btnScan;
    private EditText etTargetIp;
    private TextView tvStatus;
    private Spinner spinnerIps, spinnerMode;
    private boolean hasShizukuPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_CODE);
            }
        }

        btnShizuku = findViewById(R.id.btnShizuku);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnScan = findViewById(R.id.btnScan);
        etTargetIp = findViewById(R.id.etTargetIp);
        tvStatus = findViewById(R.id.tvStatus);
        spinnerIps = findViewById(R.id.spinnerIps);
        spinnerMode = findViewById(R.id.spinnerMode);

        // --- SETUP SPINNER MODE SERANGAN ---
        String[] modes = {
            "1. Santai (Aman)",
            "2. Sedang (Ping naik)",
            "3. Ganas (Lag parah)",
            "4. Brutal (Koneksi putus-nyambung)",
            "5. Nuclear (Tumbang total)"
        };
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);
        spinnerMode.setSelection(2); // Default ke mode "Ganas"

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

        btnScan.setOnClickListener(v -> {
            if (!hasShizukuPermission) {
                Toast.makeText(this, "Aktifkan Shizuku dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            
            tvStatus.setText("SCANNING NETWORK...");
            tvStatus.setTextColor(0xFFFFFF00);
            btnScan.setEnabled(false);

            new Thread(() -> {
                List<String> ips = scanNetwork();
                
                runOnUiThread(() -> {
                    btnScan.setEnabled(true);
                    tvStatus.setText("SYSTEM READY");
                    tvStatus.setTextColor(0xFF00FFCC);

                    if (ips.isEmpty()) {
                        Toast.makeText(this, "Tidak ada perangkat ditemukan.", Toast.LENGTH_SHORT).show();
                    } else {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ips);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerIps.setAdapter(adapter);
                    }
                });
            }).start();
        });

        btnStart.setOnClickListener(v -> {
            String ip = "";
            if (spinnerIps.getSelectedItem() != null) {
                String selected = spinnerIps.getSelectedItem().toString();
                ip = selected.split(" ")[0]; 
            } else {
                ip = etTargetIp.getText().toString().trim();
            }

            if (ip.isEmpty()) {
                Toast.makeText(this, "Pilih atau masukkan IP target!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasShizukuPermission) {
                Toast.makeText(this, "Klik 'Aktifkan Fitur' dulu!", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- AMBIL MODE SERANGAN ---
            int modePosition = spinnerMode.getSelectedItemPosition();
            int workers = 10000, payload = 1024; // Default mode 3
            
            if (modePosition == 0) { workers = 1000; payload = 128; }       // Santai
            else if (modePosition == 1) { workers = 5000; payload = 512; }   // Sedang
            else if (modePosition == 2) { workers = 10000; payload = 1024; } // Ganas
            else if (modePosition == 3) { workers = 20000; payload = 1472; } // Brutal
            else if (modePosition == 4) { workers = 50000; payload = 1472; } // Nuclear

            Intent intent = new Intent(this, StressService.class);
            intent.putExtra("TARGET_IP", ip);
            intent.putExtra("WORKERS", workers);
            intent.putExtra("PAYLOAD", payload);
            
            ContextCompat.startForegroundService(this, intent);
            
            tvStatus.setText("STRIKING " + ip);
            tvStatus.setTextColor(0xFF00FFCC);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, StressService.class));
            tvStatus.setText("SYSTEM READY");
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

private List<String> scanNetwork() {
    List<String> ipList = new ArrayList<>();
    try {
        // Ganti Shizuku.newProcess pakai Runtime.exec
        Process process = Runtime.getRuntime().exec("ip neigh");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        
        Pattern pattern = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}).*?([0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2})");
        
        while ((line = reader.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String ip = matcher.group(1);
                String macPrefix = matcher.group(2).toLowerCase();
                
                if (isValidIp(ip)) {
                    String vendor = getVendorFromMac(macPrefix);
                    String displayText = ip;
                    if (!vendor.equals("Unknown")) {
                        displayText += "  [" + vendor + "]";
                    }
                    
                    boolean exists = false;
                    for(String s : ipList) { if(s.startsWith(ip)) exists = true; }
                    if(!exists) ipList.add(displayText);
                }
            }
        }
        process.waitFor();
    } catch (Exception e) {
        e.printStackTrace();
    }
    return ipList;
}

    private String getVendorFromMac(String macPrefix) {
        switch (macPrefix) {
            case "3c:5a:": case "64:cc:": case "50:8f:": case "28:6c:": case "78:11:": return "Xiaomi/Redmi";
            case "a4:50:": case "8c:77:": case "34:23:": case "5c:0a:": return "Samsung";
            case "f0:18:": case "3c:2e:": case "9c:35:": return "Apple/iPhone";
            case "00:1a:": case "2c:54:": case "00:9a:": case "00:0c:": return "Huawei";
            case "d4:6a:": case "c0:ee:": case "10:7b:": case "2c:8a:": return "Oppo";
            case "00:ae:": case "00:9e:": case "d0:65:": return "Vivo";
            case "00:1e:": case "00:26:": case "b0:be:": return "Asus";
            case "d8:3a:": case "c0:3f:": case "e0:2c:": return "TP-Link";
            case "00:e0:": case "00:11:": return "Router/Gateway";
            default: return "Unknown";
        }
    }

    private boolean isValidIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            int num = Integer.parseInt(part);
            if (num < 0 || num > 255) return false;
        }
        return true;
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
