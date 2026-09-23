package com.example.carelink;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Random;

/**
 * Receives Firebase Cloud Messaging pushes while the app is running and turns
 * them into system notifications.
 *
 * Messages are sent as FCM *data* payloads rather than notification payloads,
 * so this service is invoked for both foreground and background deliveries and
 * is solely responsible for building the visible notification. The payload keys
 * mirror those written in ChatActivity.getNotificationBody: title, body, type,
 * userId, username and fcmToken.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        if (data.size() > 0) {
            String title = data.get("title");
            String body = data.get("body");
            showNotification(title, body, data);
        }
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        String channelId = "chat_notifications_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "CareLink Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for messages, appointments, and symptoms");
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent;
        String type = data.get("type");
        String id = data.get("id");

        if ("appointment".equals(type) && id != null) {
            intent = new Intent(this, MyVisitReportActivity.class);
            intent.putExtra("appointmentId", id);
            intent.putExtra("fromNotification", true); // FIXED: Explicit flag
        } else if ("symptom".equals(type) && id != null) {
            intent = new Intent(this, MySymptomActivity.class);
            intent.putExtra("symptomId", id);
            intent.putExtra("fromNotification", true); // FIXED: Explicit flag
        } else {
            intent = new Intent(this, MainActivity.class);
            intent.putExtra("targetFragment", "ChatFragment");
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                new Random().nextInt(),
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.carelink_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        notificationManager.notify(new Random().nextInt(), builder.build());
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
    }
}