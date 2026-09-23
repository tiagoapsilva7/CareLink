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

import com.example.carelink.MyVisitReportActivity;
import com.example.carelink.R;
import com.example.carelink.model.VisitReportModel;
import com.example.carelink.util.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Adapter for displaying visit reports in a RecyclerView using Firestore.
 * Manages UI binding, asynchronous resolution of linked user profiles,
 * and soft-deletion via the 'hiddenBy' field.
 */
public class VisitReportRecyclerAdapter extends FirestoreRecyclerAdapter<VisitReportModel, VisitReportRecyclerAdapter.VisitReportViewHolder> {

    private final Context context;
    private boolean isCaregiver;

    public VisitReportRecyclerAdapter(@NonNull FirestoreRecyclerOptions<VisitReportModel> options, Context context) {
        super(options);
        this.context = context;
    }

    public void setIsCaregiver(boolean isCaregiver) {
        this.isCaregiver = isCaregiver;
    }

    @Override
    protected void onBindViewHolder(@NonNull VisitReportViewHolder holder, int position, @NonNull VisitReportModel model) {

        // Check if the current user has soft-deleted this report
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
        if (model.getHiddenBy() != null && model.getHiddenBy().containsKey(FirebaseUtil.currentUserId())) {
            // Hide the view if the user previously deleted/hidden it
            holder.itemView.setVisibility(View.GONE);
            params.height = 0;
            params.width = 0;
            params.setMargins(0, 0, 0, 0);
            holder.itemView.setLayoutParams(params);
            return;
        } else {
            // Otherwise, show the view with standard spacing
            holder.itemView.setVisibility(View.VISIBLE);
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            int marginHorizontal = (int) (16 * context.getResources().getDisplayMetrics().density);
            int marginTop = (int) (12 * context.getResources().getDisplayMetrics().density);
            params.setMargins(marginHorizontal, marginTop, marginHorizontal, 0);
            holder.itemView.setLayoutParams(params);
        }

        // Set date and time display
        holder.tvDateTime.setText(formatDateTime(model));

        // Dynamically resolve names based on user role (Caregiver sees patient name, Patient sees caregiver name)
        if (isCaregiver) {
            resolveAndSetName(holder.tvPerson, model.getPatientId(), "Patient");
        } else {
            resolveAndSetName(holder.tvPerson, model.getCaregiverId(), "Caregiver");
        }

        holder.ivVisitIcon.setImageResource(R.drawable.appointments);

        // Styling for the report row
        holder.rowRootLayout.setBackgroundResource(R.drawable.bg_input_field);
        holder.tvDateTime.setTextColor(Color.BLACK);
        holder.tvPerson.setTextColor(Color.parseColor("#555555"));
        holder.cardIconBg.setCardBackgroundColor(ContextCompat.getColor(context, R.color.buttons));
        holder.ivVisitIcon.setColorFilter(Color.WHITE);
        holder.ivDelete.setColorFilter(ContextCompat.getColor(context, R.color.buttons));

        // Logic for soft-deleting a report
        holder.ivDelete.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Remove Visit Report")
                    .setMessage("Are you sure you want to remove this item?")
                    .setPositiveButton("Yes", (dialogInterface, which) -> {
                        // Collapse view for immediate UI feedback
                        holder.itemView.setVisibility(View.GONE);
                        RecyclerView.LayoutParams p = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                        p.height = 0;
                        p.width = 0;
                        p.setMargins(0, 0, 0, 0);
                        holder.itemView.setLayoutParams(p);

                        // Update document to mark it as hidden by current user ID
                        String myId = FirebaseUtil.currentUserId();
                        int currentPosition = holder.getBindingAdapterPosition();
                        if (currentPosition == RecyclerView.NO_POSITION) return;
                        String docId = getSnapshots().getSnapshot(currentPosition).getId();
                        FirebaseFirestore.getInstance().collection("visit_reports")
                                .document(docId)
                                .update("hiddenBy." + myId, true);
                    })
                    .setNegativeButton("No", null)
                    .create();

            // Style the alert dialog
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

        // Click listener to navigate to the detailed report view
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, MyVisitReportActivity.class);
            intent.putExtra("appointmentId", model.getReportId());
            context.startActivity(intent);
        });
    }

    /**
     * Asynchronously fetches the associated user's username from Firestore.
     * @param tv The TextView to update.
     * @param userId The ID of the user to look up.
     * @param rolePrefix The prefix string (e.g., "Patient: ").
     */
    private void resolveAndSetName(TextView tv, String userId, String rolePrefix) {
        if (userId == null || userId.isEmpty()) {
            tv.setText(rolePrefix + ": Unknown");
            return;
        }

        tv.setText(rolePrefix + ": Loading...");
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getString("username") != null) {
                        tv.setText(rolePrefix + ": " + doc.getString("username").trim());
                    } else {
                        tv.setText(rolePrefix + " ID: " + userId);
                    }
                });
    }

    /**
     * Normalizes date display to DD/MM/YYYY and appends time range.
     */
    private String formatDateTime(VisitReportModel model) {
        String displayDate = model.getVisitDate();

        if (displayDate == null || displayDate.isEmpty()) {
            displayDate = "--/--/----";
        } else if (displayDate.contains("-")) {
            // Convert YYYY-MM-DD to DD/MM/YYYY
            String[] parts = displayDate.split("-");
            if (parts.length == 3) {
                displayDate = parts[2] + "/" + parts[1] + "/" + parts[0];
            }
        }

        String hours = model.getVisitHours() != null ? model.getVisitHours() : "--:-- - --:--";

        return displayDate + " | " + hours;
    }

    @NonNull
    @Override
    public VisitReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.visit_report_recycler_row, parent, false);
        return new VisitReportViewHolder(view);
    }

    /**
     * ViewHolder pattern for efficient access to row UI components.
     */
    static class VisitReportViewHolder extends RecyclerView.ViewHolder {
        LinearLayout rowRootLayout;
        CardView cardIconBg;
        ImageView ivVisitIcon, ivDelete;
        TextView tvDateTime, tvPerson;

        public VisitReportViewHolder(@NonNull View itemView) {
            super(itemView);
            rowRootLayout = itemView.findViewById(R.id.row_root_layout);
            cardIconBg = itemView.findViewById(R.id.card_icon_bg);
            ivVisitIcon = itemView.findViewById(R.id.iv_visit_icon);
            ivDelete = itemView.findViewById(R.id.iv_delete);
            tvDateTime = itemView.findViewById(R.id.tv_row_datetime);
            tvPerson = itemView.findViewById(R.id.tv_row_person);
        }
    }
}