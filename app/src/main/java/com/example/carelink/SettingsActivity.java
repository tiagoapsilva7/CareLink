package com.example.carelink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Activity for managing user application settings.
 * Handles sound muting/unmuting and dynamic dark/light mode switching.
 */
public class SettingsActivity extends AppCompatActivity {

    private Switch soundToggle, themeToggle;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Configure status bar and navigation bar visual appearance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.bg_color));
            // Explicitly force the bottom system navigation bar to stay black
            getWindow().setNavigationBarColor(Color.BLACK);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // Initialize Shared Preferences for persistent storage of user settings
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Bind UI components
        soundToggle = findViewById(R.id.sound_toggle);
        themeToggle = findViewById(R.id.theme_toggle);

        // Handle hardware back press to return to the Main Activity's home fragment
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                intent.putExtra("targetFragment", "HomeFragment");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });

        // Load persisted settings into the UI
        loadSettings();

        // Sound listener: toggles system music stream volume
        soundToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("sound_enabled", isChecked);
            editor.apply();

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            if (isChecked) {
                // Restore volume from the last saved state or default to max
                int previousVolume = sharedPreferences.getInt("previous_volume", audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0);
                Toast.makeText(SettingsActivity.this, "Sound Enabled", Toast.LENGTH_SHORT).show();
            } else {
                // Mute audio and save current volume for future restoration
                SharedPreferences.Editor editorVolume = sharedPreferences.edit();
                int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                editorVolume.putInt("previous_volume", currentVolume);
                editorVolume.apply();

                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                Toast.makeText(SettingsActivity.this, "Sound Disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Theme listener: updates UI theme dynamically
        themeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("dark_mode_enabled", isChecked);
            editor.apply();

            // Set the theme mode specifically for this activity instance
            if (isChecked) {
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                Toast.makeText(SettingsActivity.this, "Dark Mode Enabled", Toast.LENGTH_SHORT).show();
            } else {
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                Toast.makeText(SettingsActivity.this, "Day Mode Enabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Reads saved settings from Shared Preferences and updates UI and activity delegate state.
     */
    private void loadSettings() {
        boolean soundEnabled = sharedPreferences.getBoolean("sound_enabled", true);
        boolean darkModeEnabled = sharedPreferences.getBoolean("dark_mode_enabled", false);

        soundToggle.setChecked(soundEnabled);
        themeToggle.setChecked(darkModeEnabled);

        // Apply the saved theme preference to the activity delegate
        if (darkModeEnabled) {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}