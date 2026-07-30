package com.gayadi.server.recommendation

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(VectorStore::class)
class PlaceEmbeddingService(
    private val vectorStore: VectorStore,
    private val jdbc: JdbcClient
) {

    fun embedAllPlaces(): Int {
        val places = jdbc.sql("SELECT * FROM places").query().listOfRows()
        val documents = places.map { place ->
            val name = col(place, "name").toString()
            val category = col(place, "category").toString()
            val address = col(place, "address").toString()
            val basicInfo = col(place, "basic_info")?.toString() ?: ""
            Document(
                "$name - $category - $address $basicInfo",
                mapOf(
                    "placeId" to col(place, "id").toString(),
                    "name" to name,
                    "category" to category
                )
            )
        }
        vectorStore.add(documents)
        return documents.size
    }

    private fun col(row: Map<String, Any>, key: String): Any? =
        row[key] ?: row[key.uppercase()]
}
