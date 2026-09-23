package com.example.carelink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.carelink.util.FirebaseUtil;
import com.example.carelink.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    // ---------------------------------------------------------------
    // Session persistence: survives process death (screen-off kills).
    // Cleared only on explicit sign-out so screen-off/on never
    // triggers a redundant biometric prompt.
    // ---------------------------------------------------------------
    public static final String PREFS_NAME   = "PulseTrackrSession";
    public static final String KEY_UNLOCKED = "isUnlocked";

    /**
     * Returns true if the user already completed biometric/credential auth this session.
     * Uses SharedPreferences to check if the user is verified.
     */
    public static boolean isSessionUnlocked(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_UNLOCKED, false);
    }

    /**
     * Marks the session as authenticated (persists across process death).
     * Called upon successful biometric unlock.
     */
    private void setSessionUnlocked(boolean unlocked) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UNLOCKED, unlocked)
                .apply();
    }
    // ---------------------------------------------------------------

    // UI Element Declarations
    Button   login_button;
    EditText etEmail, etPassword;
    TextView start_register;

    // Initialize Firebase Authentication instance
    public FirebaseAuth auth = FirebaseAuth.getInstance();

    // State flags to prevent infinite biometric loops if the user cancels or the app pauses
    private boolean isPromptActive    = false;
    private boolean hasCanceledPrompt = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (!isTaskRoot()
                && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)
                && getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_MAIN)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // UI Styling: Set the status bar to a light color scheme matching the app background
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setStatusBarColor(getResources().getColor(R.color.bg_color));

        // Bind UI components to their XML views
        login_button   = findViewById(R.id.login_button);
        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        start_register = findViewById(R.id.start_register);

        // Disable the login button initially until the user enters credentials
        login_button.setEnabled(false);

        // ---------------------------------------------------------------
        // Input Validation: Listens to keystrokes in the Email and Password fields.
        // Dynamically enables the login button only when both fields have text.
        // ---------------------------------------------------------------
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isEnabled = !etEmail.getText().toString().trim().isEmpty() &&
                        !etPassword.getText().toString().trim().isEmpty();
                login_button.setEnabled(isEnabled);
                // Visually dim the button if it is disabled
                login_button.setAlpha(isEnabled ? 1.0f : 0.5f);
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        // Attach the watcher to both text fields
        etEmail.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);

        // ---------------------------------------------------------------
        // UX Animation: Smoothly bumps the layout up by 60 pixels when the
        // user clicks a text field so the virtual keyboard doesn't hide the inputs.
        // ---------------------------------------------------------------
        View rootLayout = findViewById(R.id.infoTextView);
        View.OnFocusChangeListener focusBump = (v, hasFocus) -> {
            if (hasFocus) {
                // Move layout up
                rootLayout.animate().translationY(-60f).setDuration(50).start();
            } else {
                // Move layout back down if neither field has focus
                rootLayout.postDelayed(() -> {
                    if (!etEmail.hasFocus() && !etPassword.hasFocus()) {
                        rootLayout.animate().translationY(0f).setDuration(200).start();
                    }
                }, 100);
            }
        };
        etEmail.setOnFocusChangeListener(focusBump);
        etPassword.setOnFocusChangeListener(focusBump);

        // Handle Login button click
        login_button.setOnClickListener(v -> {
            String txt_email    = etEmail.getText().toString().trim();
            String txt_password = etPassword.getText().toString().trim();
            // Trigger Firebase authentication
            loginUser(txt_email, txt_password);
        });

        // Handle routing to the Registration screen
        start_register.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish(); // Close LoginActivity so user doesn't return here on 'Back' press
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if the user is already logged in via Firebase
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser != null) {
            // If logged in AND has already passed biometrics this session, bypass login
            if (isSessionUnlocked(this)) {
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }
            // If logged in BUT hasn't passed biometrics, trigger the biometric prompt
            else if (!isPromptActive && !hasCanceledPrompt) {
                showBiometricPrompt();
            }
        }
    }

    /**
     * Authenticates the user with Firebase using Email and Password.
     */
    private void loginUser(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                // On success, do NOT immediately log them in. Force biometric verification first.
                .addOnSuccessListener(this, authResult -> showBiometricPrompt())

                // On failure, intercept the specific Firebase error and show a user-friendly message.
                .addOnFailureListener(this, e -> {
                    String errorMessage = "Login failed. Please try again.";
                    if (e instanceof FirebaseAuthException) {
                        String errorCode = ((FirebaseAuthException) e).getErrorCode();
                        switch (errorCode) {
                            case "ERROR_USER_NOT_FOUND":
                                errorMessage = "Email not registered. Please sign up.";
                                break;
                            case "ERROR_WRONG_PASSWORD":
                                errorMessage = "Incorrect password. Please try again.";
                                break;
                            case "ERROR_INVALID_EMAIL":
                                errorMessage = "Invalid email format.";
                                break;
                            case "ERROR_INVALID_CREDENTIAL":
                                errorMessage = "Incorrect email or password. Please try again.";
                                break;
                        }
                    }
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Fetches the Firebase Cloud Messaging (FCM) token for this device
     * and saves it to Firestore for targeted push notifications.
     */
    private void updateDeviceToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String token = task.getResult();
                FirebaseUtil.currentUserDetails().update("fcmToken", token);
            }
        });
    }

    /**
     * Displays the Android system Biometric/PIN prompt to verify user identity.
     */
    private void showBiometricPrompt() {
        isPromptActive = true;
        Executor executor = ContextCompat.getMainExecutor(this);

        // Build the callback that handles success, error, or failure of the fingerprint/face scan
        BiometricPrompt biometricPrompt = new BiometricPrompt(LoginActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                isPromptActive = false;

                // If the user explicitly hits "Cancel" or "Cancel Authentication", sign them out
                // of Firebase so they don't get stuck in a locked state.
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    hasCanceledPrompt = true;
                    setSessionUnlocked(false); // ensure flag is cleared on manual cancel
                    auth.signOut();
                    Toast.makeText(getApplicationContext(),
                            "Authentication canceled. Please log in manually.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                isPromptActive    = false;
                hasCanceledPrompt = false;

                //  Persist unlock state so screen-off/on doesn't re-prompt.
                setSessionUnlocked(true);

                Toast.makeText(getApplicationContext(), "Login Successful!", Toast.LENGTH_SHORT).show();

                // Update push notification token upon successful full auth
                updateDeviceToken();

                // Route user to the main dashboard
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                isPromptActive = false;
                Toast.makeText(getApplicationContext(),
                        "Biometric not recognized.", Toast.LENGTH_SHORT).show();
            }
        });

        // Configure the UI of the system prompt
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock CareLink")
                .setSubtitle("Use your fingerprint or device PIN to log in")
                // Allow fallback to the device's lock screen PIN/Pattern if biometrics fail
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        // Launch the prompt
        biometricPrompt.authenticate(promptInfo);
    }
}