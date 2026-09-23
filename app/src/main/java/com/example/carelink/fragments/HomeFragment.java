package com.example.carelink.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.carelink.MyMedicalRecordsActivity;
import com.example.carelink.R;
import com.example.carelink.SearchUsersActivity;
import com.example.carelink.VisitCalendarActivity;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.FirebaseUtil;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Objects;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String PREF_NAME = "PulseTrackrPrefs";
    private static final String KEY_USER_ROLE = "cached_user_role";

    private Button medicalRecords, myEvo;
    private ImageView galleryImg, medicalIcon;
    private TextView tvMedicalBadge;
    private FrameLayout flEvo, flMedical;

    UserModel currentUserModel;
    private ListenerRegistration userListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        // Bind Views
        medicalRecords = rootView.findViewById(R.id.medicalRecords);
        myEvo = rootView.findViewById(R.id.myEvo);

        galleryImg = rootView.findViewById(R.id.gallery_img);
        medicalIcon = rootView.findViewById(R.id.medical_icon);
        tvMedicalBadge = rootView.findViewById(R.id.tv_medical_badge);

        flEvo = rootView.findViewById(R.id.fl_evo);
        flMedical = rootView.findViewById(R.id.fl_medical);

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Apply cached role immediately for seamless UI loading
        String cachedRole = prefs.getString(KEY_USER_ROLE, null);


        // Listen for real-time user updates
        if (FirebaseUtil.currentUserId() != null) {
            userListener = FirebaseUtil.currentUserDetails().addSnapshotListener((documentSnapshot, e) -> {
                if (e != null || documentSnapshot == null) return;
                if (!isAdded() || getContext() == null) return;

                if (documentSnapshot.exists()) {
                    currentUserModel = documentSnapshot.toObject(UserModel.class);

                    if (currentUserModel != null) {
                        if (currentUserModel.getRole() != null) {
                            String fetchedRole = currentUserModel.getRole().trim();
                            prefs.edit().putString(KEY_USER_ROLE, fetchedRole).apply();

                        }

                        // Badge logic handling
                        int unread = currentUserModel.getUnreadMedicalRecords();
                        if (unread > 0) {
                            tvMedicalBadge.setVisibility(View.VISIBLE);
                            tvMedicalBadge.setText(String.valueOf(unread));

                            medicalRecords.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                            medicalRecords.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
                            medicalIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.buttons));
                        } else {
                            tvMedicalBadge.setVisibility(View.GONE);

                            medicalRecords.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.buttons)));
                            medicalRecords.setTextColor(Color.WHITE);
                            medicalIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white));
                        }
                    }
                }
            });
        }

        medicalRecords.setOnClickListener(v -> {
            String currentRole = prefs.getString(KEY_USER_ROLE, null);
            if (currentRole != null) {
                FirebaseUtil.currentUserDetails().update("unreadMedicalRecords", 0);

                Intent intent;
                if (Objects.equals(currentRole, "Patient")) {
                    intent = new Intent(getActivity(), MyMedicalRecordsActivity.class);
                } else {
                    intent = new Intent(getActivity(), SearchUsersActivity.class);
                    intent.putExtra("bool", "medical");
                }
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "User data not loaded yet", Toast.LENGTH_SHORT).show();
            }
        });

        myEvo.setOnClickListener(v -> {
            String currentRole = prefs.getString(KEY_USER_ROLE, null);
            if (currentRole != null) {
                Intent intent;
                if (Objects.equals(currentRole, "Patient")) {
                    intent = new Intent(getActivity(), VisitCalendarActivity.class);
                } else {
                    intent = new Intent(getActivity(), SearchUsersActivity.class);
                    intent.putExtra("bool", "dashboard");
                }
                startActivity(intent);
            }
        });

        return rootView;
    }



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        requireActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.bg_color));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}