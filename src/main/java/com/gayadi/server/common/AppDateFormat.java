package com.gayadi.server.common;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.exception.CommonErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Android 표시 형식과 ISO 형식 사이의 날짜·시각 변환을 한곳에서 관리합니다. */
public final class AppDateFormat {

    public static final String DATE_PATTERN = "(?:\\d{4}\\.\\d{2}\\.\\d{2}|\\d{4}-\\d{2}-\\d{2})";
    public static final String TIME_PATTERN = "(?:[01]\\d|2[0-3]):[0-5]\\d";
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private AppDateFormat() {
    }

    public static LocalDate parseDate(String value, String label) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.replace('.', '-'));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE, label);
        }
    }

    public static LocalTime parseTime(String value, String label) {
        if (value == null) return null;
        try {
            return LocalTime.parse(value, TIME);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_TIME, label);
        }
    }

    public static String date(LocalDate value) {
        return value == null ? "" : value.format(DATE);
    }

    public static String time(LocalTime value) {
        return value == null ? "" : value.format(TIME);
    }

    public static LocalDate databaseDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10).replace('.', '-'));
    }

    public static LocalDateTime databaseDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    public static LocalTime databaseTime(Object value) {
        if (value instanceof LocalTime time) return time;
        if (value instanceof java.sql.Time time) return time.toLocalTime();
        return LocalTime.parse(value.toString().substring(0, 8));
    }
}
