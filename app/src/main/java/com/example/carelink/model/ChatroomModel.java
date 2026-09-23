package com.example.carelink.model;

import com.google.firebase.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Metadata for a one-to-one conversation, stored in the Firestore
 * {@code chatrooms} collection.
 *
 * The document id is the deterministic pair id built by
 * {@link com.example.carelink.util.FirebaseUtil#getChatroomId}, so both
 * participants resolve the same room. The denormalised last-message fields let
 * the recent-chats list render without reading each room's messages.
 *
 * The no-argument constructor and public getters/setters are required by
 * Firestore's automatic POJO deserialisation; do not remove them.
 */
public class ChatroomModel {
    String chatroomId;
    List<String> userIds;
    Timestamp lastMessageTimestamp;
    String lastMessageSenderId;
    String lastMessage;
    Map<String, Long> unreadMessageCount;

    String status;
    String requestSenderId;

    private Map<String, Long> unreadMedicalCount;


    public ChatroomModel() {
    }

    public ChatroomModel(String chatroomId, List<String> userIds, Timestamp lastMessageTimestamp, String lastMessageSenderId, String lastMessage, Map<String, Long> unreadMessageCount, String status, String requestSenderId) {
        this.chatroomId = chatroomId;
        this.userIds = userIds;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessage = lastMessage;
        this.unreadMessageCount = unreadMessageCount;
        this.status = status;
        this.requestSenderId = requestSenderId;
    }

    // Getters and Setters
    public String getChatroomId() { return chatroomId; }
    public void setChatroomId(String chatroomId) { this.chatroomId = chatroomId; }
    public Map<String, Long> getUnreadMedicalCount() { return unreadMedicalCount; }
    public void setUnreadMedicalCount(Map<String, Long> unreadMedicalCount) { this.unreadMedicalCount = unreadMedicalCount; }
    public List<String> getUserIds() { return userIds; }
    public void setUserIds(List<String> userIds) { this.userIds = userIds; }

    public Timestamp getLastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(Timestamp lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }

    public String getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public Map<String, Long> getUnreadMessageCount() { return unreadMessageCount; }
    public void setUnreadMessageCount(Map<String, Long> unreadMessageCount) { this.unreadMessageCount = unreadMessageCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestSenderId() { return requestSenderId; }
    public void setRequestSenderId(String requestSenderId) { this.requestSenderId = requestSenderId; }
}