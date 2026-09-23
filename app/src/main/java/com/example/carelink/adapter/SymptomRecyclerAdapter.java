package com.example.carelink.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.carelink.MySymptomActivity;
import com.example.carelink.R;
import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.SymptomModel;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Adapter for managing the list of symptom reports in a RecyclerView.
 * Handles UI binding, conditional styling for unread items, and deletion logic
 * differentiated by user role (Patient vs Doctor).
 */
public class SymptomRecyclerAdapter extends FirestoreRecyclerAdapter<SymptomModel, SymptomRecyclerAdapter.SymptomViewHolder> {

    private final Context context;
    private boolean isDoctor;

    public SymptomRecyclerAdapter(@NonNull FirestoreRecyclerOptions<SymptomModel> options, Context context) {
        super(options);
        this.context = context;
    }

    public void setIsDoctor(boolean isDoctor) {
        this.isDoctor = isDoctor;
    }

    @Override
    protected void onBindViewHolder(@NonNull SymptomViewHolder holder, int position, @NonNull SymptomModel model) {

        // Logic to hide the item if the current user has flagged it as hidden
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
        if (model.getHiddenBy() != null && model.getHiddenBy().containsKey(FirebaseUtil.currentUserId())) {
            holder.itemView.setVisibility(View.GONE);
            params.height = 0;
            params.width = 0;
            params.setMargins(0, 0, 0, 0);
            holder.itemView.setLayoutParams(params);
            return;
        } else {
            holder.itemView.setVisibility(View.VISIBLE);
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            int marginHorizontal = (int) (16 * context.getResources().getDisplayMetrics().density);
            int marginTop = (int) (12 * context.getResources().getDisplayMetrics().density);
            params.setMargins(marginHorizontal, marginTop, marginHorizontal, 0);
            holder.itemView.setLayoutParams(params);
        }

        // Populate basic symptom details
        holder.tvTitle.setText(model.getTitle());
        String onsetText = "Onset: " + (model.getOnsetDate() != null ? model.getOnsetDate() : "Unknown");
        holder.tvDate.setText(onsetText);

        // Styling logic: highlight unread symptoms for doctors
        boolean isUnreadByDoctor = isDoctor && (model.getReadBy() == null || !model.getReadBy().containsKey(FirebaseUtil.currentUserId()));

        if (isUnreadByDoctor) {
            holder.rowRootLayout.setBackgroundResource(R.drawable.bg_highlighted_symptom);
            holder.tvTitle.setTextColor(Color.WHITE);
            holder.tvDate.setTextColor(Color.WHITE);
            holder.cardIconBg.setCardBackgroundColor(Color.WHITE);
            holder.ivSymptomIcon.setColorFilter(ContextCompat.getColor(context, R.color.buttons));
            holder.ivDelete.setColorFilter(Color.WHITE);
        } else {
            holder.rowRootLayout.setBackgroundResource(R.drawable.bg_input_field);
            holder.tvTitle.setTextColor(Color.BLACK);
            holder.tvDate.setTextColor(Color.parseColor("#555555"));
            holder.cardIconBg.setCardBackgroundColor(ContextCompat.getColor(context, R.color.buttons));
            holder.ivSymptomIcon.setColorFilter(Color.WHITE);
            holder.ivDelete.setColorFilter(ContextCompat.getColor(context, R.color.buttons));
        }

        // Handle deletion request
        holder.ivDelete.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Remove Symptom")
                    .setMessage("Are you sure you want to remove this item?")
                    .setPositiveButton("Yes", (dialogInterface, which) -> {

                        // Immediate visual feedback by collapsing the item view
                        holder.itemView.setVisibility(View.GONE);
                        RecyclerView.LayoutParams p = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                        p.height = 0;
                        p.width = 0;
                        p.setMargins(0, 0, 0, 0);
                        holder.itemView.setLayoutParams(p);

                        String myId = FirebaseUtil.currentUserId();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        // Patient logic: hard delete from database and cleanup notification counters
                        if (!isDoctor) {
                            db.collection("chatrooms").whereArrayContains("userIds", myId).get().addOnSuccessListener(query -> {
                                for (DocumentSnapshot doc : query.getDocuments()) {
                                    ChatroomModel chat = doc.toObject(ChatroomModel.class);
                                    if (chat != null && chat.getUserIds() != null) {
                                        for (String otherId : chat.getUserIds()) {
                                            if (!otherId.equals(myId)) {
                                                boolean doctorReadIt = model.getReadBy() != null && model.getReadBy().containsKey(otherId);
                                                if (!doctorReadIt) {
                                                    db.collection("users").document(otherId).update("unreadMedicalRecords", FieldValue.increment(-1));
                                                    doc.getReference().update("unreadMedicalCount." + otherId, FieldValue.increment(-1));
                                                }
                                            }
                                        }
                                    }
                                }
                            });
                            db.collection("symptoms").document(model.getSymptomId()).delete();
                        } else {
                            // Doctor logic: soft delete (mark as hidden for current doctor)
                            if (isUnreadByDoctor) {
                                db.collection("users").document(myId).update("unreadMedicalRecords", FieldValue.increment(-1));
                                db.collection("chatrooms").whereArrayContains("userIds", myId).get().addOnSuccessListener(query -> {
                                    for (DocumentSnapshot doc : query.getDocuments()) {
                                        ChatroomModel chat = doc.toObject(ChatroomModel.class);
                                        if (chat != null && chat.getUserIds() != null && chat.getUserIds().contains(model.getPatientId())) {
                                            doc.getReference().update("unreadMedicalCount." + myId, FieldValue.increment(-1));
                                            break;
                                        }
                                    }
                                });
                            }
                            db.collection("symptoms").document(model.getSymptomId()).update("hiddenBy." + myId, true);
                        }
                    })
                    .setNegativeButton("No", null)
                    .create();

            // Style the dialog UI
            dialog.setOnShowListener(d -> {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(context, R.color.buttons));
                int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
                TextView titleView = dialog.findViewById(titleId);
                if (titleView != null) titleView.setTextColor(ContextCompat.getColor(context, R.color.buttons));
                TextView messageView = dialog.findViewById(android.R.id.message);
                if (messageView != null) messageView.setTextColor(ContextCompat.getColor(context, R.color.buttons));
            });
            dialog.show();
        });

        // Click listener to open detailed symptom view
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, MySymptomActivity.class);
            intent.putExtra("symptomId", model.getSymptomId());
            context.startActivity(intent);
        });
    }

    @NonNull
    @Override
    public SymptomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.symptom_recycler_row, parent, false);
        return new SymptomViewHolder(view);
    }

    // ViewHolder class for efficient view lookups
    static class SymptomViewHolder extends RecyclerView.ViewHolder {
        LinearLayout rowRootLayout;
        CardView cardIconBg;
        ImageView ivSymptomIcon, ivDelete;
        TextView tvTitle, tvDate;

        public SymptomViewHolder(@NonNull View itemView) {
            super(itemView);
            rowRootLayout = itemView.findViewById(R.id.row_root_layout);
            cardIconBg = itemView.findViewById(R.id.card_icon_bg);
            ivSymptomIcon = itemView.findViewById(R.id.iv_symptom_icon);
            ivDelete = itemView.findViewById(R.id.iv_delete);
            tvTitle = itemView.findViewById(R.id.tv_row_title);
            tvDate = itemView.findViewById(R.id.tv_row_date);
        }
    }
}