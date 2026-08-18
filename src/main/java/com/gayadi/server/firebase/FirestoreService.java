package com.gayadi.server.firebase;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnBean(Firestore.class)
public class FirestoreService {

    private final Firestore firestore;

    public FirestoreService(Firestore firestore) {
        this.firestore = firestore;
    }

    public void save(String collection, long id, Map<String, Object> data) {
        try {
            firestore.collection(collection).document(String.valueOf(id)).set(data).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Firestore 동기화 실패: " + collection + "/" + id, e);
        }
    }

    public Map<String, Object> find(String collection, long id) {
        try {
            DocumentSnapshot doc = firestore.collection(collection)
                    .document(String.valueOf(id)).get().get(10, TimeUnit.SECONDS);
            return doc.exists() ? doc.getData() : null;
        } catch (Exception e) {
            throw new RuntimeException("Firestore 조회 실패: " + collection + "/" + id, e);
        }
    }

    public void delete(String collection, long id) {
        try {
            firestore.collection(collection).document(String.valueOf(id)).delete().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Firestore 삭제 실패: " + collection + "/" + id, e);
        }
    }
}
