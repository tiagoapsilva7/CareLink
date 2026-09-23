package com.example.carelink;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.carelink.R;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("PULSE_TRACKR_ALARM", "Alarm triggered successfully!");

        String drugName = intent.getStringExtra("drugName");
        String posology = intent.getStringExtra("posology");
        String appointmentId = intent.getStringExtra("appointmentId");

        // 1. Create the Intent that opens the Activity
        Intent tapIntent = new Intent(context, CreateRemindersActivity.class);
        tapIntent.putExtra("drugName", drugName);
        tapIntent.putExtra("posology", posology);
        tapIntent.putExtra("appointmentId", appointmentId);

        // Ensure it opens cleanly as a fresh task if the app is in the background
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 2. Wrap it in a PendingIntent
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(), // Unique request code
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "medication_reminders_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Medication Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.carelink_logo)
                .setContentTitle("Medication Reminder")
                .setContentText("It's time to take: " + drugName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent); // 3. Attach it to the notification!

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}