package com.example.carelink.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.carelink.LoginActivity;
import com.example.carelink.R;
import com.example.carelink.SettingsActivity;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.AndroidUtil;
import com.example.carelink.util.FirebaseUtil;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fragment responsible for displaying and editing user profiles.
 * Supports two modes: Owner mode (editable) and Read-Only mode (viewing other users).
 */
public class ProfileFragment extends Fragment {

    private static final String PREF_NAME = "CareLinkPrefs";
    private static final String KEY_USER_ROLE = "user_role";

    // UI Components
    ImageView profilePic;
    EditText usernameInput, ageInput, etAddress, etOtherIllness;
    TextView emailInput, tvClickPic;
    Spinner spinnerHospital, spinnerGender, spinnerSpecialty;

    LinearLayout llActionButtons, btnSettings, btnLogout;

    TextView illnessesLabel, ageLabel, genderLabel, specialtyLabel, addressLabel;
    View illnessesContainer, ageContainer, genderContainer, specialtyContainer, addressContainer;

    CheckBox cbCancer, cbKidney, cbCardiac, cbHypertension, cbDiabetes, cbCholesterol, cbDementia, cbOther;
    Button updateProfileBtn;

    // Image selection handler
    ActivityResultLauncher<Intent> imagePickLauncher;
    Uri selectedImageUri;

    // State variables
    UserModel currentUserModel;
    boolean activation_changes = false;
    private AlertDialog progressDialog;
    private boolean isProfileChanged = false;
    private boolean isReadOnly = false;
    private String targetUserId;

    // Dropdown data mapping
    private String[] displayHospitals = {"Select your ULS", "ULS São João", "ULS Santa Maria", "ULS Coimbra"};
    private final String[] dbHospitals = {"", "Hospital São João", "Hospital Santa Maria", "Hospital Coimbra"};
    private String[] displayGenders = {"Select gender", "Male", "Female", "Other"};
    private String[] displaySpecialties = {"Select your specialty", "Cardiologist", "Anesthesiologist", "General Practitioner"};

    private final List<String> PREDEFINED_ILLNESSES = Arrays.asList(
            "Cancer", "Kidney failure", "Cardiac insufficiency",
            "Hypertension", "Diabetes", "High cholesterol", "Dementia"
    );

    public ProfileFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configure status bar styling
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        requireActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.bg_color));

        // Register the activity result launcher for the profile picture selection
        imagePickLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Intent data = result.getData();
                if (data != null && data.getData() != null) {
                    selectedImageUri = data.getData();
                    AndroidUtil.setProfilePic(getContext(), selectedImageUri, profilePic);
                    activation_changes = true;
                    checkForChanges();
                }
            }
        });

        // Initialize the blocking progress dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Loading").setMessage("Please wait...").setCancelable(false).setView(new ProgressBar(getContext()));
        progressDialog = builder.create();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Bind UI elements
        profilePic = view.findViewById(R.id.profile_image_view);
        tvClickPic = view.findViewById(R.id.tv_click_pic);
        llActionButtons = view.findViewById(R.id.ll_action_buttons);
        btnSettings = view.findViewById(R.id.btn_settings);
        btnLogout = view.findViewById(R.id.btn_logout);

        usernameInput = view.findViewById(R.id.username_profile);
        emailInput = view.findViewById(R.id.email_profile);
        spinnerHospital = view.findViewById(R.id.spinnerHospital);

        ageInput = view.findViewById(R.id.username_age);
        ageLabel = view.findViewById(R.id.age_label);
        ageContainer = view.findViewById(R.id.age_container);

        spinnerGender = view.findViewById(R.id.spinner_gender);
        genderLabel = view.findViewById(R.id.gender_label);
        genderContainer = view.findViewById(R.id.gender_container);

        etAddress = view.findViewById(R.id.et_address);
        addressLabel = view.findViewById(R.id.address_label);
        addressContainer = view.findViewById(R.id.address_container);

        etOtherIllness = view.findViewById(R.id.et_other_illness);
        illnessesLabel = view.findViewById(R.id.illnesses_label);
        illnessesContainer = view.findViewById(R.id.illnesses_container);

        spinnerSpecialty = view.findViewById(R.id.spinnerSpecialty);
        specialtyLabel = view.findViewById(R.id.specialty_label);
        specialtyContainer = view.findViewById(R.id.specialty_container);

        cbCancer = view.findViewById(R.id.cb_cancer);
        cbKidney = view.findViewById(R.id.cb_kidney);
        cbCardiac = view.findViewById(R.id.cb_cardiac);
        cbHypertension = view.findViewById(R.id.cb_hypertension);
        cbDiabetes = view.findViewById(R.id.cb_diabetes);
        cbCholesterol = view.findViewById(R.id.cb_cholesterol);
        cbDementia = view.findViewById(R.id.cb_dementia);
        cbOther = view.findViewById(R.id.cb_other);

        updateProfileBtn = view.findViewById(R.id.update_profile_button);

        // Determine if fragment is viewing current user or another user
        isReadOnly = false;
        targetUserId = FirebaseUtil.currentUserId();

        if (requireActivity().getIntent() != null && requireActivity().getIntent().hasExtra("userIdToView")) {
            String extraId = requireActivity().getIntent().getStringExtra("userIdToView");
            if (extraId != null && !extraId.isEmpty()) {
                targetUserId = extraId;
                isReadOnly = true;
            }
            // Consume the intent extra
            requireActivity().getIntent().removeExtra("userIdToView");
        }

        // Handle hardware back press routing
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isReadOnly) requireActivity().finish();
                else requireActivity().finishAffinity();
            }
        });

        // Apply UI state based on read/write permission
        if (isReadOnly) {
            // Disable editing features for read-only view
            tvClickPic.setVisibility(View.INVISIBLE);
            profilePic.setEnabled(false);
            updateProfileBtn.setVisibility(View.GONE);
            llActionButtons.setVisibility(View.GONE);

            usernameInput.setEnabled(false);
            ageInput.setEnabled(false);
            etAddress.setEnabled(false);
            spinnerGender.setEnabled(false);
            spinnerHospital.setEnabled(false);
            spinnerSpecialty.setEnabled(false);
            cbCancer.setEnabled(false);
            cbKidney.setEnabled(false);
            cbCardiac.setEnabled(false);
            cbHypertension.setEnabled(false);
            cbDiabetes.setEnabled(false);
            cbCholesterol.setEnabled(false);
            cbDementia.setEnabled(false);
            cbOther.setEnabled(false);
            etOtherIllness.setEnabled(false);

            usernameInput.setHint("");
            ageInput.setHint("");
            etAddress.setHint("");
            etOtherIllness.setHint("");
            displayHospitals[0] = "";
            displayGenders[0] = "";
            displaySpecialties[0] = "";

        } else {
            // Enable editing features for owner view
            tvClickPic.setVisibility(View.VISIBLE);
            profilePic.setEnabled(true);
            updateProfileBtn.setVisibility(View.VISIBLE);
            llActionButtons.setVisibility(View.VISIBLE);

            btnSettings.setOnClickListener(v -> startActivity(new Intent(getActivity(), SettingsActivity.class)));

            // Logout sequence
            btnLogout.setOnClickListener(v -> {
                if (!isAdded() || getActivity() == null || getContext() == null) return;
                btnLogout.setEnabled(false);

                Context safeContext = getContext();
                Activity safeActivity = getActivity();

                // Clear cached user role
                SharedPreferences prefs = safeContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit().remove(KEY_USER_ROLE).apply();

                // Clear biometric session flag to require authentication on next launch
                safeContext.getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(LoginActivity.KEY_UNLOCKED, false).apply();

                // Remove device token from Firestore to stop receiving targeted push notifications
                DocumentReference currentUserRef = FirebaseUtil.currentUserDetails();
                if (currentUserRef != null) {
                    try { currentUserRef.update("fcmToken", ""); }
                    catch (Exception e) { Log.e("ProfileFragment", "Caught FCM token wipe error", e); }
                }

                // Execute Firebase sign out and return to LoginActivity
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(safeContext, "You have signed out successfully.", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(safeActivity, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    safeActivity.finish();
                }, 500);
            });

            usernameInput.setEnabled(true);
            ageInput.setEnabled(true);
            etAddress.setEnabled(true);
            spinnerGender.setEnabled(true);
            spinnerHospital.setEnabled(true);
            spinnerSpecialty.setEnabled(true);
            cbCancer.setEnabled(true);
            cbKidney.setEnabled(true);
            cbCardiac.setEnabled(true);
            cbHypertension.setEnabled(true);
            cbDiabetes.setEnabled(true);
            cbCholesterol.setEnabled(true);
            cbDementia.setEnabled(true);
            cbOther.setEnabled(true);

            usernameInput.setHint("Enter your username");
            ageInput.setHint("Enter your age");
            etAddress.setHint("Enter your address");
            etOtherIllness.setHint("Separate each illness by a comma");
            displayHospitals[0] = "Select your hospital";
            displayGenders[0] = "Select gender";
            displaySpecialties[0] = "Select your specialty";

            // Setup profile picture picker
            profilePic.setOnClickListener(v -> {
                ImagePicker.with(this).cropSquare().compress(512).maxResultSize(512, 512)
                        .createIntent(intent -> { imagePickLauncher.launch(intent); return null; });
            });

            updateProfileBtn.setOnClickListener(v -> updateBtnClick());
        }

        // Initialize Hospital Spinner Adapter with styling
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, displayHospitals) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v; tv.setTextSize(16f);
                if (position == 0) tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                else tv.setTextColor(getResources().getColor(R.color.buttons));
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) v; v.setBackgroundColor(getResources().getColor(android.R.color.white));
                if (position == 0) tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                else tv.setTextColor(getResources().getColor(R.color.buttons));
                return v;
            }
        };
        spinnerHospital.setAdapter(spinnerAdapter);
        spinnerHospital.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if(!isReadOnly) checkForChanges(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Initialize Gender Spinner Adapter with styling
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, displayGenders) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v; tv.setTextSize(16f);
                if (position == 0) tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                else tv.setTextColor(getResources().getColor(R.color.buttons));
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) v; v.setBackgroundColor(getResources().getColor(android.R.color.white));
                if (position == 0) tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                else tv.setTextColor(getResources().getColor(R.color.buttons));
                return v;
            }
        };
        spinnerGender.setAdapter(genderAdapter);
        spinnerGender.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if(!isReadOnly) checkForChanges(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Lock bottom navigation while fetching data
        setBottomNavClickable(false);
        progressDialog.show();
        getUserData();

        // Setup dynamic listeners for edit mode to track unsaved changes
        if (!isReadOnly) {
            updateProfileBtn.setEnabled(false);
            updateProfileBtn.setAlpha(0.5f);

            TextWatcher changeWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (etOtherIllness.getText().toString().trim().isEmpty()) etOtherIllness.setTextSize(12);
                    else etOtherIllness.setTextSize(13);
                    checkForChanges();
                }
                @Override public void afterTextChanged(Editable s) {}
            };

            usernameInput.addTextChangedListener(changeWatcher);
            ageInput.addTextChangedListener(changeWatcher);
            etAddress.addTextChangedListener(changeWatcher);
            etOtherIllness.addTextChangedListener(changeWatcher);

            View.OnClickListener checkboxListener = v -> checkForChanges();
            cbCancer.setOnClickListener(checkboxListener);
            cbKidney.setOnClickListener(checkboxListener);
            cbCardiac.setOnClickListener(checkboxListener);
            cbHypertension.setOnClickListener(checkboxListener);
            cbDiabetes.setOnClickListener(checkboxListener);
            cbCholesterol.setOnClickListener(checkboxListener);
            cbDementia.setOnClickListener(checkboxListener);

            cbOther.setOnCheckedChangeListener((buttonView, isChecked) -> {
                etOtherIllness.setEnabled(isChecked);
                if (!isChecked) { etOtherIllness.setText(""); etOtherIllness.setError(null); }
                checkForChanges();
            });
        }
        return view;
    }

    /**
     * Toggles interaction state of the application's bottom navigation bar.
     */
    private void setBottomNavClickable(boolean isClickable) {
        if (getActivity() == null) return;
        try {
            View bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
            if (bottomNav != null) bottomNav.setOnTouchListener(isClickable ? null : (v, event) -> true);
        } catch (Exception e) { Log.e("ProfileFragment", "Could not lock bottom nav", e); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setBottomNavClickable(true);
    }

    /**
     * Parses the current state of the illness checkboxes and custom input text field.
     */
    private List<String> getCurrentIllnessSelection() {
        List<String> illnesses = new ArrayList<>();
        if (cbCancer.isChecked()) illnesses.add("Cancer");
        if (cbKidney.isChecked()) illnesses.add("Kidney failure");
        if (cbCardiac.isChecked()) illnesses.add("Cardiac insufficiency");
        if (cbHypertension.isChecked()) illnesses.add("Hypertension");
        if (cbDiabetes.isChecked()) illnesses.add("Diabetes");
        if (cbCholesterol.isChecked()) illnesses.add("High cholesterol");
        if (cbDementia.isChecked()) illnesses.add("Dementia");

        if (cbOther.isChecked()) {
            String otherText = etOtherIllness.getText().toString().trim();
            if (!otherText.isEmpty()) {
                String[] customIllnesses = otherText.split(",");
                for (String illness : customIllnesses) {
                    String trimmedIllness = illness.trim();
                    if (!trimmedIllness.isEmpty()) illnesses.add(trimmedIllness);
                }
            }
        }
        return illnesses;
    }

    /**
     * Compares current UI values against the loaded Firestore model to enable or disable the Update button.
     */
    void checkForChanges() {
        if (currentUserModel == null || isReadOnly) return;

        boolean usernameChanged = !usernameInput.getText().toString().equals(currentUserModel.getUsername());
        boolean imageChanged = selectedImageUri != null && activation_changes;

        int selectedHospitalIndex = spinnerHospital.getSelectedItemPosition();
        String currentSelectedHospital = selectedHospitalIndex > 0 ? dbHospitals[selectedHospitalIndex] : "";
        String dbHospital = currentUserModel.getHospital() != null ? currentUserModel.getHospital() : "";
        boolean hospitalChanged = !currentSelectedHospital.equals(dbHospital);

        boolean ageChanged = false;
        boolean genderChanged = false;
        boolean illnessesChanged = false;
        boolean addressChanged = false;

        // Caregivers/Doctors do not possess patient-specific fields
        if (!"Caregiver".equalsIgnoreCase(currentUserModel.getRole()) && !"Doctor".equalsIgnoreCase(currentUserModel.getRole())) {
            String ageStr = ageInput.getText().toString().trim();
            if (ageStr.isEmpty()) ageChanged = (currentUserModel.getAge() != 0);
            else {
                try { ageChanged = Integer.parseInt(ageStr) != currentUserModel.getAge(); }
                catch (NumberFormatException e) { ageChanged = true; }
            }

            int selectedGenderIndex = spinnerGender.getSelectedItemPosition();
            String currentSelectedGender = selectedGenderIndex > 0 ? displayGenders[selectedGenderIndex] : "";
            String dbGender = currentUserModel.getGender() != null ? currentUserModel.getGender() : "";
            genderChanged = !currentSelectedGender.equals(dbGender);

            addressChanged = !etAddress.getText().toString().trim().equals(currentUserModel.getAddress());

            List<String> currentIllnesses = getCurrentIllnessSelection();
            illnessesChanged = !currentIllnesses.equals(currentUserModel.getDiagnosedIllnesses());
        }

        isProfileChanged = usernameChanged || imageChanged || ageChanged || genderChanged || hospitalChanged || addressChanged || illnessesChanged;

        boolean isHospitalValid = selectedHospitalIndex > 0;
        boolean isUsernameValid = !usernameInput.getText().toString().trim().isEmpty();

        boolean canUpdate = isProfileChanged && isHospitalValid && isUsernameValid;

        updateProfileBtn.setEnabled(canUpdate);
        updateProfileBtn.setAlpha(canUpdate ? 1.0f : 0.5f);

        if (isHospitalValid) {
            View selectedView = spinnerHospital.getSelectedView();
            if (selectedView instanceof TextView) ((TextView) selectedView).setError(null);
        }
    }

    /**
     * Handles the data validation and compilation before pushing updates to Firestore.
     */
    void updateBtnClick() {
        if (!isProfileChanged || isReadOnly) return;

        String newUsername = usernameInput.getText().toString();
        int selectedHospitalIndex = spinnerHospital.getSelectedItemPosition();

        // Ensure hospital validation
        if (selectedHospitalIndex == 0) {
            View selectedView = spinnerHospital.getSelectedView();
            if (selectedView instanceof TextView) ((TextView) selectedView).setError("Select a valid hospital");
            return;
        }
        String newHospital = dbHospitals[selectedHospitalIndex];

        if (newUsername.isEmpty()) { usernameInput.setError("Type a valid username"); return; }

        setBottomNavClickable(false);
        progressDialog.show();

        currentUserModel.setUsername(newUsername);
        currentUserModel.setHospital(newHospital);

        // Validate and apply patient-specific fields
        if (!"Caregiver".equalsIgnoreCase(currentUserModel.getRole()) && !"Doctor".equalsIgnoreCase(currentUserModel.getRole())) {
            String newAgeStr = ageInput.getText().toString().trim();
            int newAge = 0;
            if (!newAgeStr.isEmpty()) {
                if (!newAgeStr.matches("\\d+") || Integer.parseInt(newAgeStr) > 110 || Integer.parseInt(newAgeStr) < 0) {
                    ageInput.setError("Type a valid age");
                    progressDialog.dismiss(); setBottomNavClickable(true); return;
                }
                newAge = Integer.parseInt(newAgeStr);
            }
            currentUserModel.setAge(newAge);

            String newAddress = etAddress.getText().toString().trim();
            if (newAddress.isEmpty()) {
                etAddress.setError("Provide an address");
                progressDialog.dismiss(); setBottomNavClickable(true); return;
            }
            currentUserModel.setAddress(newAddress);

            int selectedGenderIndex = spinnerGender.getSelectedItemPosition();
            if (selectedGenderIndex > 0) currentUserModel.setGender(displayGenders[selectedGenderIndex]);
            else currentUserModel.setGender("");

            if (cbOther.isChecked() && etOtherIllness.getText().toString().trim().isEmpty()) {
                etOtherIllness.setError("Please specify the illness");
                progressDialog.dismiss(); setBottomNavClickable(true); return;
            }

            currentUserModel.setDiagnosedIllnesses(getCurrentIllnessSelection());
        }

        // Upload new image to storage if selected, otherwise proceed directly to document update
        if (selectedImageUri != null) {
            FirebaseUtil.getCurrentProfilePicStorageRef().putFile(selectedImageUri)
                    .addOnCompleteListener(task -> updateToFirestore());
        } else {
            updateToFirestore();
        }
    }

    /**
     * Executes the final Firestore set operation with the modified user model.
     */
    void updateToFirestore() {
        FirebaseUtil.currentUserDetails().set(currentUserModel)
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getContext() == null) return;
                    progressDialog.dismiss();
                    setBottomNavClickable(true);

                    if (task.isSuccessful()) {
                        activation_changes = false;
                        Toast.makeText(getContext(),"Updated Successfully!", Toast.LENGTH_LONG).show();
                        updateProfileBtn.setEnabled(false);
                        updateProfileBtn.setAlpha(0.5f);
                    } else {
                        Toast.makeText(getContext(),"Update failed!", Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Fetches user document and profile image from Firestore/Storage and populates the UI.
     */
    void getUserData() {
        // Fetch profile picture async
        FirebaseUtil.getOtherProfilePicStorageRef(targetUserId).getDownloadUrl()
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getContext() == null) return;
                    if (task.isSuccessful()) {
                        Uri uri = task.getResult();
                        AndroidUtil.setProfilePic(getContext(), uri, profilePic);
                    }
                });

        // Fetch document async
        FirebaseFirestore.getInstance().collection("users").document(targetUserId).get().addOnCompleteListener(task -> {
            if (!isAdded() || getContext() == null) {
                if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
                setBottomNavClickable(true);
                return;
            }

            currentUserModel = task.getResult().toObject(UserModel.class);

            if (currentUserModel != null) {
                usernameInput.setText(currentUserModel.getUsername());
                emailInput.setText(currentUserModel.getEmail());

                String userHospital = currentUserModel.getHospital();
                if (userHospital != null) {
                    for (int i = 0; i < dbHospitals.length; i++) {
                        if (dbHospitals[i].equals(userHospital)) { spinnerHospital.setSelection(i); break; }
                    }
                }

                // Adjust UI visibility based on retrieved role
                if ("Caregiver".equalsIgnoreCase(currentUserModel.getRole()) || "Doctor".equalsIgnoreCase(currentUserModel.getRole())) {
                    specialtyLabel.setVisibility(View.GONE); specialtyContainer.setVisibility(View.GONE);
                    ageLabel.setVisibility(View.GONE); ageContainer.setVisibility(View.GONE);
                    genderLabel.setVisibility(View.GONE); genderContainer.setVisibility(View.GONE);
                    addressLabel.setVisibility(View.GONE); addressContainer.setVisibility(View.GONE);
                    illnessesLabel.setVisibility(View.GONE); illnessesContainer.setVisibility(View.GONE);
                } else {
                    // Patient data population
                    specialtyLabel.setVisibility(View.GONE); specialtyContainer.setVisibility(View.GONE);

                    ageLabel.setVisibility(View.VISIBLE); ageContainer.setVisibility(View.VISIBLE);
                    addressLabel.setVisibility(View.VISIBLE); addressContainer.setVisibility(View.VISIBLE);
                    genderLabel.setVisibility(View.VISIBLE); genderContainer.setVisibility(View.VISIBLE);
                    illnessesLabel.setVisibility(View.VISIBLE); illnessesContainer.setVisibility(View.VISIBLE);

                    if (currentUserModel.getAge() > 0) ageInput.setText(String.valueOf(currentUserModel.getAge()));
                    if (currentUserModel.getAddress() != null) etAddress.setText(currentUserModel.getAddress());

                    String userGender = currentUserModel.getGender();
                    if (userGender != null) {
                        for (int i = 0; i < displayGenders.length; i++) {
                            if (displayGenders[i].equals(userGender)) { spinnerGender.setSelection(i); break; }
                        }
                    }

                    List<String> illnesses = currentUserModel.getDiagnosedIllnesses();
                    if (illnesses != null) {
                        cbCancer.setChecked(illnesses.contains("Cancer"));
                        cbKidney.setChecked(illnesses.contains("Kidney failure"));
                        cbCardiac.setChecked(illnesses.contains("Cardiac insufficiency"));
                        cbHypertension.setChecked(illnesses.contains("Hypertension"));
                        cbDiabetes.setChecked(illnesses.contains("Diabetes"));
                        cbCholesterol.setChecked(illnesses.contains("High cholesterol"));
                        cbDementia.setChecked(illnesses.contains("Dementia"));

                        // Extract custom illnesses not matching predefined keys
                        List<String> otherIllnessesList = new ArrayList<>();
                        for (String illness : illnesses) {
                            if (!PREDEFINED_ILLNESSES.contains(illness)) otherIllnessesList.add(illness);
                        }

                        if (!otherIllnessesList.isEmpty()) {
                            cbOther.setChecked(true);
                            etOtherIllness.setText(String.join(", ", otherIllnessesList));
                            if (!isReadOnly) etOtherIllness.setEnabled(true);
                        }
                    }
                }
                checkForChanges();
            }
            progressDialog.dismiss();
            setBottomNavClickable(true);
        });
    }
}