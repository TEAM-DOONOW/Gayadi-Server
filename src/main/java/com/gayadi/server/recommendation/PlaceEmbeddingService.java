package com.gayadi.server.recommendation;

import com.gayadi.server.common.RowSupport;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class PlaceEmbeddingService {

    private static final int BATCH_SIZE = 100;

    private final VectorStore vectorStore;
    private final JdbcClient jdbc;
    private final String storeFile;

    public PlaceEmbeddingService(
            VectorStore vectorStore,
            JdbcClient jdbc,
            @Value("${app.ai.vector-store-file:.data/place-vectors.json}") String storeFile) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.storeFile = storeFile;
    }

    public int embedAllPlaces() {
        long cursor = 0;
        int embeddedCount = 0;
        while (true) {
            List<Map<String, Object>> places = jdbc.sql("""
                    SELECT id, name, category, address, basic_info, latitude, longitude
                    FROM places
                    WHERE status = 'ACTIVE' AND visibility = 'PUBLIC' AND id > ?
                    ORDER BY id
                    LIMIT ?
                    """)
                    .params(cursor, BATCH_SIZE)
                    .query().listOfRows();
            if (places.isEmpty()) break;

            List<Document> documents = new ArrayList<>(places.size());
            for (Map<String, Object> place : places) {
                long placeId = RowSupport.longValue(place, "id");
                String name = RowSupport.strValue(place, "name");
                String category = RowSupport.strValue(place, "category");
                String address = nullableText(place, "address");
                String basicInfo = nullableText(place, "basic_info");
                documents.add(new Document(
                        "place-" + placeId,
                        name + " - " + category + " - " + address + " " + basicInfo,
                        Map.of(
                                "placeId", String.valueOf(placeId),
                                "name", name,
                                "category", category,
                                "latitude", RowSupport.value(place, "latitude").toString(),
                                "longitude", RowSupport.value(place, "longitude").toString())
                ));
                cursor = placeId;
            }
            vectorStore.add(documents);
            embeddedCount += documents.size();
        }
        persistLocalStore();
        return embeddedCount;
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        return value == null ? "" : value.toString();
    }

    private void persistLocalStore() {
        if (!(vectorStore instanceof SimpleVectorStore simpleStore)) return;
        File file = new File(storeFile);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("장소 검색 자료 저장 폴더를 만들지 못했습니다.");
        }
        simpleStore.save(file);
    }
}
