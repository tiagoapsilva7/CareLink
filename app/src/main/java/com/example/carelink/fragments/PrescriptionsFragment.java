package com.example.carelink.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.carelink.CreateRemindersActivity;
import com.example.carelink.R;
import com.example.carelink.model.PrescriptionModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Fragment responsible for managing, viewing, and uploading prescriptions.
 * It uses PDFBox to locally parse uploaded PDFs to extract medication details (drug name and posology),
 * and syncs data and files with Firebase Firestore and Storage.
 */
public class PrescriptionsFragment extends Fragment {

    // UI elements declaration
    private Button btnAddPrescription, btnConfirmUpload;
    private LinearLayout llUploadPanel, boxUploadPrescription, llListContainer;
    private TextView tvPrescriptionStatus, tvEmptyState;

    // Firebase database instances and state tracking variables
    private FirebaseFirestore firestore;
    private String currentUserId;
    private ListenerRegistration prescriptionsListener;

    // Variables for holding temporary upload data
    private Uri selectedPdfUri;
    private String extractedDrugName = "";
    private String extractedPosology = "";

    // Local list caching the user's prescriptions from Firestore
    private final List<PrescriptionModel> prescriptions = new ArrayList<>();

    // Launcher for handling the PDF file selection intent
    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // If the user successfully selects a file
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    selectedPdfUri = result.getData().getData();

                    // Update UI to show parsing status
                    tvPrescriptionStatus.setText("Analyzing PDF...");
                    tvPrescriptionStatus.setTextColor(Color.BLACK);
                    boxUploadPrescription.setBackgroundResource(R.drawable.bg_input_field);
                    btnConfirmUpload.setVisibility(View.GONE);

                    // Reset previously extracted data
                    extractedDrugName = "";
                    extractedPosology = "";

                    // Start background thread to analyze the selected PDF
                    analyzePrescriptionPdf(selectedPdfUri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the XML layout for this fragment
        return inflater.inflate(R.layout.fragment_prescriptions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize PDFBox resource loader required for parsing PDFs on Android
        PDFBoxResourceLoader.init(requireContext().getApplicationContext());

        // Bind UI components to their respective views
        btnAddPrescription = view.findViewById(R.id.btn_add_prescription);
        llUploadPanel = view.findViewById(R.id.ll_upload_panel);
        boxUploadPrescription = view.findViewById(R.id.box_upload_prescription);
        tvPrescriptionStatus = view.findViewById(R.id.tv_prescription_status);
        btnConfirmUpload = view.findViewById(R.id.btn_confirm_upload);
        llListContainer = view.findViewById(R.id.ll_prescriptions_list_container);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);

        // Initialize Firebase instances and fetch the current user's ID
        firestore = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Toggle visibility of the upload panel when the add button is clicked
        btnAddPrescription.setOnClickListener(v -> {
            boolean isVisible = llUploadPanel.getVisibility() == View.VISIBLE;
            llUploadPanel.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            btnAddPrescription.setText(isVisible ? "+ Add Prescription" : "Cancel");
        });

        // Open the document picker when the upload box is clicked
        boxUploadPrescription.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            pdfPickerLauncher.launch(intent);
        });

        // Trigger the Firebase upload process when the user confirms
        btnConfirmUpload.setOnClickListener(v -> uploadPrescriptionToFirebase());

        // Start listening to the prescriptions collection in Firestore
        attachPrescriptionsListener();
    }

    /**
     * Attaches a real-time listener to Firestore to fetch and auto-update
     * the list of prescriptions for the current user.
     */
    private void attachPrescriptionsListener() {
        prescriptionsListener = firestore.collection("prescriptions")
                .whereEqualTo("patientId", currentUserId)
                .addSnapshotListener((querySnapshot, e) -> {
                    // Ensure the fragment is still attached to the activity
                    if (!isAdded()) return;

                    if (e != null) {
                        Toast.makeText(requireContext(), "Failed to load prescriptions.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Clear the current list to prevent duplicates on update
                    prescriptions.clear();
                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            PrescriptionModel model = doc.toObject(PrescriptionModel.class);
                            prescriptions.add(model);
                        }
                    }

                    // Sort the fetched prescriptions by creation date (newest first)
                    Collections.sort(prescriptions, (p1, p2) -> {
                        Timestamp t1 = p1.getCreatedAt();
                        Timestamp t2 = p2.getCreatedAt();
                        if (t1 == null || t2 == null) return 0;
                        return t2.compareTo(t1); // newest first
                    });

                    // Update the UI with the sorted list
                    renderPrescriptionsList();
                });
    }

    /**
     * Dynamically inflates card views for each prescription and adds them to the container.
     */
    private void renderPrescriptionsList() {
        // Clear previous views to prepare for redrawing
        llListContainer.removeAllViews();

        // Show empty state text if no prescriptions exist
        if (prescriptions.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        tvEmptyState.setVisibility(View.GONE);

        // Iterate over the data list to build individual UI cards
        for (PrescriptionModel prescription : prescriptions) {
            View card = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_prescription_card, llListContainer, false);

            // Bind card-specific UI elements
            TextView tvCompound = card.findViewById(R.id.tv_card_compound);
            TextView tvPosology = card.findViewById(R.id.tv_card_posology);
            TextView tvStatus = card.findViewById(R.id.tv_card_status);
            ImageView ivView = card.findViewById(R.id.iv_card_view_pdf);
            ImageView ivNotify = card.findViewById(R.id.iv_card_setup_notifications);
            ImageView ivRemove = card.findViewById(R.id.iv_card_remove_pdf);

            // Set fallback text if extraction data is missing
            String compound = prescription.getCompound() != null && !prescription.getCompound().isEmpty()
                    ? prescription.getCompound() : "Unidentified medication";
            String posology = prescription.getPosology() != null && !prescription.getPosology().isEmpty()
                    ? prescription.getPosology() : "No posology detected";

            tvCompound.setText(compound);
            tvPosology.setText(posology);

            // Determine and display whether reminders are currently active for this prescription
            boolean remindersActive = prescription.getReminderRequestCodes() != null
                    && !prescription.getReminderRequestCodes().isEmpty();
            tvStatus.setText(remindersActive ? "Reminders active" : "No reminders set");
            tvStatus.setTextColor(remindersActive ? Color.parseColor("#006400") : Color.GRAY);

            // Enable or disable the notification button based on whether data was successfully extracted
            boolean canSetupReminders = prescription.getCompound() != null && !prescription.getCompound().isEmpty()
                    && prescription.getPosology() != null && !prescription.getPosology().isEmpty();

            ivNotify.setEnabled(canSetupReminders);
            ivNotify.setAlpha(canSetupReminders ? 1.0f : 0.3f);

            // Set listener to open the PDF in an external viewer
            ivView.setOnClickListener(v -> {
                if (prescription.getPdfUrl() != null && !prescription.getPdfUrl().isEmpty()) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(prescription.getPdfUrl())));
                }
            });

            // Set listener to navigate to the reminder creation activity
            ivNotify.setOnClickListener(v -> {
                if (canSetupReminders) {
                    Intent intent = new Intent(requireContext(), CreateRemindersActivity.class);
                    intent.putExtra("prescriptionId", prescription.getId());
                    intent.putExtra("drugName", prescription.getCompound());
                    intent.putExtra("posology", prescription.getPosology());
                    startActivity(intent);
                } else {
                    Toast.makeText(requireContext(), "Cannot set reminders for unidentified prescriptions.", Toast.LENGTH_SHORT).show();
                }
            });

            // Set listener to prompt for deletion
            ivRemove.setOnClickListener(v -> confirmRemovePrescription(prescription));

            // Add the fully constructed card to the linear layout container
            llListContainer.addView(card);
        }
    }

    /**
     * Shows a confirmation dialog warning the user before deleting a prescription.
     */
    private void confirmRemovePrescription(PrescriptionModel prescription) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Remove Prescription")
                .setMessage("Are you sure you want to permanently delete this PDF? Any active medication alarms will be canceled.")
                .setPositiveButton("Yes", (dialogInterface, which) -> removePrescription(prescription))
                .setNegativeButton("No", null)
                .create();

        // Customize the alert dialog appearance upon showing
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            Button btnYes = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnYes.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
            btnYes.setTypeface(null, Typeface.BOLD);
            Button btnNo = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            btnNo.setTextColor(Color.RED);
            btnNo.setTypeface(null, Typeface.BOLD);
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            TextView titleView = dialog.findViewById(titleId);
            if (titleView != null) titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) messageView.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
        });
        dialog.show();
    }

    /**
     * Executes the deletion process: cancels local alarms, deletes from Storage, then Firestore.
     */
    private void removePrescription(PrescriptionModel prescription) {
        // Cancel any pending notifications linked to this prescription
        cancelAlarmsFor(prescription);

        // Delete the associated PDF file from Firebase Storage
        if (prescription.getPdfUrl() != null && !prescription.getPdfUrl().isEmpty()) {
            try {
                StorageReference pdfRef = FirebaseStorage.getInstance().getReferenceFromUrl(prescription.getPdfUrl());
                pdfRef.delete().addOnCompleteListener(task -> deletePrescriptionDoc(prescription.getId()));
            } catch (Exception e) {
                // If parsing the URL fails, just delete the document from Firestore anyway
                deletePrescriptionDoc(prescription.getId());
            }
        } else {
            // Delete immediately if no URL is present
            deletePrescriptionDoc(prescription.getId());
        }
    }

    /**
     * Removes the prescription document from the Firestore database.
     */
    private void deletePrescriptionDoc(String prescriptionId) {
        firestore.collection("prescriptions").document(prescriptionId).delete()
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Prescription removed.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Failed to remove prescription.", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Iterates through saved request codes and cancels active alarms in the system AlarmManager.
     */
    private void cancelAlarmsFor(PrescriptionModel prescription) {
        List<Long> codes = prescription.getReminderRequestCodes();
        if (codes == null || codes.isEmpty()) return;

        android.app.AlarmManager alarmManager =
                (android.app.AlarmManager) requireContext().getSystemService(android.content.Context.ALARM_SERVICE);
        Intent intent = new Intent(requireContext(), com.example.carelink.ReminderReceiver.class);

        // Loop and reconstruct matching PendingIntents to cancel each one
        for (Long code : codes) {
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    requireContext(), code.intValue(), intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            if (alarmManager != null) alarmManager.cancel(pi);
        }
    }

    /**
     * Analyzes the contents of the chosen PDF in a background thread to automatically
     * extract medication details based on a predefined formatting template.
     */
    private void analyzePrescriptionPdf(Uri uri) {
        // Run file processing on a background thread to avoid freezing the UI
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Load PDF stream
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                PDDocument document = PDDocument.load(is);

                // Set up the text stripper to preserve vertical reading order
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String text = stripper.getText(document);
                document.close();

                // Tracking variables for extraction logic
                String drugName = "";
                String posology = "";
                boolean templateFound = false;
                boolean foundDrug = false;

                // Process the PDF text line by line
                String[] lines = text.split("\\r?\\n");
                for (String rawLine : lines) {
                    String cleanLine = rawLine.trim();

                    // Search for the anchor text that indicates where the prescription details begin
                    if (cleanLine.toLowerCase().contains("dci / nome, dosagem")) {
                        templateFound = true;
                        continue;
                    }

                    if (templateFound && !cleanLine.isEmpty()) {
                        // Skip unneeded table header lines
                        if (cleanLine.toLowerCase().contains("quant.") || cleanLine.toLowerCase().contains("validade")) continue;

                        // Identify the drug name via regex (e.g. number followed by capitalized word)
                        if (!foundDrug && cleanLine.matches("^\\d+\\s+[A-ZÁÉÍÓÚÂÊÎÔÛÃÕÇ].*")) {
                            drugName = cleanLine.replaceFirst("^\\d+\\s+", "").trim();
                            foundDrug = true;
                            continue;
                        }

                        // Capture the posology (dosage/directions) right after finding the drug name
                        if (foundDrug && posology.isEmpty()) {
                            // Avoid capturing boilerplate footer text as posology
                            if (cleanLine.toLowerCase().startsWith("esta prescrição custa-lhe")) {
                                continue;
                            }
                            posology = cleanLine;

                            // Truncate at the first period to keep instructions clean and brief
                            int periodIndex = posology.indexOf('.');
                            if (periodIndex != -1) {
                                posology = posology.substring(0, periodIndex + 1);
                            } else {
                                // Fallback truncation if multiple spaces are encountered
                                posology = posology.split(" {2,}")[0];
                            }
                            break; // Both drug and posology found, exit loop
                        }
                    }
                }

                // Store extracted values to state variables
                extractedDrugName = drugName;
                extractedPosology = posology;

                // Update UI back on the main thread after analysis is complete
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvPrescriptionStatus.setText("PDF Selected. Ready to save.");
                        btnConfirmUpload.setVisibility(View.VISIBLE);
                    });
                }
            } catch (Exception e) {
                // Handle parsing errors gracefully
                Log.e("PDF_ERROR", "Error parsing PDF", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvPrescriptionStatus.setText("PDF Selected. Ready to save.");
                        btnConfirmUpload.setVisibility(View.VISIBLE);
                    });
                }
            }
        });
    }

    /**
     * Uploads the PDF file to Firebase Storage, then stores a corresponding document
     * in Firestore with the metadata and extracted text.
     */
    private void uploadPrescriptionToFirebase() {
        if (selectedPdfUri == null) return;

        // Display a non-cancelable loading dialog
        AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Uploading Prescription")
                .setMessage("Please wait...")
                .setCancelable(false)
                .setView(new ProgressBar(requireContext()))
                .create();
        progressDialog.show();

        // Generate a new unique document ID for this prescription
        String prescriptionId = firestore.collection("prescriptions").document().getId();

        // Determine target path in Firebase Storage
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("prescriptions/" + currentUserId + "/" + prescriptionId + ".pdf");

        // Execute file upload
        storageRef.putFile(selectedPdfUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {

                    // On successful file upload, assemble the model for Firestore
                    PrescriptionModel newPrescription = new PrescriptionModel(
                            prescriptionId,
                            currentUserId,
                            uri.toString(),
                            extractedDrugName,
                            extractedPosology,
                            Timestamp.now()
                    );

                    // Save the model data to Firestore
                    firestore.collection("prescriptions").document(prescriptionId).set(newPrescription)
                            .addOnSuccessListener(aVoid -> {
                                progressDialog.dismiss();
                                Toast.makeText(requireContext(), "Prescription saved!", Toast.LENGTH_SHORT).show();

                                // FIX: Save strings before resetting the panel!
                                // Cache data locally because resetUploadPanel() will clear the globals
                                String finalDrug = extractedDrugName;
                                String finalPos = extractedPosology;

                                // Reset the UI to its original collapsed state
                                resetUploadPanel();

                                // Route user based on extraction success
                                if (!finalDrug.isEmpty() && !finalPos.isEmpty()) {
                                    promptForReminders(prescriptionId, finalDrug, finalPos);
                                } else {
                                    promptExtractionFailed();
                                }
                            })
                            .addOnFailureListener(err -> {
                                progressDialog.dismiss();
                                Toast.makeText(requireContext(), "Failed to save prescription.", Toast.LENGTH_SHORT).show();
                            });
                }))
                .addOnFailureListener(err -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Failed to upload file", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Clears cached upload data and returns the upload UI elements to their default states.
     */
    private void resetUploadPanel() {
        selectedPdfUri = null;
        extractedDrugName = "";
        extractedPosology = "";
        tvPrescriptionStatus.setText("Tap to select PDF");
        tvPrescriptionStatus.setTextColor(Color.BLACK);
        btnConfirmUpload.setVisibility(View.GONE);
        llUploadPanel.setVisibility(View.GONE);
        btnAddPrescription.setText("+ Add Prescription");
    }

    /**
     * Displays an alert dialog confirming what details were extracted and asks the user
     * if they want to proceed with setting up alarms for the medication.
     */
    private void promptForReminders(String prescriptionId, String drug, String posology) {
        String msg = "We detected the following details:<br><br>" +
                "Compound: <b>" + drug + "</b><br><br>" +
                "Posology: <b>" + posology + "</b><br><br>" +
                "Would you like to setup automated notifications?";

        // Handle HTML parsing differences across Android versions
        Spanned styledMessage;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            styledMessage = Html.fromHtml(msg, Html.FROM_HTML_MODE_LEGACY);
        } else {
            styledMessage = Html.fromHtml(msg);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Prescription Detected")
                .setMessage(styledMessage)
                .setPositiveButton("Yes", (d, w) -> {
                    // Navigate to reminder creation with pre-filled details
                    Intent intent = new Intent(requireContext(), CreateRemindersActivity.class);
                    intent.putExtra("prescriptionId", prescriptionId);
                    intent.putExtra("drugName", drug);
                    intent.putExtra("posology", posology);
                    startActivity(intent);
                })
                .setNegativeButton("No", null)
                .create();

        // Custom styling for the dialog elements
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            Button btnYes = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnYes.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
            btnYes.setTypeface(null, Typeface.BOLD);
            Button btnNo = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            btnNo.setTextColor(Color.RED);
            btnNo.setTypeface(null, Typeface.BOLD);
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            TextView titleView = dialog.findViewById(titleId);
            if (titleView != null) titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) messageView.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
        });
        dialog.show();
    }

    /**
     * Displays a dialog indicating that the PDF was uploaded successfully, but
     * no usable medication details were found inside it.
     */
    private void promptExtractionFailed() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Information Not Found")
                .setMessage("It wasn't possible to extract the prescription's information. You can still view the PDF, but automated reminders won't be available for it.")
                .setPositiveButton("I understand", null)
                .create();

        // Custom styling for the dialog elements
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            Button btnYes = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnYes.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
            btnYes.setTypeface(null, Typeface.BOLD);
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            TextView titleView = dialog.findViewById(titleId);
            if (titleView != null) titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) messageView.setTextColor(ContextCompat.getColor(requireContext(), R.color.buttons));
        });
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up the Firestore listener when the view is destroyed to prevent memory leaks
        if (prescriptionsListener != null) {
            prescriptionsListener.remove();
            prescriptionsListener = null;
        }
    }
}