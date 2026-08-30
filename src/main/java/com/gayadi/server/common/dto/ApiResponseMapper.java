package com.gayadi.server.common.dto;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** JDBC projection을 공개 API DTO로 변환하는 HTTP 경계 mapper입니다. */
@Component
public class ApiResponseMapper {

    private final ObjectMapper objectMapper;

    public ApiResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T toDto(Object source, Class<T> type) {
        return objectMapper.convertValue(source, type);
    }

    public <T> List<T> toDtoList(List<?> source, Class<T> type) {
        return source.stream().map(value -> toDto(value, type)).toList();
    }
}
