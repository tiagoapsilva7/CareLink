package com.example.carelink.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

/**
 * Static accessors for the Firebase entry points the app uses: the current
 * user's id, Firestore collection and document references, and Storage paths.
 *
 * Centralising these keeps collection names and the chatroom-id convention in
 * one place, so a rename does not have to be chased through every activity.
 */
public class FirebaseUtil {

    public static String currentUserId(){
        if(FirebaseAuth.getInstance().getCurrentUser() == null){
            return null; // Ensure we explicitly return null
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public static boolean isLoggedIn(){
        if(currentUserId() != null){
            return true;
        }
        return false;
    }

    public static DocumentReference currentUserDetails(){
        // FIXED: Catch the null check here to prevent the fatal "Provided document path must not be null" exception.
        String userId = currentUserId();
        if (userId == null) {
            return null; // Return null so calling methods can handle it safely
        }
        return FirebaseFirestore.getInstance().collection("users").document(userId);
    }

    public static CollectionReference allUserCollectionReference(){
        return FirebaseFirestore.getInstance().collection("users");
    }

    public static DocumentReference getChatroomReference(String chatroomId){
        return FirebaseFirestore.getInstance().collection("chatrooms").document(chatroomId);
    }

    public static CollectionReference getChatroomMessageReference(String chatroomId){
        return getChatroomReference(chatroomId).collection("chats");
    }

    public static String getChatroomId(String userId1, String userId2){
        if(userId1.hashCode() < userId2.hashCode()){
            return userId1+"_"+userId2;
        }else{
            return userId2+"_"+userId1;
        }
    }

    public static CollectionReference allChatroomCollectionReference(){
        return FirebaseFirestore.getInstance().collection("chatrooms");
    }

    public static DocumentReference getOtherUserFromChatroom(java.util.List<String> userIds){
        if(userIds.get(0).equals(FirebaseUtil.currentUserId())){
            return allUserCollectionReference().document(userIds.get(1));
        }else{
            return allUserCollectionReference().document(userIds.get(0));
        }
    }

    public static StorageReference getCurrentProfilePicStorageRef(){
        return FirebaseStorage.getInstance().getReference().child("profile_pic")
                .child(FirebaseUtil.currentUserId());
    }

    public static StorageReference getOtherProfilePicStorageRef(String otherUserId){
        return FirebaseStorage.getInstance().getReference().child("profile_pic")
                .child(otherUserId);
    }
}