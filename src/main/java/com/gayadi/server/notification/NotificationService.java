package com.gayadi.server.notification;

import com.gayadi.server.firebase.FcmService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final ObjectProvider<FcmService> fcm;

    public NotificationService(NotificationRepository repository, ObjectProvider<FcmService> fcm) {
        this.repository = repository;
        this.fcm = fcm;
    }

    public List<NotificationRepository.NotificationItem> list(long userId, int limit, int offset) {
        return repository.list(userId, Math.max(1, Math.min(limit, 100)), Math.max(0, offset));
    }

    public boolean markRead(long userId, long id) {
        return repository.markRead(userId, id);
    }

    public void saveToken(long userId, String token) {
        repository.saveToken(userId, token);
    }

    public void removeToken(long userId, String token) {
        repository.removeToken(userId, token);
    }

    public void publish(long userId, String type, String title, String content, Long tripId, Long invitationId) {
        long id = repository.create(userId, type, title, content, tripId, invitationId);
        Runnable send = () -> {
            FcmService sender = fcm.getIfAvailable();
            if (sender == null) return;
            for (String token : repository.tokens(userId)) {
                try {
                    sender.sendToToken(token, title, content, Map.of(
                            "notificationId", Long.toString(id), "type", type));
                } catch (RuntimeException ignored) {
                    // Persisted inbox remains available when a push token is stale or FCM is unavailable.
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
