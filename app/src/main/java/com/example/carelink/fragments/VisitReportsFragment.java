package com.example.carelink.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carelink.CreateVisitReportActivity;
import com.example.carelink.R;
import com.example.carelink.VisitCalendarActivity;
import com.example.carelink.adapter.VisitReportRecyclerAdapter;
import com.example.carelink.model.VisitReportModel;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

/**
 * Fragment that displays a list of visit reports for a specific patient.
 * Caregivers can view/create reports, while patients can view all reports filed for them.
 */
public class VisitReportsFragment extends Fragment {

    RecyclerView recyclerView;
    FloatingActionButton fabAddVisit;
    VisitReportRecyclerAdapter adapter;

    String targetPatientId;
    String targetPatientName;
    boolean isCaregiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_visit_reports, container, false);

        recyclerView = view.findViewById(R.id.visit_reports_recyclerview);
        fabAddVisit = view.findViewById(R.id.fab_add_visit);

        // Retrieve intent data to identify if the current user is a caregiver viewing a patient,
        // or a patient viewing their own records.
        if (getActivity() != null && getActivity().getIntent() != null) {
            boolean fromChat = getActivity().getIntent().getBooleanExtra("fromChat", false);
            String intentUserId = getActivity().getIntent().getStringExtra("userId");
            targetPatientName = getActivity().getIntent().getStringExtra("otherUserName");

            if (fromChat && intentUserId != null) {
                targetPatientId = intentUserId;
                isCaregiver = true;
            } else {
                targetPatientId = FirebaseUtil.currentUserId();
                isCaregiver = false;
            }
        }

        // Only show the FAB for caregivers, allowing them to create new visit reports.
        if (isCaregiver) {
            fabAddVisit.setVisibility(View.VISIBLE);
            fabAddVisit.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), CreateVisitReportActivity.class);
                intent.putExtra("userId", targetPatientId);
                intent.putExtra("otherUserName", targetPatientName);
                startActivity(intent);
            });
        }

        setupRecyclerView();

        return view;
    }

    /**
     * Configures the Firestore query and adapter.
     * Caregivers see reports they authored; Patients see all reports regarding their care.
     */
    private void setupRecyclerView() {
        Query query;

        if (isCaregiver) {
            // CAREGIVER VIEW: filter by the specific patient and the caregiver's own ID
            query = FirebaseFirestore.getInstance().collection("visit_reports")
                    .whereEqualTo("patientId", targetPatientId)
                    .whereEqualTo("caregiverId", FirebaseUtil.currentUserId())
                    .orderBy("visitDate", Query.Direction.DESCENDING);
        } else {
            // PATIENT VIEW: filter only by patient ID to show all relevant history
            query = FirebaseFirestore.getInstance().collection("visit_reports")
                    .whereEqualTo("patientId", targetPatientId)
                    .orderBy("visitDate", Query.Direction.DESCENDING);
        }

        // Configure FirebaseUI options
        FirestoreRecyclerOptions<VisitReportModel> options = new FirestoreRecyclerOptions.Builder<VisitReportModel>()
                .setQuery(query, VisitReportModel.class)
                .build();

        adapter = new VisitReportRecyclerAdapter(options, getContext());
        adapter.setIsCaregiver(isCaregiver);

        // Use custom LayoutManager to prevent potential RecyclerView crashes during data updates
        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Start listening for real-time Firestore updates when the fragment becomes visible
        if (adapter != null) adapter.startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Stop listening for updates to save resources when the fragment is not visible
        if (adapter != null) adapter.stopListening();
    }

    /**
     * Custom LinearLayoutManager implementation that catches and logs IndexOutOfBoundsExceptions
     * that occasionally occur in RecyclerView during rapid data updates.
     */
    public static class WrapContentLinearLayoutManager extends LinearLayoutManager {
        public WrapContentLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }
        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            try {
                super.onLayoutChildren(recycler, state);
            } catch (IndexOutOfBoundsException e) {
                Log.e("VisitReportsFragment", "Intercepted RecyclerView Inconsistency Crash");
            }
        }
    }
}