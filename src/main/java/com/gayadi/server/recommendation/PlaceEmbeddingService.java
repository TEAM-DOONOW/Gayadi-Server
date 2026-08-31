package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.query.PlaceEmbeddingQueryResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 저장된 장소를 벡터 검색용 임베딩 문서로 변환합니다. */
@Service
@ConditionalOnProperty(name = "app.ai.embedding.enabled", havingValue = "true")
public class PlaceEmbeddingService {

    private static final int BATCH_SIZE = 100;

    private final VectorStore vectorStore;
    private final PlaceEmbeddingRepository repository;
    private final String storeFile;

    public PlaceEmbeddingService(
            VectorStore vectorStore,
            PlaceEmbeddingRepository repository,
            @Value("${app.ai.vector-store-file:.data/place-vectors.json}") String storeFile) {
        this.vectorStore = vectorStore;
        this.repository = repository;
        this.storeFile = storeFile;
    }

    /** 전체 활성 장소의 벡터 임베딩을 갱신합니다. */
    public int embedAllPlaces() {
        long cursor = 0;
        int embeddedCount = 0;
        while (true) {
            List<PlaceEmbeddingQueryResult> places = repository.findBatchAfter(cursor, BATCH_SIZE);
            if (places.isEmpty()) {
                break;
            }

            List<Document> documents = new ArrayList<>(places.size());
            for (PlaceEmbeddingQueryResult place : places) {
                long placeId = place.id();
                documents.add(new Document(
                        "place-" + placeId,
                        place.name() + " - " + place.category() + " - "
                                + place.address() + " " + place.basicInfo(),
                        java.util.Map.of(
                                "placeId", String.valueOf(placeId),
                                "name", place.name(),
                                "category", place.category(),
                                "latitude", String.valueOf(place.latitude()),
                                "longitude", String.valueOf(place.longitude()))
                ));
                cursor = placeId;
            }
            vectorStore.add(documents);
            embeddedCount += documents.size();
        }
        persistLocalStore();
        return embeddedCount;
    }

    private void persistLocalStore() {
        if (!(vectorStore instanceof SimpleVectorStore simpleStore)) {
            return;
        }
        File file = new File(storeFile);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("장소 검색 자료 저장 폴더를 만들지 못했습니다.");
        }
        simpleStore.save(file);
    }
}
