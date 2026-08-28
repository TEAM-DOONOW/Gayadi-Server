package com.gayadi.server.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@ConditionalOnProperty(name = "app.ai.embedding.enabled", havingValue = "true")
public class EmbeddingAiConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Value("${app.ai.vector-store-file:.data/place-vectors.json}") String storeFile) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(storeFile);
        if (file.isFile()) {
            store.load(file);
        }
        return store;
    }
}
