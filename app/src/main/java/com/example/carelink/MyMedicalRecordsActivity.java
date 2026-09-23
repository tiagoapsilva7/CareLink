package com.example.carelink;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.carelink.fragments.PrescriptionsFragment;
import com.example.carelink.fragments.SymptomsFragment;
import com.example.carelink.fragments.VisitReportsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MyMedicalRecordsActivity extends AppCompatActivity {

    VisitReportsFragment visitReportsFragment;
    SymptomsFragment symptomsFragment;
    PrescriptionsFragment prescriptionsFragment;
    BottomNavigationView bottomNavigationView;
    TextView tvMedicalTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_medical_records);

        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.buttons));

        visitReportsFragment = new VisitReportsFragment();
        symptomsFragment = new SymptomsFragment();
        prescriptionsFragment = new PrescriptionsFragment();

        bottomNavigationView = findViewById(R.id.bottom_navigation_medical);
        bottomNavigationView.setItemActiveIndicatorColor(ContextCompat.getColorStateList(this, R.color.bg_color));
        tvMedicalTitle = findViewById(R.id.tvMedicalTitle);

        // Read routing data
        boolean fromChat = getIntent().getBooleanExtra("fromChat", false);

        // If a Caregiver is viewing this, hide the prescriptions tab
        if (fromChat) {
            bottomNavigationView.getMenu().removeItem(R.id.menu_prescriptions);
        }

        // Handle Back Button
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Initial Fragment Load
        tvMedicalTitle.setText("Visit reports");
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container_medical, visitReportsFragment).commit();
        bottomNavigationView.setSelectedItemId(R.id.menu_appointments);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.menu_appointments) {
                    tvMedicalTitle.setText("Visit reports");
                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container_medical, visitReportsFragment).commit();
                    return true;
                } else if (itemId == R.id.menu_symptoms) {
                    tvMedicalTitle.setText("Symptom reports");
                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container_medical, symptomsFragment).commit();
                    return true;
                } else if (itemId == R.id.menu_prescriptions) {
                    tvMedicalTitle.setText("My prescriptions");
                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container_medical, prescriptionsFragment).commit();
                    return true;
                }
                return false;
            }
        });
    }
}