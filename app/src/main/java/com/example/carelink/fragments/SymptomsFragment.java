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

import com.example.carelink.CreateSymptomActivity;
import com.example.carelink.R;
import com.example.carelink.adapter.SymptomRecyclerAdapter;
import com.example.carelink.model.SymptomModel;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class SymptomsFragment extends Fragment {

    RecyclerView recyclerView;
    FloatingActionButton fabAddSymptom;
    SymptomRecyclerAdapter adapter;

    String targetPatientId;
    boolean isDoctor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_symptoms, container, false);

        recyclerView = view.findViewById(R.id.symptoms_recyclerview);
        fabAddSymptom = view.findViewById(R.id.fab_add_symptom);

        if (getActivity() != null && getActivity().getIntent() != null) {
            boolean fromChat = getActivity().getIntent().getBooleanExtra("fromChat", false);
            String intentUserId = getActivity().getIntent().getStringExtra("userId");

            if (fromChat && intentUserId != null) {
                targetPatientId = intentUserId;
                isDoctor = true;
            } else {
                targetPatientId = FirebaseUtil.currentUserId();
                isDoctor = false;
            }
        }

        if (!isDoctor) {
            fabAddSymptom.setVisibility(View.VISIBLE);
            fabAddSymptom.setOnClickListener(v -> {
                // FIXED: Skips the search screen and goes straight to creating the symptom
                Intent intent = new Intent(getContext(), CreateSymptomActivity.class);
                intent.putExtra("patientId", targetPatientId);
                startActivity(intent);
            });
        } else {
            fabAddSymptom.setVisibility(View.GONE);
        }

        setupRecyclerView();
        return view;
    }

    private void setupRecyclerView() {
        // Both patients and caregivers now just query the symptoms tied to the patientId
        Query query = FirebaseFirestore.getInstance().collection("symptoms")
                .whereEqualTo("patientId", targetPatientId)
                .orderBy("onsetTimestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<SymptomModel> options = new FirestoreRecyclerOptions.Builder<SymptomModel>()
                .setQuery(query, SymptomModel.class)
                .build();

        adapter = new SymptomRecyclerAdapter(options, getContext());
        adapter.setIsDoctor(isDoctor);

        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter != null) adapter.startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (adapter != null) adapter.stopListening();
    }

    public static class WrapContentLinearLayoutManager extends LinearLayoutManager {
        public WrapContentLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }
        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            try { super.onLayoutChildren(recycler, state); }
            catch (IndexOutOfBoundsException e) { Log.e("SymptomsFragment", "Crash intercepted"); }
        }
    }
}