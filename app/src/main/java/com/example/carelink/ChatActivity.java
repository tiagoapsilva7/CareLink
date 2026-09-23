package com.example.carelink;

import static android.view.View.GONE;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.example.carelink.adapter.ChatRecyclerAdapter;
import com.example.carelink.model.ChatMessageModel;
import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.AndroidUtil;
import com.example.carelink.util.FirebaseUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;

public class ChatActivity extends AppCompatActivity {

    // UI Components
    EditText message_input;
    ImageButton message_send_button;
    TextView username_text;
    RecyclerView recyclerview;
    ImageView imageView;
    ImageButton optionsMenuBtn;

    // Connection state layouts
    LinearLayout end_connection_confirm_layout;
    Button btn_cancel_end, btn_confirm_end;
    View bottom_layout;
    LinearLayout request_layout;
    LinearLayout request_buttons_container;
    TextView request_message_tv;
    Button btn_send_request, btn_accept_request, btn_reject_request;

    // Data models and variables
    ChatroomModel chatroomModel;
    UserModel otherUser;
    public String chatroomId;
    public String otherUserId;
    public String otherUserUsername;
    public String fcmToken;
    public String myUsername;
    private String myRole = "";
    private String myFcmToken = "";

    private AlertDialog progressDialog;
    ChatRecyclerAdapter adapter;
    private StorageReference storageReference;

    // Vestigial variables for media (kept to avoid breaking undeclared dependencies)
    private static final int REQUEST_PERMISSION_CODE = 1001;
    private MediaRecorder mediaRecorder;
    private Uri audioUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        storageReference = FirebaseStorage.getInstance().getReference();

        // Validate intent payload
        if (getIntent() == null) {
            finish();
            return;
        }

        // Bind views
        message_input = findViewById(R.id.message_input);
        message_send_button = findViewById(R.id.message_send_button);
        username_text = findViewById(R.id.username_text);
        recyclerview = findViewById(R.id.recyclerview_chat);
        imageView = findViewById(R.id.profile_pic_image_view);
        optionsMenuBtn = findViewById(R.id.options_menu_button);

        end_connection_confirm_layout = findViewById(R.id.end_connection_confirm_layout);
        btn_cancel_end = findViewById(R.id.btn_cancel_end);
        btn_confirm_end = findViewById(R.id.btn_confirm_end);

        bottom_layout = findViewById(R.id.bottom_layout);
        request_layout = findViewById(R.id.request_layout);
        request_buttons_container = findViewById(R.id.request_buttons_container);
        request_message_tv = findViewById(R.id.request_message_tv);
        btn_send_request = findViewById(R.id.btn_send_request);
        btn_accept_request = findViewById(R.id.btn_accept_request);
        btn_reject_request = findViewById(R.id.btn_reject_request);

        // Setup generic loading dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Loading").setMessage("Please wait...").setCancelable(false).setView(new ProgressBar(this));
        progressDialog = builder.create();

        // Extract target user metadata from intent
        otherUser = AndroidUtil.getUserModelFromIntent(getIntent());
        if (otherUser == null) {
            finish();
            return;
        }

        // Initialize chatroom identifiers
        chatroomId = getIntent().getStringExtra("chatroomId");
        if (chatroomId == null) {
            chatroomId = FirebaseUtil.getChatroomId(FirebaseUtil.currentUserId(), otherUser.getUserId());
        }

        otherUserId = getIntent().getStringExtra("otherUserId");
        if (otherUserId == null) {
            otherUserId = otherUser.getUserId();
        }

        otherUserUsername = getIntent().getStringExtra("otherUserUsername");
        if (otherUserUsername == null) {
            otherUserUsername = otherUser.getUsername();
        }

        fcmToken = getIntent().getStringExtra("fcmToken");
        if (fcmToken == null) {
            fcmToken = otherUser.getFcmToken();
        }

        // Fetch current user details to format UI correctly
        FirebaseUtil.currentUserDetails().get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                myUsername = doc.getString("username");
                myRole = doc.getString("role");
                myFcmToken = doc.getString("fcmToken");

                if (myRole != null && myRole.trim().equalsIgnoreCase("Patient")) {
                    username_text.setText("Dr. " + otherUserUsername);
                } else {
                    username_text.setText(otherUserUsername);
                }
                updateChatUI();
            }
        });

        // Initialize popup menu for profile viewing and connection management
        optionsMenuBtn.setOnClickListener(v -> {
            ContextThemeWrapper wrapper = new ContextThemeWrapper(ChatActivity.this, R.style.PopupMenuLight);
            PopupMenu popupMenu = new PopupMenu(wrapper, v);

            if (myRole != null && myRole.trim().equalsIgnoreCase("Doctor")) {
                SpannableString viewProfileText = new SpannableString("View Profile");
                viewProfileText.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ChatActivity.this, R.color.buttons)), 0, viewProfileText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                viewProfileText.setSpan(new StyleSpan(Typeface.BOLD), 0, viewProfileText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                popupMenu.getMenu().add(0, 1, 0, viewProfileText);

                SpannableString checkEvoText = new SpannableString("Check Evolution");
                checkEvoText.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ChatActivity.this, R.color.buttons)), 0, checkEvoText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                checkEvoText.setSpan(new StyleSpan(Typeface.BOLD), 0, checkEvoText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                popupMenu.getMenu().add(0, 3, 1, checkEvoText);
            }

            SpannableString endConnectionText = new SpannableString("End Connection");
            endConnectionText.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ChatActivity.this, android.R.color.holo_red_light)), 0, endConnectionText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            endConnectionText.setSpan(new StyleSpan(Typeface.BOLD), 0, endConnectionText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            popupMenu.getMenu().add(0, 2, 2, endConnectionText);

            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    Intent intent = new Intent(ChatActivity.this, MainActivity.class);
                    intent.putExtra("targetFragment", "ProfileFragment");
                    intent.putExtra("userIdToView", otherUserId);
                    startActivity(intent);
                    return true;
                } else if (item.getItemId() == 2) {
                    bottom_layout.setVisibility(GONE);
                    end_connection_confirm_layout.setVisibility(View.VISIBLE);
                    recyclerview.setAlpha(0.3f);
                    optionsMenuBtn.setVisibility(GONE);
                    return true;
                } else if (item.getItemId() == 3) {
                    // Logic for checking evolution goes here
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });

        // Connection termination confirmation handlers
        btn_cancel_end.setOnClickListener(v -> {
            end_connection_confirm_layout.setVisibility(GONE);
            bottom_layout.setVisibility(View.VISIBLE);
            recyclerview.setAlpha(1.0f);
            optionsMenuBtn.setVisibility(View.VISIBLE);
        });

        btn_confirm_end.setOnClickListener(v -> {
            progressDialog.show();
            // Delete all subcollection messages before deleting the chatroom document
            FirebaseUtil.getChatroomMessageReference(chatroomId).get().addOnSuccessListener(queryDocumentSnapshots -> {
                WriteBatch batch = FirebaseFirestore.getInstance().batch();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    batch.delete(doc.getReference());
                }
                batch.commit().addOnSuccessListener(aVoid -> {
                    FirebaseUtil.getChatroomReference(chatroomId).delete().addOnCompleteListener(task -> {
                        progressDialog.dismiss();
                        Toast.makeText(ChatActivity.this, "Connection Ended", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(ChatActivity.this, MainActivity.class);
                        intent.putExtra("targetFragment", "ChatFragment");
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    });
                }).addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(ChatActivity.this, "Error ending connection", Toast.LENGTH_SHORT).show();
                });
            });
        });

        // Chat request handlers
        btn_send_request.setOnClickListener(v -> {
            chatroomModel.setStatus("pending");
            chatroomModel.setRequestSenderId(FirebaseUtil.currentUserId());
            chatroomModel.setLastMessageTimestamp(Timestamp.now());
            chatroomModel.setLastMessage("Chat Request Sent");

            Map<String, Long> unreadMap = new HashMap<>();
            unreadMap.put(otherUserId, 1L);
            unreadMap.put(FirebaseUtil.currentUserId(), 0L);
            chatroomModel.setUnreadMessageCount(unreadMap);

            FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel).addOnSuccessListener(aVoid -> {
                updateChatUI();
                sendNotification(otherUser.getFcmToken(), myUsername, "New Chat Request!");
            });
        });

        btn_accept_request.setOnClickListener(v -> {
            String acceptedMessage = "Friend request accepted. You can now talk with this user!";

            Map<String, Long> unreadMap = new HashMap<>();
            unreadMap.put(otherUserId, 1L);
            unreadMap.put(FirebaseUtil.currentUserId(), 0L);

            FirebaseUtil.getChatroomReference(chatroomId).update(
                    "status", "accepted",
                    "lastMessage", acceptedMessage,
                    "lastMessageTimestamp", Timestamp.now(),
                    "lastMessageSenderId", FirebaseUtil.currentUserId(),
                    "unreadMessageCount", unreadMap
            ).addOnSuccessListener(aVoid -> {
                chatroomModel.setStatus("accepted");
                chatroomModel.setLastMessage(acceptedMessage);
                chatroomModel.setLastMessageTimestamp(Timestamp.now());
                chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
                chatroomModel.setUnreadMessageCount(unreadMap);
                updateChatUI();
            });
        });

        btn_reject_request.setOnClickListener(v -> {
            progressDialog.show();
            FirebaseUtil.getChatroomMessageReference(chatroomId).get().addOnSuccessListener(queryDocumentSnapshots -> {
                WriteBatch batch = FirebaseFirestore.getInstance().batch();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    batch.delete(doc.getReference());
                }
                batch.commit().addOnSuccessListener(aVoid -> {
                    FirebaseUtil.getChatroomReference(chatroomId).delete().addOnCompleteListener(task -> {
                        progressDialog.dismiss();
                        finish();
                    });
                });
            });
        });

        // Load profile picture
        FirebaseUtil.getOtherProfilePicStorageRef(otherUserId).getDownloadUrl()
                .addOnCompleteListener(t -> {
                    if (t.isSuccessful()) {
                        Uri uri = t.getResult();
                        AndroidUtil.setProfilePic(this, uri, imageView);
                    }
                });

        // Handle back button routing to ensure proper fragment resumption
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (end_connection_confirm_layout.getVisibility() == View.VISIBLE) {
                    btn_cancel_end.performClick();
                } else {
                    boolean fromNotification = getIntent().getBooleanExtra("fromNotification", false);
                    if (fromNotification || isTaskRoot()) {
                        Intent intent = new Intent(ChatActivity.this, MainActivity.class);
                        intent.putExtra("targetFragment", "ChatFragment");
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                    }
                    finish();
                }
            }
        });

        getOrCreateChatroomModel();
        setupChatRecyclerView();

        message_send_button.setOnClickListener(v -> {
            String message = message_input.getText().toString().trim();
            if (message.isEmpty()) return;
            sendMessageToUser(message);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.users_chat), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });
    }

    // Fetches the chatroom from Firestore or creates a default document if it doesn't exist yet
    public void getOrCreateChatroomModel() {
        FirebaseUtil.getChatroomReference(chatroomId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                chatroomModel = task.getResult().toObject(ChatroomModel.class);

                if (chatroomModel == null || !task.getResult().exists()) {
                    Map<String, Long> initialUnreadMap = new HashMap<>();
                    initialUnreadMap.put(FirebaseUtil.currentUserId(), 0L);
                    initialUnreadMap.put(otherUserId, 0L);

                    chatroomModel = new ChatroomModel(
                            chatroomId,
                            Arrays.asList(FirebaseUtil.currentUserId(), otherUserId),
                            null,
                            "",
                            "",
                            initialUnreadMap,
                            "none",
                            ""
                    );
                    updateChatUI();
                } else {
                    FirebaseUtil.getChatroomReference(chatroomId).update(
                            "unreadMessageCount." + FirebaseUtil.currentUserId(), 0L
                    ).addOnFailureListener(e -> {
                        Map<String, Long> unreadMap = new HashMap<>();
                        unreadMap.put(FirebaseUtil.currentUserId(), 0L);
                        unreadMap.put(otherUserId, 0L);
                        FirebaseUtil.getChatroomReference(chatroomId).update("unreadMessageCount", unreadMap);
                    });

                    updateChatUI();
                }
            } else {
                Log.e("ChatActivity", "Error getting chatroom: " + task.getException());
            }
        });
    }

    // Adjusts visibility of UI elements based on the current connection state
    private void updateChatUI() {
        if (chatroomModel == null) return;

        String status = chatroomModel.getStatus() != null ? chatroomModel.getStatus() : "accepted";
        String displayOtherName = (myRole != null && myRole.trim().equalsIgnoreCase("Patient")) ? "Dr. " + otherUserUsername : otherUserUsername;

        optionsMenuBtn.setVisibility(GONE);

        if ("accepted".equals(status)) {
            request_layout.setVisibility(GONE);
            end_connection_confirm_layout.setVisibility(GONE);
            bottom_layout.setVisibility(View.VISIBLE);

            optionsMenuBtn.setVisibility(View.VISIBLE);

        } else if ("none".equals(status)) {
            bottom_layout.setVisibility(GONE);
            request_layout.setVisibility(View.VISIBLE);
            request_message_tv.setText("You must request acceptance from " + displayOtherName + " in order to exchange messages with them.");
            btn_send_request.setVisibility(View.VISIBLE);
            request_buttons_container.setVisibility(GONE);
        } else if ("pending".equals(status)) {
            bottom_layout.setVisibility(GONE);
            request_layout.setVisibility(View.VISIBLE);

            if (FirebaseUtil.currentUserId().equals(chatroomModel.getRequestSenderId())) {
                request_message_tv.setText("Request sent! Waiting for " + displayOtherName + " to accept.");
                btn_send_request.setVisibility(GONE);
                request_buttons_container.setVisibility(GONE);
            } else {
                request_message_tv.setText(displayOtherName + " wants to exchange messages with you.");
                btn_send_request.setVisibility(GONE);
                request_buttons_container.setVisibility(View.VISIBLE);
            }
        }
    }

    // Updates chatroom metadata and appends the message object
    public void sendMessageToUser(String message) {
        if (chatroomModel != null) {
            chatroomModel.setLastMessageTimestamp(Timestamp.now());
            chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
            chatroomModel.setLastMessage(message);
        }

        FirebaseUtil.getChatroomReference(chatroomId).update(
                "lastMessageTimestamp", Timestamp.now(),
                "lastMessageSenderId", FirebaseUtil.currentUserId(),
                "lastMessage", message,
                "unreadMessageCount." + otherUserId, FieldValue.increment(1),
                "unreadMessageCount." + FirebaseUtil.currentUserId(), 0L
        ).addOnFailureListener(e -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastMessageTimestamp", Timestamp.now());
            updates.put("lastMessageSenderId", FirebaseUtil.currentUserId());
            updates.put("lastMessage", message);

            Map<String, Long> newUnreadMap = new HashMap<>();
            newUnreadMap.put(otherUserId, 1L);
            newUnreadMap.put(FirebaseUtil.currentUserId(), 0L);
            updates.put("unreadMessageCount", newUnreadMap);

            FirebaseUtil.getChatroomReference(chatroomId).update(updates);
        });

        ChatMessageModel chatMessageModel = new ChatMessageModel(message, FirebaseUtil.currentUserId(), Timestamp.now());
        FirebaseUtil.getChatroomMessageReference(chatroomId).add(chatMessageModel).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                message_input.setText("");
                if (AndroidUtil.isLink(message) && !AndroidUtil.containsMediaTerm(message).isEmpty()) {
                    String message_multimedia = myUsername + " sent you a" + AndroidUtil.grammar(AndroidUtil.containsMediaTerm(message)) + AndroidUtil.containsMediaTerm(message);
                    sendNotification(fcmToken, myUsername, message_multimedia);
                } else {
                    sendNotification(fcmToken, myUsername, message);
                }
            }
        });
    }

    // Connects the recycler view to the Firestore query adapter
    void setupChatRecyclerView() {
        Query query = FirebaseUtil.getChatroomMessageReference(chatroomId).orderBy("timestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<ChatMessageModel> options = new FirestoreRecyclerOptions.Builder<ChatMessageModel>()
                .setQuery(query, ChatMessageModel.class).build();

        adapter = new ChatRecyclerAdapter(options, getApplicationContext(), chatroomId, otherUserId, otherUserUsername, myUsername, fcmToken);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setReverseLayout(true);
        recyclerview.setLayoutManager(manager);
        recyclerview.setAdapter(adapter);
        adapter.startListening();

        // Auto-scroll to the bottom when new messages arrive
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                recyclerview.smoothScrollToPosition(0);
            }
        });
    }

    /**
     * Sends a push notification to a single device via the FCM v1 REST API.
     *
     * Runs on a background executor because it performs two blocking network
     * calls: one to exchange the service-account key for a short-lived OAuth2
     * access token, and one to POST the message itself. Silently returns when
     * the target device token is missing.
     *
     * SECURITY: the service-account key is read from assets/, which means it is
     * packaged into the APK and can be extracted from any installed build,
     * which effectively makes it public. It is git-ignored (see README,
     * "Firebase setup"), but the only real fix is to move this call into a
     * Firebase Cloud Function and have the client invoke that instead, so no
     * admin credential ever reaches the device.
     */
    public void sendNotification(String token, String title, String body) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String BASE_URL = "https://fcm.googleapis.com";
            String FCM_SEND_ENDPOINT = "/v1/projects/carelink-54780/messages:send";

            try {
                if (token == null || token.isEmpty()) {
                    return;
                }

                URL url = new URL(BASE_URL + FCM_SEND_ENDPOINT);
                AssetManager assetManager = getAssets();
                InputStream inputStream = assetManager.open("carelink-54780-firebase-adminsdk-fbsvc-e795cf99fa.json");
                GoogleCredentials googleCredentials = GoogleCredentials.fromStream(inputStream)
                        .createScoped(Arrays.asList("https://www.googleapis.com/auth/firebase.messaging"));

                googleCredentials.refresh();
                String accessToken = googleCredentials.getAccessToken().getTokenValue();

                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
                httpURLConnection.setRequestProperty("Content-Type", "application/json; UTF-8");
                httpURLConnection.setDoOutput(true);

                String jsonPayload = buildJsonPayload(token, title, body);

                try (OutputStream os = httpURLConnection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                httpURLConnection.getResponseCode();

            } catch (Exception e) {
                Log.e("FCM_DEBUG", "Exception during FCM send: " + e.getMessage(), e);
            }
        });
    } 

    // Constructs the JSON body structure required by the FCM v1 API
    private String buildJsonPayload(String token, String title, String body) {
        String safeToken = myFcmToken != null ? myFcmToken : "";
        String myId = FirebaseUtil.currentUserId();

        return "{\"message\":{\"token\":\"" + token + "\",\"data\":{\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"type\":\"chat\",\"userId\":\"" + myId + "\",\"username\":\"" + myUsername + "\",\"fcmToken\":\"" + safeToken + "\"}}}";
    }
}