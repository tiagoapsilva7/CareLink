package com.example.carelink;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.carelink.model.UserModel;
import com.example.carelink.util.FirebaseUtil;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

public class SplashActivity extends AppCompatActivity {
    FirebaseAuth auth = FirebaseAuth.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // Start the database synchronization before navigating
        syncDatabaseAndProceed();
    }

    private void syncDatabaseAndProceed() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Scan the database for manually created profiles
        db.collection("users").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                WriteBatch batch = db.batch();
                boolean needsUpdate = false;

                for (DocumentSnapshot doc : task.getResult()) {
                    String docId = doc.getId();
                    String userIdField = doc.getString("userId");

                    // If a doctor was created manually in the console and is missing the userId field,
                    // or it doesn't match the document ID, the app automatically fixes it here.
                    if (userIdField == null || userIdField.trim().isEmpty() || !userIdField.equals(docId)) {
                        batch.update(doc.getReference(), "userId", docId);
                        needsUpdate = true;
                    }
                }

                if (needsUpdate) {
                    // Commit the fixes to Firestore, then proceed
                    batch.commit().addOnCompleteListener(bTask -> {
                        Log.d("SplashSync", "Successfully synced missing userIds for manually created doctors.");
                        checkCurrentUserAndNavigate();
                    });
                } else {
                    // Everything is already clean, proceed immediately
                    checkCurrentUserAndNavigate();
                }
            } else {
                checkCurrentUserAndNavigate();
            }
        });
    }

    private void checkCurrentUserAndNavigate() {
        if (auth.getCurrentUser() != null) {

            // 2. Ensure the currently logged-in user actually has a completed Firestore document
            FirebaseUtil.currentUserDetails().get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DocumentSnapshot doc = task.getResult();
                    if (doc != null && !doc.exists()) {

                        // Fallback: The user exists in Auth but not in the database.
                        // Create a default UserModel for them so the app doesn't crash.
                        String email = auth.getCurrentUser().getEmail() != null ? auth.getCurrentUser().getEmail() : "";
                        UserModel newUser = new UserModel(
                                "User",
                                email,
                                Timestamp.now(),
                                auth.getCurrentUser().getUid(),
                                "Patient", // Defaulting to Patient, can be updated in-app later
                                0,
                                ""
                        );

                        FirebaseUtil.currentUserDetails().set(newUser).addOnCompleteListener(t -> navigateToMain());
                    } else {
                        // User exists and is perfectly healthy
                        navigateToMain();
                    }
                } else {
                    navigateToMain();
                }
            });
        } else {
            // Not logged in, go to Login
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}