package com.example.carelink.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carelink.ChatActivity;
import com.example.carelink.R;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.AndroidUtil;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

public class SearchUserRecyclerAdapter extends FirestoreRecyclerAdapter<UserModel, SearchUserRecyclerAdapter.UserModelViewHolder> {

    Context context;
    private String imageUrl = null;
    private String navigationTarget = null;
    private String currentUserRole = "";

    public void setNavigationTarget(String navigationTarget) {
        this.navigationTarget = navigationTarget;
    }

    public void setCurrentUserRole(String role) {
        this.currentUserRole = role;
    }

    public SearchUserRecyclerAdapter(@NonNull FirestoreRecyclerOptions<UserModel> options, Context applicationContext) {
        super(options);
        this.context = applicationContext;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Override
    protected void onBindViewHolder(@NonNull UserModelViewHolder holder, int position, @NonNull UserModel model) {

        // Conditionally set the username text based on the logged-in user's role
        if ("Patient".equals(currentUserRole)) {
            holder.usernameText.setText(String.format("Dr. %s", model.getUsername()));
        } else {
            holder.usernameText.setText(model.getUsername());
        }

        // Safe profile picture loading
        if (model.getUserId() != null && !model.getUserId().trim().isEmpty()) {
            FirebaseUtil.getOtherProfilePicStorageRef(model.getUserId()).getDownloadUrl()
                    .addOnCompleteListener(t -> {
                        if (t.isSuccessful()){
                            Uri uri = t.getResult();
                            AndroidUtil.setProfilePic(context, uri, holder.profilePic);
                        }
                    });
        }

        // On-click listener for navigating to the next screen
        holder.itemView.setOnClickListener(v -> {
            if (model.getUserId() == null || model.getUserId().trim().isEmpty()) {
                return; // Ignore clicks on corrupted database profiles
            }

            // Routing for the Doctor's/Caregiver's Patient Dashboard View
            if ("view_dashboard".equals(navigationTarget)) {

            } else {

                Intent intent = new Intent(context, ChatActivity.class);
                AndroidUtil.passUserModelAsIntent(intent, model);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        });
    }

    @NonNull
    @Override
    public UserModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.search_user_recycler_row, parent, false);
        return new UserModelViewHolder(view);
    }

    class UserModelViewHolder extends RecyclerView.ViewHolder {
        TextView usernameText;
        ImageView profilePic;

        public UserModelViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.user_name_text);
            profilePic = itemView.findViewById(R.id.profile_pic_image_view);
        }
    }
}