package com.gayadi.server.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnBean(FirebaseMessaging.class)
public class FcmService {

    private final FirebaseMessaging messaging;

    public FcmService(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    public void sendToTopic(String topic, String title, String body, Map<String, String> data) {
        try {
            Message.Builder builder = Message.builder()
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .setTopic(topic);
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }
            messaging.send(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("FCM 발송 실패: " + title, e);
        }
    }

    public void sendToToken(String token, String title, String body, Map<String, String> data) {
        try {
            Message.Builder builder = Message.builder()
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .setToken(token);
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }
            messaging.send(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("FCM 발송 실패: " + title, e);
        }
    }
}
