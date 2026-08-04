package com.gayadi.server.recommendation;

import com.gayadi.server.common.RowSupport;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnExpression("'${spring.ai.openai.api-key:}' != ''")
public class PlaceEmbeddingService {

    private final VectorStore vectorStore;
    private final JdbcClient jdbc;

    public PlaceEmbeddingService(VectorStore vectorStore, JdbcClient jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    public int embedAllPlaces() {
        List<Map<String, Object>> places = jdbc.sql(
                "SELECT * FROM places WHERE status = 'ACTIVE'").query().listOfRows();

        List<Document> documents = places.stream().map(place -> {
            String name = RowSupport.strValue(place, "name");
            String category = RowSupport.strValue(place, "category");
            Object addrObj = place.get("address");
            String address = addrObj != null ? addrObj.toString() : "";
            Object infoObj = place.get("basic_info");
            String basicInfo = infoObj != null ? infoObj.toString() : "";
            return new Document(
                    name + " - " + category + " - " + address + " " + basicInfo,
                    Map.of(
                            "placeId", RowSupport.strValue(place, "id"),
                            "name", name,
                            "category", category
                    )
            );
        }).collect(Collectors.toList());

        vectorStore.add(documents);
        return documents.size();
    }
}
