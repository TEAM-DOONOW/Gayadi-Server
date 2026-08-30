package com.gayadi.server.event;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationPayloadValidatorTest {

    private final JsonSupport json = new JsonSupport(new ObjectMapper());

    @Test
    void acceptsSmallStructuredObservation() {
        String result = ObservationPayloadValidator.validateAndSerialize(
                Map.of("temperature", 29, "weather", Map.of("rain", false)), json);

        assertThat(result).contains("temperature", "weather");
    }

    @Test
    void rejectsTooManyEntries() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < 33; index++) values.put("key" + index, index);

        assertThatThrownBy(() -> ObservationPayloadValidator.validateAndSerialize(values, json))
                .isInstanceOf(ApiException.class).hasMessageContaining("항목 32개");
    }

    @Test
    void rejectsOversizedSerializedValue() {
        String oversized = "가".repeat(3_000);

        assertThatThrownBy(() -> ObservationPayloadValidator.validateAndSerialize(
                Map.of("description", oversized), json))
                .isInstanceOf(ApiException.class);
    }
}
