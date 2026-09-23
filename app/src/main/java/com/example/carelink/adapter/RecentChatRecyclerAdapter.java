package com.example.carelink.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carelink.ChatActivity;
import com.example.carelink.CreateSymptomActivity;
import com.example.carelink.MyMedicalRecordsActivity;
import com.example.carelink.R;
import com.example.carelink.VisitCalendarActivity;
import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.AndroidUtil;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

// Adapter to manage and display recent chatroom sessions in a RecyclerView
public class RecentChatRecyclerAdapter extends FirestoreRecyclerAdapter<ChatroomModel, RecentChatRecyclerAdapter.ChatroomModelViewHolder> {

    Context context;
    private String currentUserRole = "";
    private String doctorViewMode = "";

    public RecentChatRecyclerAdapter(@NonNull FirestoreRecyclerOptions<ChatroomModel> options, Context context) {
        super(options);
        this.context = context;
    }

    public void setCurrentUserRole(String role) {
        this.currentUserRole = role;
        notifyDataSetChanged();
    }

    public void setDoctorViewMode(String mode) {
        this.doctorViewMode = mode != null ? mode : "";
    }

    @Override
    protected void onBindViewHolder(@NonNull ChatroomModelViewHolder holder, int position, @NonNull ChatroomModel model) {
        holder.profilePic.setImageResource(R.drawable.profile);

        // Retrieve the partner user's details for this specific chatroom
        FirebaseUtil.getOtherUserFromChatroom(model.getUserIds())
                .get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        boolean lastMessageSentByMe = model.getLastMessageSenderId() != null && model.getLastMessageSenderId().equals(FirebaseUtil.currentUserId());
                        UserModel otherUserModel = task.getResult().toObject(UserModel.class);

                        String chatStatus = model.getStatus() != null ? model.getStatus() : "accepted";
                        boolean isPendingReceiver = "pending".equals(chatStatus) && model.getRequestSenderId() != null && !model.getRequestSenderId().equals(FirebaseUtil.currentUserId());

                        boolean isSymptomMode = "select_doctor_symptom".equals(doctorViewMode);
                        boolean isSpecialMode = "dashboard".equals(doctorViewMode) || "medical".equals(doctorViewMode) || isSymptomMode;

                        // UI logic for "Symptom Selection" mode
                        if (isSymptomMode) {
                            holder.rowRootLayout.setBackgroundColor(Color.TRANSPARENT);
                            holder.unreadCountText.setVisibility(View.GONE);
                            holder.usernameText.setTypeface(null, Typeface.NORMAL);
                            holder.lastMessageText.setTypeface(null, Typeface.NORMAL);
                            holder.lastMessageTime.setVisibility(View.GONE);
                            holder.lastMessageDay.setVisibility(View.GONE);
                        }
                        // UI logic for standard chat mode (displaying unread counts and timestamps)
                        else if (!isSpecialMode) {
                            Map<String, Long> unreadMap = model.getUnreadMessageCount();
                            long unreadCount = 0;
                            if (unreadMap != null && unreadMap.containsKey(FirebaseUtil.currentUserId())) {
                                Object countObj = unreadMap.get(FirebaseUtil.currentUserId());
                                if (countObj instanceof Number) unreadCount = ((Number) countObj).longValue();
                            }

                            if (isPendingReceiver && unreadCount == 0) unreadCount = 1;

                            if (unreadCount > 0) {
                                holder.rowRootLayout.setBackgroundColor(Color.WHITE);
                                holder.unreadCountText.setVisibility(View.VISIBLE);
                                holder.unreadCountText.setText(String.valueOf(unreadCount));
                                holder.lastMessageText.setTypeface(null, Typeface.BOLD);
                                holder.usernameText.setTypeface(null, Typeface.BOLD);
                            } else {
                                holder.rowRootLayout.setBackgroundColor(Color.TRANSPARENT);
                                holder.unreadCountText.setVisibility(View.GONE);
                                holder.lastMessageText.setTypeface(null, Typeface.NORMAL);
                                holder.usernameText.setTypeface(null, Typeface.NORMAL);
                            }

                            if (model.getLastMessageTimestamp() != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
                                String timeStr = sdf.format(model.getLastMessageTimestamp().toDate());
                                holder.lastMessageTime.setText(timeStr);
                                holder.lastMessageTime.setVisibility(View.VISIBLE);
                            } else {
                                holder.lastMessageTime.setVisibility(View.GONE);
                            }
                            holder.lastMessageDay.setVisibility(View.GONE);

                        }
                        // UI logic for Dashboard/Medical mode (displaying medical unread counts)
                        else {
                            Map<String, Long> unreadMedMap = model.getUnreadMedicalCount();
                            long unreadMedCount = 0;
                            if (unreadMedMap != null && unreadMedMap.containsKey(FirebaseUtil.currentUserId())) {
                                Object countObj = unreadMedMap.get(FirebaseUtil.currentUserId());
                                if (countObj instanceof Number) unreadMedCount = ((Number) countObj).longValue();
                            }

                            if (unreadMedCount > 0) {
                                holder.rowRootLayout.setBackgroundColor(Color.WHITE);
                                holder.unreadCountText.setVisibility(View.VISIBLE);
                                holder.unreadCountText.setText(String.valueOf(unreadMedCount));
                                holder.usernameText.setTypeface(null, Typeface.BOLD);
                            } else {
                                holder.rowRootLayout.setBackgroundColor(Color.TRANSPARENT);
                                holder.unreadCountText.setVisibility(View.GONE);
                                holder.usernameText.setTypeface(null, Typeface.NORMAL);
                            }

                            holder.lastMessageTime.setVisibility(View.GONE);
                            holder.lastMessageDay.setVisibility(View.GONE);
                        }

                        // Load other user's profile image
                        FirebaseUtil.getOtherProfilePicStorageRef(otherUserModel.getUserId()).getDownloadUrl()
                                .addOnCompleteListener(t -> {
                                    if (t.isSuccessful()) AndroidUtil.setProfilePic(context, t.getResult(), holder.profilePic);
                                });

                        // Set display username
                        if (currentUserRole != null && currentUserRole.trim().equalsIgnoreCase("Patient")) {
                            holder.usernameText.setText(String.format("Dr. %s", otherUserModel.getUsername()));
                        } else {
                            holder.usernameText.setText(otherUserModel.getUsername());
                        }

                        // Set status text based on active view mode
                        if (isSymptomMode) {
                            holder.lastMessageText.setText(otherUserModel.getSpecialty() != null && !otherUserModel.getSpecialty().isEmpty() ? otherUserModel.getSpecialty() : "Specialty not specified");
                            holder.lastMessageText.setTypeface(null, Typeface.NORMAL);
                        } else if (isSpecialMode) {
                            String lastOnlineStr = "Unknown";
                            if (otherUserModel.getLastOnline() != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                                lastOnlineStr = sdf.format(otherUserModel.getLastOnline().toDate());
                            }
                            holder.lastMessageText.setText("Last Online: " + lastOnlineStr);
                            holder.lastMessageText.setTypeface(null, Typeface.ITALIC);
                        } else if ("pending".equals(chatStatus)) {
                            if (!isPendingReceiver) holder.lastMessageText.setText("Request pending...");
                            else holder.lastMessageText.setText("New Chat Request!");
                        } else {
                            if (lastMessageSentByMe) {
                                holder.lastMessageText.setText("You: " + model.getLastMessage());
                            } else {
                                holder.lastMessageText.setText(model.getLastMessage());
                            }
                        }

                        // Handle row click navigation logic
                        holder.itemView.setOnClickListener(v -> {
                            if ("dashboard".equals(doctorViewMode)) {
                                Intent intent = new Intent(context, VisitCalendarActivity.class);
                                intent.putExtra("userId", otherUserModel.getUserId());
                                intent.putExtra("otherUserName", otherUserModel.getUsername());
                                intent.putExtra("fromChat", true);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);

                            } else if ("medical".equals(doctorViewMode)) {
                                String chatroomId = getSnapshots().getSnapshot(holder.getBindingAdapterPosition()).getId();
                                java.util.Map<String, Object> resetData = new java.util.HashMap<>();
                                resetData.put(FirebaseUtil.currentUserId(), 0);
                                java.util.Map<String, Object> mergeData = new java.util.HashMap<>();
                                mergeData.put("unreadMedicalCount", resetData);
                                FirebaseFirestore.getInstance().collection("chatrooms").document(chatroomId)
                                        .set(mergeData, com.google.firebase.firestore.SetOptions.merge());

                                Intent intent = new Intent(context, MyMedicalRecordsActivity.class);
                                intent.putExtra("userId", otherUserModel.getUserId());
                                intent.putExtra("otherUserName", otherUserModel.getUsername());
                                intent.putExtra("fromChat", true);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);

                            } else if (isSymptomMode) {
                                Intent intent = new Intent(context, CreateSymptomActivity.class);
                                intent.putExtra("patientId", FirebaseUtil.currentUserId());
                                intent.putExtra("targetDoctorId", otherUserModel.getUserId());
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);

                            } else {
                                Intent intent = new Intent(context, ChatActivity.class);
                                AndroidUtil.passUserModelAsIntent(intent, otherUserModel);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                            }
                        });
                    }
                });
    }

    @NonNull
    @Override
    public ChatroomModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.recent_chat_recycler_row, parent, false);
        return new ChatroomModelViewHolder(view);
    }

    // ViewHolder class to cache UI components for each chat row
    class ChatroomModelViewHolder extends RecyclerView.ViewHolder {
        TextView usernameText, lastMessageText, lastMessageTime, lastMessageDay, unreadCountText;
        ImageView profilePic;
        LinearLayout rowRootLayout;

        public ChatroomModelViewHolder(@NonNull View itemView) {
            super(itemView);
            rowRootLayout = itemView.findViewById(R.id.row_root_layout);
            usernameText = itemView.findViewById(R.id.user_name_text);
            lastMessageText = itemView.findViewById(R.id.last_message_text);
            lastMessageTime = itemView.findViewById(R.id.last_message_time_text);
            lastMessageDay = itemView.findViewById(R.id.last_message_day);
            profilePic = itemView.findViewById(R.id.profile_pic_image_view);
            unreadCountText = itemView.findViewById(R.id.unread_count_text);
        }
    }
}