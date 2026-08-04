package com.gayadi.server.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(
            @Value("${firebase.project-id}") String projectId,
            @Value("${firebase.service-account-key-path:}") String keyPath) throws IOException {

        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setProjectId(projectId);

        if (keyPath != null && !keyPath.isBlank()) {
            try (FileInputStream serviceAccount = new FileInputStream(keyPath)) {
                builder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
            }
        } else {
            builder.setCredentials(GoogleCredentials.getApplicationDefault());
        }

        return FirebaseApp.initializeApp(builder.build());
    }

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
