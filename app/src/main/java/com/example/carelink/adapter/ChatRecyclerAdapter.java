package com.example.carelink.adapter;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carelink.R;
import com.example.carelink.model.ChatMessageModel;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Adapter responsible for binding ChatMessageModel data from Firestore to the RecyclerView in the chat interface.
public class ChatRecyclerAdapter extends FirestoreRecyclerAdapter<ChatMessageModel,ChatRecyclerAdapter.ChatModelViewHolder> {

    // Context and chat metadata passed from the parent activity
    Context context;
    private String chatroomId;
    private String otherUserId;
    private String otherUserUsername;
    private String myUsername;
    private String fcmToken;

    public ChatRecyclerAdapter(@NonNull FirestoreRecyclerOptions<ChatMessageModel> options, Context context, String chatroomId, String otherUserId, String otherUserUsername, String myUsername, String fcmToken) {
        super(options);
        this.context = context;
        this.chatroomId = chatroomId;
        this.otherUserId = otherUserId;
        this.otherUserUsername = otherUserUsername;
        this.myUsername = myUsername;
        this.fcmToken = fcmToken;
    }

    @Override
    protected void onBindViewHolder(@NonNull ChatModelViewHolder holder, int position, @NonNull ChatMessageModel model) {
        // Reset view visibility and content for every item to prevent recycled views from showing artifacts of previously scrolled items.
        holder.leftChatContainer.setVisibility(View.GONE);
        holder.rightChatContainer.setVisibility(View.GONE);
        holder.leftChatTextview.setText(null);
        holder.rightChatTextview.setText(null);

        // Convert Firestore Timestamp into a readable string representation
        String formattedTime = "";
        if (model.getTimestamp() != null) {
            Date date = model.getTimestamp().toDate();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            formattedTime = sdf.format(date);
        }

        // Determine if the current user is the sender to align the message bubble correctly
        if (model.getSenderId().equals(FirebaseUtil.currentUserId())) {
            // Right-aligned view for the local user
            holder.rightChatContainer.setVisibility(View.VISIBLE);
            holder.rightTimestamp.setText(formattedTime);

            // Handle standard text messages, making URLs clickable
            holder.rightChatTextview.setText(model.getMessage());
            holder.rightChatTextview.setMovementMethod(LinkMovementMethod.getInstance());
            Linkify.addLinks(holder.rightChatTextview, Linkify.ALL);
        } else {
            // Left-aligned view for the remote user
            holder.leftChatContainer.setVisibility(View.VISIBLE);
            holder.leftTimestamp.setText(formattedTime);

            // Handle standard text messages, making URLs clickable
            holder.leftChatTextview.setText(model.getMessage());
            holder.leftChatTextview.setMovementMethod(LinkMovementMethod.getInstance());
            Linkify.addLinks(holder.leftChatTextview, Linkify.ALL);
        }
    }

    @NonNull
    @Override
    public ChatModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.chat_message_recycler_row, parent, false);
        return new ChatModelViewHolder(view);
    }

    // ViewHolder class to cache UI components for each chat row
    class ChatModelViewHolder extends RecyclerView.ViewHolder {
        LinearLayout leftChatContainer, rightChatContainer, leftChatLayout, rightChatLayout;
        TextView leftChatTextview, rightChatTextview, leftTimestamp, rightTimestamp;

        public ChatModelViewHolder(@NonNull View itemView) {
            super(itemView);

            // Wrappers containing the entire message block including timestamps
            leftChatContainer = itemView.findViewById(R.id.left_chat_container);
            rightChatContainer = itemView.findViewById(R.id.right_chat_container);

            // Visual chat bubbles
            leftChatLayout = itemView.findViewById(R.id.left_chat_layout);
            rightChatLayout = itemView.findViewById(R.id.right_chat_layout);

            // Message content components
            leftChatTextview = itemView.findViewById(R.id.left_chat_tv);
            rightChatTextview = itemView.findViewById(R.id.right_chat_tv);

            // Timestamp displays below the bubbles
            leftTimestamp = itemView.findViewById(R.id.left_timestamp);
            rightTimestamp = itemView.findViewById(R.id.right_timestamp);
        }
    }
}