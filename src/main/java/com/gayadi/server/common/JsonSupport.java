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
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("JSON 변환에 실패했습니다.", exception);
        }
    }
}
