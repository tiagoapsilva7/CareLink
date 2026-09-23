package com.example.carelink.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carelink.R;
import com.example.carelink.adapter.RecentChatRecyclerAdapter;
import com.example.carelink.adapter.SearchUserRecyclerAdapter;
import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.Query;

public class ChatFragment extends Fragment {

    private RecyclerView recyclerView;
    private EditText etSearchUsername;
    private RecentChatRecyclerAdapter recentChatAdapter;
    private SearchUserRecyclerAdapter searchAdapter;

    private String role = "";
    private String hospital = "";

    public ChatFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(0);
        requireActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.bars_color));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        etSearchUsername = view.findViewById(R.id.et_search_username);

        // Apply custom layout manager to handle potential internal RecyclerView state inconsistencies
        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));

        // Set initial UI state while fetching user data
        etSearchUsername.setEnabled(false);
        etSearchUsername.setHint("Loading...");
        setBottomNavClickable(false);

        // Retrieve the current user's role and hospital to enforce search constraints
        FirebaseUtil.currentUserDetails().get().addOnCompleteListener(task -> {
            if (!isAdded() || getContext() == null) return;

            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                role = task.getResult().getString("role");
                hospital = task.getResult().getString("hospital");
                if (hospital == null) hospital = "";

                // Adjust the search bar hint depending on the user's role
                if ("Caregiver".equalsIgnoreCase(role)) {
                    etSearchUsername.setHint("Search patients");
                } else {
                    etSearchUsername.setHint("Search caregivers");
                }
            }

            etSearchUsername.setEnabled(true);
            setupRecentChatsRecyclerView();
            setBottomNavClickable(true);
        });

        // Monitor text input to toggle between displaying recent chats and active search results
        etSearchUsername.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String searchTerm = s.toString().trim();
                if (searchTerm.isEmpty()) {
                    setupRecentChatsRecyclerView();
                } else {
                    setupSearchRecyclerView(searchTerm);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Define back button behavior to close the activity from this fragment
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requireActivity().finish();
            }
        });

        return view;
    }

    private void setupRecentChatsRecyclerView() {
        if (searchAdapter != null) searchAdapter.stopListening();

        // Query chatrooms associated with the current user, ordering by the most recent message
        Query query = FirebaseUtil.allChatroomCollectionReference()
                .whereArrayContains("userIds", FirebaseUtil.currentUserId())
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<ChatroomModel> options = new FirestoreRecyclerOptions.Builder<ChatroomModel>()
                .setQuery(query, ChatroomModel.class)
                .build();

        recentChatAdapter = new RecentChatRecyclerAdapter(options, requireContext());
        recentChatAdapter.setCurrentUserRole(role);
        recyclerView.setAdapter(recentChatAdapter);
        recentChatAdapter.startListening();
    }

    private void setupSearchRecyclerView(String searchTerm) {
        if (recentChatAdapter != null) recentChatAdapter.stopListening();

        // Determine the opposing role to restrict search boundaries
        String targetRole = "Patient".equalsIgnoreCase(role) ? "Caregiver" : "Patient";

        // Query users matching the target role, hospital, and inputted search term
        Query query = FirebaseUtil.allUserCollectionReference()
                .whereEqualTo("role", targetRole)
                .whereEqualTo("hospital", hospital)
                .whereGreaterThanOrEqualTo("username", searchTerm)
                .whereLessThanOrEqualTo("username", searchTerm + "\uf8ff")
                .orderBy("username");

        FirestoreRecyclerOptions<UserModel> options = new FirestoreRecyclerOptions.Builder<UserModel>()
                .setQuery(query, UserModel.class)
                .build();

        searchAdapter = new SearchUserRecyclerAdapter(options, requireContext());
        searchAdapter.setCurrentUserRole(role);
        recyclerView.setAdapter(searchAdapter);
        searchAdapter.startListening();
    }

    // Controls the interactability of the bottom navigation bar to prevent navigation during async loads
    private void setBottomNavClickable(boolean isClickable) {
        if (getActivity() == null) return;
        try {
            View bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setOnTouchListener(isClickable ? null : (v, event) -> true);
            }
        } catch (Exception e) {
            Log.e("ChatFragment", "Nav lock error", e);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (recentChatAdapter != null) recentChatAdapter.startListening();
        if (searchAdapter != null) searchAdapter.startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (recentChatAdapter != null) recentChatAdapter.stopListening();
        if (searchAdapter != null) searchAdapter.stopListening();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        // Refresh the dataset of whichever adapter is currently active
        if (recyclerView.getAdapter() == recentChatAdapter && recentChatAdapter != null) {
            recentChatAdapter.notifyDataSetChanged();
        } else if (recyclerView.getAdapter() == searchAdapter && searchAdapter != null) {
            searchAdapter.notifyDataSetChanged();
        }
    }

    // Custom layout manager designed to suppress IndexOutOfBoundsExceptions that occasionally occur during rapid dataset changes
    public static class WrapContentLinearLayoutManager extends LinearLayoutManager {
        public WrapContentLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }
        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            try {
                super.onLayoutChildren(recycler, state);
            } catch (IndexOutOfBoundsException e) {
                Log.e("ChatFragment", "Intercepted RecyclerView Crash");
            }
        }
    }
}