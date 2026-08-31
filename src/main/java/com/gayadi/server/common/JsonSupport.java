package com.gayadi.server.common;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class JsonSupport {

    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON 변환에 실패했습니다.", e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON 파싱에 실패했습니다.", e);
        }
    }

    public <T> T convert(Object value, Class<T> type) {
        return objectMapper.convertValue(value, type);
    }
}
