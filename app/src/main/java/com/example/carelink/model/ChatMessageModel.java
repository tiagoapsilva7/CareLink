package com.example.carelink.model;

import com.google.firebase.Timestamp;

/**
 * A single chat message, stored as a document under
 * {@code chatrooms/{chatroomId}/chats} in Firestore.
 *
 * The no-argument constructor and public getters/setters are required by
 * Firestore's automatic POJO deserialisation; do not remove them.
 */
public class ChatMessageModel {
    private String message;
    private String  senderId;
    private Timestamp timestamp;



    public ChatMessageModel() {
    }

    public ChatMessageModel(String message, String senderId, Timestamp timestamp) {
        this.message = message;
        this.senderId = senderId;
        this.timestamp = timestamp;

    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setsenderId(String senderId) {
        this.senderId = senderId;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }


}
