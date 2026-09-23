package com.example.carelink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.carelink.model.UserModel;
import com.example.carelink.util.AndroidUtil;
import com.example.carelink.util.FirebaseUtil;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.Executor;

/**
 * Handles the registration flow for new Patient users.
 * Integrates real-time form validation, username uniqueness verification,
 * biometric authentication, and Firebase account creation.
 */
public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    String role = "Patient";
    UserModel userModel;

    private EditText etUsername, etEmail, age, etAddress, etPassword1, etPassword2;
    private Button register_button;
    private Spinner spinnerHospital;

    // Arrays mapping UI display names to database storage values for hospital selection
    private final String[] displayHospitals = {"Select your ULS", "ULS São João", "ULS Santa Maria", "ULS Coimbra"};
    private final String[] dbHospitals = {"", "Hospital São João", "Hospital Santa Maria", "Hospital Coimbra"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Configure system UI to match the application's light theme
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setStatusBarColor(getResources().getColor(R.color.bg_color));

        // Intercept hardware back button to ensure proper routing back to LoginActivity
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                intent.putExtra("targetFragment", "ChatFragment");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });

        auth = FirebaseAuth.getInstance();

        // View bindings
        register_button = findViewById(R.id.settings);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        age = findViewById(R.id.etAge);
        etAddress = findViewById(R.id.etAddress);
        etPassword1 = findViewById(R.id.etPassword1);
        etPassword2 = findViewById(R.id.etPassword2);
        spinnerHospital = findViewById(R.id.spinnerHospital);

        // Initial state for the registration button
        register_button.setAlpha(0.5f);
        register_button.setEnabled(false);

        // Custom ArrayAdapter to style the placeholder item differently from selectable items
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, displayHospitals) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextSize(14f);
                if (position == 0) {
                    tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                } else {
                    tv.setTextColor(getResources().getColor(R.color.buttons));
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                view.setBackgroundColor(getResources().getColor(android.R.color.white));
                if (position == 0) {
                    tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                } else {
                    tv.setTextColor(getResources().getColor(R.color.buttons));
                }
                return view;
            }
        };

        spinnerHospital.setAdapter(spinnerAdapter);

        // Trigger form validation whenever the hospital selection changes
        spinnerHospital.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                validateForm();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Real-time input validation logic
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Password length validation
                if (etPassword1.getText().length() > 0 && etPassword1.getText().length() < 8) {
                    etPassword1.setError("Password must be at least 8 characters long.");
                } else {
                    etPassword1.setError(null);
                }

                // Password confirmation validation
                if (etPassword2.getText().length() > 0) {
                    if (etPassword1.getText().length() == 0) {
                        etPassword2.setError("Start by typing the password in the field above.");
                    } else if (!etPassword2.getText().toString().trim().equals(etPassword1.getText().toString().trim())) {
                        etPassword2.setError("Passwords must be equal.");
                    } else {
                        etPassword2.setError(null);
                    }
                } else {
                    etPassword2.setError(null);
                }

                // Age format and logical bounds validation
                if (age.getText().length() > 0 && !age.getText().toString().trim().matches("\\d+")) {
                    age.setError("You must write an integer number");
                } else if (age.getText().length() > 0 && AndroidUtil.isInteger(age.getText().toString().trim()) && (Integer.parseInt(age.getText().toString().trim()) > 110 || Integer.parseInt(age.getText().toString().trim()) < 18)){
                    age.setError("Age must be between 18 and 110");
                } else {
                    age.setError(null);
                }

                // Username length constraint
                if (etUsername.getText().length() > 12){
                    etUsername.setError("Username cannot exceed 12 characters.");
                } else {
                    etUsername.setError(null);
                }

                // Evaluate overall form state
                validateForm();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        // Attach the TextWatcher to all relevant input fields
        etPassword1.addTextChangedListener(watcher);
        etPassword2.addTextChangedListener(watcher);
        etUsername.addTextChangedListener(watcher);
        age.addTextChangedListener(watcher);
        etAddress.addTextChangedListener(watcher);

        // Registration execution logic
        register_button.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password1 = etPassword1.getText().toString().trim();
            String password2 = etPassword2.getText().toString().trim();
            String Age = age.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            int selectedHospitalIndex = spinnerHospital.getSelectedItemPosition();
            String selectedHospital = dbHospitals[selectedHospitalIndex];

            try {
                // Final strict validation check before network calls
                if (!(password1.equals(password2))) throw new Exception("Passwords do not match.");
                if (password1.length() < 8) throw new Exception("Password must be at least 8 characters long.");
                if (!Age.isEmpty() && !AndroidUtil.isInteger(Age)) throw new Exception("You must write an integer number of years.");
                if (!Age.isEmpty() && Age.matches("\\d+") && (Integer.parseInt(Age) > 110 || Integer.parseInt(Age) < 18)) throw new Exception("Age must be between 18 and 110.");
                if (username.length() > 12) throw new Exception("Username cannot exceed 12 characters.");
                if (address.isEmpty()) throw new Exception("Please provide your address.");
                if (selectedHospitalIndex == 0) throw new Exception("Please select a hospital from the list.");

                registerUser(username, email, password1, Integer.parseInt(Age), selectedHospital, address);

            } catch (Exception e) {
                Toast.makeText(RegisterActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Evaluates the state of all input fields and toggles the registration button's availability.
     * Also manages dynamic error indicators for the Spinner view.
     */
    private void validateForm() {
        boolean isEnabled =
                !etUsername.getText().toString().trim().isEmpty() &&
                        etUsername.getText().length() <= 12 &&
                        !etPassword1.getText().toString().trim().isEmpty() &&
                        !etPassword2.getText().toString().trim().isEmpty() &&
                        !age.getText().toString().trim().isEmpty() &&
                        !etAddress.getText().toString().trim().isEmpty() &&
                        spinnerHospital.getSelectedItemPosition() > 0;

        register_button.setEnabled(isEnabled);
        register_button.setAlpha(isEnabled ? 1.0f : 0.5f);

        // Apply error UI to the Spinner if the user is filling out the form but missed the hospital selection
        boolean isPasswordStarted = etPassword1.getText().length() > 0 || etPassword2.getText().length() > 0;
        boolean isHospitalEmpty = spinnerHospital.getSelectedItemPosition() == 0;

        View selectedView = spinnerHospital.getSelectedView();
        if (selectedView instanceof TextView) {
            if (isPasswordStarted && isHospitalEmpty) {
                ((TextView) selectedView).setError("Select a valid ULS");
            } else {
                ((TextView) selectedView).setError(null);
            }
        }
    }

    /**
     * Checks Firestore to ensure the requested username is not already taken.
     * Proceeds to biometric authentication if the username is available.
     */
    private void registerUser(String username, String email, String password, int age, String hospital, String address) {
        FirebaseUtil.allUserCollectionReference()
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        Toast.makeText(RegisterActivity.this, "Username already exists. Please choose another one.", Toast.LENGTH_SHORT).show();
                    } else if (task.isSuccessful()) {
                        showBiometricPrompt(username, email, password, age, hospital, address);
                    } else {
                        Toast.makeText(RegisterActivity.this, "Error checking username: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Initiates the system biometric/credential prompt to secure the account creation process.
     */
    private void showBiometricPrompt(String username, String email, String password, int age, String hospital, String address) {
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(RegisterActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(), "Authentication canceled. Account was not created.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // Proceed with Firebase auth only after successful biometric/credential verification
                createAccountInFirebase(username, email, password, age, hospital, address);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Biometric not recognized.", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Secure your PulseTrackr Account")
                .setSubtitle("Use your fingerprint or device PIN to confirm registration")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * Finalizes the registration by creating the user in Firebase Authentication,
     * retrieving the FCM token, saving user metadata to Firestore, and handling session persistence.
     */
    private void createAccountInFirebase(String username, String email, String password, int age, String hospital, String address) {
        Toast.makeText(RegisterActivity.this, "Creating account, please wait...", Toast.LENGTH_SHORT).show();

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(RegisterActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Retrieve the device's token for targeted push notifications
                            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
                                String fcmToken = "";
                                if (tokenTask.isSuccessful() && tokenTask.getResult() != null) {
                                    fcmToken = tokenTask.getResult();
                                }

                                // Construct the user data model
                                userModel = new UserModel(username, email, Timestamp.now(), FirebaseUtil.currentUserId(), role, age, hospital);
                                userModel.setFcmToken(fcmToken);
                                userModel.setAddress(address);

                                // Commit the model to the Firestore database
                                FirebaseUtil.currentUserDetails().set(userModel).addOnCompleteListener(dbTask -> {
                                    // Mark the session as unlocked so the user is not immediately prompted for biometrics again upon navigation
                                    getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE)
                                            .edit()
                                            .putBoolean(LoginActivity.KEY_UNLOCKED, true)
                                            .apply();

                                    Toast.makeText(RegisterActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();

                                    // Navigate to MainActivity and clear the activity stack
                                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                });
                            });
                        } else {
                            Toast.makeText(RegisterActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}