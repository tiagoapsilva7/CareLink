package com.example.carelink;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carelink.adapter.RecentChatRecyclerAdapter;
import com.example.carelink.adapter.SearchUserRecyclerAdapter;
import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class SearchUsersActivity extends AppCompatActivity {

    SearchUserRecyclerAdapter searchAdapter;
    RecentChatRecyclerAdapter recentChatAdapter;
    String message;

    TextView tvSearchTitle;
    EditText etSearchUsername;
    ImageView searchUserIcon;
    RecyclerView recyclerview;
    FrameLayout headerContainer;
    LinearLayout llConnectedPatientsTitle;

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    CollectionReference usersCollection = db.collection("users");

    String currentUserRole = null;
    String currentUserHospital = null;
    boolean isDoctorDashboardMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search_users);

        headerContainer = findViewById(R.id.header_container);
        tvSearchTitle = findViewById(R.id.searchByText);
        etSearchUsername = findViewById(R.id.etSearchUsername);
        recyclerview = findViewById(R.id.recyclerview);
        searchUserIcon = findViewById(R.id.search_user_icon);
        llConnectedPatientsTitle = findViewById(R.id.llConnectedPatientsTitle);

        message = getIntent().getStringExtra("bool");

        // Determine if the activity is functioning as a dashboard view or a standard user search
        isDoctorDashboardMode = "dashboard".equals(message) || "medical".equals(message) || "select_doctor_symptom".equals(message);

        // Handle back button to return specifically to the Chat fragment
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(SearchUsersActivity.this, MainActivity.class);
                intent.putExtra("targetFragment", "ChatFragment");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });

        if (isDoctorDashboardMode) {
            // Hide search bar components for dashboard view
            tvSearchTitle.setVisibility(View.GONE);
            etSearchUsername.setVisibility(View.GONE);
            searchUserIcon.setVisibility(View.GONE);

            llConnectedPatientsTitle.setVisibility(View.VISIBLE);
            headerContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.buttons));

            // Custom UI adjustments if selecting a doctor for symptom submission
            if ("select_doctor_symptom".equals(message)) {
                for (int i = 0; i < llConnectedPatientsTitle.getChildCount(); i++) {
                    View child = llConnectedPatientsTitle.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        tv.setText("Select the doctor to receive your symptom");

                        float currentSp = tv.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSp - 6);
                    }
                }
            }

            loadConnectedPatientsForDashboard();
        } else {
            // Standard search mode setup
            etSearchUsername.setEnabled(false);
            etSearchUsername.setHint("Loading...");
            tvSearchTitle.setText("Loading...");

            // Fetch current user details to configure search scope (hospital and role filtering)
            FirebaseUtil.currentUserDetails().get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    currentUserRole = documentSnapshot.getString("role");
                    currentUserHospital = documentSnapshot.getString("hospital");
                    if (currentUserHospital == null) currentUserHospital = "";

                    etSearchUsername.setEnabled(true);
                    etSearchUsername.setHint("Type the username");

                    if ("Doctor".equals(currentUserRole)) {
                        tvSearchTitle.setText("Search patients in your hospital");
                    } else if ("Caregiver".equals(currentUserRole)) {
                        tvSearchTitle.setText("Search patients in your hospital");
                    } else {
                        tvSearchTitle.setText("Search professionals in your hospital");
                    }
                }
            });

            // Listen for search input changes
            etSearchUsername.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String searchTerm = s.toString().trim();
                    if (!searchTerm.isEmpty()) {
                        recyclerview.setVisibility(View.VISIBLE);
                        setupSearchRecyclerView(searchTerm);
                    } else {
                        recyclerview.setVisibility(View.GONE);
                        if (searchAdapter != null) {
                            searchAdapter.stopListening();
                            recyclerview.setAdapter(null);
                        }
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    // Load recent chats for the dashboard view
    private void loadConnectedPatientsForDashboard() {
        FirebaseUtil.currentUserDetails().get().addOnSuccessListener(doc -> {
            currentUserRole = doc.getString("role");

            Query query = db.collection("chatrooms")
                    .whereArrayContains("userIds", FirebaseUtil.currentUserId())
                    .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING);

            FirestoreRecyclerOptions<ChatroomModel> options = new FirestoreRecyclerOptions.Builder<ChatroomModel>()
                    .setQuery(query, ChatroomModel.class)
                    .build();

            recentChatAdapter = new RecentChatRecyclerAdapter(options, this);
            recentChatAdapter.setCurrentUserRole(currentUserRole);
            recentChatAdapter.setDoctorViewMode(message);

            recyclerview.setLayoutManager(new WrapContentLinearLayoutManager(SearchUsersActivity.this, LinearLayoutManager.VERTICAL, false));
            recyclerview.setAdapter(recentChatAdapter);
            recyclerview.setVisibility(View.VISIBLE);

            recentChatAdapter.startListening();
        });
    }

    // Configure the adapter for user search based on role and hospital
    void setupSearchRecyclerView(String searchTerm) {
        if (currentUserRole == null || currentUserHospital == null) return;

        // Determine target role based on current user's role
        String targetRole = currentUserRole.equals("Patient") ? "Doctor" : "Patient";

        Query query = usersCollection
                .whereEqualTo("role", targetRole)
                .whereEqualTo("hospital", currentUserHospital)
                .whereGreaterThanOrEqualTo("username", searchTerm)
                .whereLessThan("username", searchTerm + "\uf8ff")
                .orderBy("username");

        FirestoreRecyclerOptions<UserModel> options = new FirestoreRecyclerOptions.Builder<UserModel>()
                .setQuery(query, UserModel.class).build();

        if (searchAdapter != null) searchAdapter.stopListening();
        searchAdapter = new SearchUserRecyclerAdapter(options, getApplicationContext());
        searchAdapter.setCurrentUserRole(currentUserRole);
        searchAdapter.setNavigationTarget(message);

        recyclerview.setLayoutManager(new WrapContentLinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerview.setAdapter(searchAdapter);
        searchAdapter.startListening();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (searchAdapter != null) searchAdapter.startListening();
        if (recentChatAdapter != null) recentChatAdapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (searchAdapter != null) searchAdapter.stopListening();
        if (recentChatAdapter != null) recentChatAdapter.stopListening();
    }

    // Prevents crashes related to RecyclerView consistency when dataset updates rapidly
    public static class WrapContentLinearLayoutManager extends LinearLayoutManager {
        public WrapContentLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }
        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            try { super.onLayoutChildren(recycler, state); }
            catch (IndexOutOfBoundsException e) { Log.e("SearchUsersActivity", "Intercepted Crash"); }
        }
    }
}