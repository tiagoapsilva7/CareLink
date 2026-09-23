package com.example.carelink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.carelink.fragments.ChatFragment;
import com.example.carelink.fragments.HomeFragment;
import com.example.carelink.fragments.ProfileFragment;
import com.example.carelink.util.FirebaseUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.Timestamp;

/**
 * Acts as the primary container for the application's core screens.
 * Manages the BottomNavigationView, handles deep-linking to specific fragments via Intents,
 * tracks user online status, and handles Android 13+ push notification permissions.
 */
public class MainActivity extends AppCompatActivity {

    HomeFragment           homeFragment;
    ProfileFragment        profileFragment;
    ChatFragment           chatFragment;
    BottomNavigationView   bottomNavigationView;

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ---------------------------------------------------------------
        // BACKDOOR GUARD: Uses the SharedPreferences-backed flag so it survives
        // process death (e.g. screen turned off or app backgrounded).
        // A static boolean would reset to false after a process kill and falsely block entry.
        // If the session is not unlocked, redirect immediately to the Login/Biometric screen.
        // ---------------------------------------------------------------
        if (!LoginActivity.isSessionUnlocked(this)) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Configure the system status bar to blend with the app's light background
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        // Trigger notification permission flow for devices running Android 13 (Tiramisu) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkNotificationPermission();
        }

        // Update the user's presence timestamp in Firestore to track their last active moment
        if (FirebaseUtil.currentUserId() != null) {
            FirebaseUtil.currentUserDetails().update("lastOnline", Timestamp.now());
        }

        // Initialize the fragments used in the bottom navigation
        homeFragment    = new HomeFragment();
        profileFragment = new ProfileFragment();
        chatFragment    = new ChatFragment();

        // Setup the bottom navigation view and define its active indicator styling
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setItemActiveIndicatorColor(
                ContextCompat.getColorStateList(this, R.color.bg_color));

        // ---------------------------------------------------------------
        // Intent Routing: Allows other activities or push notifications to
        // "deep-link" directly into a specific tab upon launching MainActivity.
        // ---------------------------------------------------------------
        String fragment = getIntent().getStringExtra("goToFragment");
        if ("home".equals(fragment)) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }

        String targetFragment = getIntent().getStringExtra("targetFragment");
        if (targetFragment != null && targetFragment.equalsIgnoreCase("ChatFragment")) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, chatFragment).commit();
            bottomNavigationView.setSelectedItemId(R.id.menu_chat);
        } else if (targetFragment != null && targetFragment.equalsIgnoreCase("ProfileFragment")) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, profileFragment).commit();
            bottomNavigationView.setSelectedItemId(R.id.menu_profile);
        } else {
            // Default behavior: load the Home tab
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, homeFragment).commit();
            bottomNavigationView.setSelectedItemId(R.id.menu_home);
        }

        // ---------------------------------------------------------------
        // Bottom Navigation Listener: Swaps out the fragments in the
        // fragment_container whenever a user taps a different menu item.
        // ---------------------------------------------------------------
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.menu_home) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, homeFragment).commit();
                }
                if (item.getItemId() == R.id.menu_profile) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, profileFragment).commit();
                }
                if (item.getItemId() == R.id.menu_chat) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, chatFragment).commit();
                }
                return true;
            }
        });
    }

    /**
     * Checks if the POST_NOTIFICATIONS permission has been granted.
     * Required for FCM Push Notifications on Android 13+.
     */
    private void checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission();
        }
    }

    /**
     * Triggers the native Android permission request dialog for notifications.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    /**
     * Handles the user's response to the notification permission prompt.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission for notifications granted", Toast.LENGTH_SHORT).show();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Check if the system is preventing the prompt from showing again (permanent denial)
                    boolean shouldShowRationale =
                            shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS);
                    if (!shouldShowRationale) {
                        // User selected "Don't ask again", so we provide a manual routing to Settings
                        showSettingsDialog();
                    } else {
                        Toast.makeText(this, "Permission for notifications denied.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    /**
     * Displays a dialog advising the user that notifications are permanently blocked,
     * offering a direct shortcut to the application's OS-level settings page to enable them.
     */
    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications Blocked")
                .setMessage("Notifications have been permanently blocked. To receive important " +
                        "updates and chat messages, please go to Settings and allow notifications.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    // Route directly to the specific App Settings page for this application
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}