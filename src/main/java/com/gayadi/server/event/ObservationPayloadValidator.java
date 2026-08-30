package com.gayadi.server.event;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class ObservationPayloadValidator {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_NODES = 128;
    private static final int MAX_MAP_ENTRIES = 32;
    private static final int MAX_LIST_ITEMS = 32;
    private static final int MAX_KEY_LENGTH = 50;
    private static final int MAX_STRING_LENGTH = 500;
    private static final int MAX_SERIALIZED_BYTES = 8 * 1024;

    private ObservationPayloadValidator() {
    }

    static String validateAndSerialize(Map<String, Object> values, JsonSupport json) {
        Counter counter = new Counter();
        validate(values, 0, counter);
        String serialized = json.write(values);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_SERIALIZED_BYTES) {
            throw invalid();
        }
        return serialized;
    }

    private static void validate(Object value, int depth, Counter counter) {
        if (depth > MAX_DEPTH || ++counter.nodes > MAX_NODES) throw invalid();
        if (value == null || value instanceof Boolean || value instanceof Number) return;
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) throw invalid();
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_MAP_ENTRIES) throw invalid();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || key.isBlank() || key.length() > MAX_KEY_LENGTH) throw invalid();
                validate(entry.getValue(), depth + 1, counter);
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_LIST_ITEMS) throw invalid();
            for (Object item : list) validate(item, depth + 1, counter);
            return;
        }
        throw invalid();
    }

    private static BusinessException invalid() {
        return new BusinessException(EventErrorCode.OBSERVATION_PAYLOAD_INVALID);
    }

    private static final class Counter {
        private int nodes;
    }
}
