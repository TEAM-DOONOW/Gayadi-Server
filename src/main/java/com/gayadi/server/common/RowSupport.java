package com.gayadi.server.common;

import java.util.Map;

public final class RowSupport {

    private RowSupport() {
    }

    public static Object value(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v != null) return v;
        v = row.get(key.toUpperCase());
        if (v != null) return v;
        throw new IllegalArgumentException("조회 결과에 필요한 열이 없습니다: " + key);
    }

    public static long longValue(Map<String, Object> row, String key) {
        Object v = value(row, key);
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    public static int intValue(Map<String, Object> row, String key) {
        Object v = value(row, key);
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    public static String strValue(Map<String, Object> row, String key) {
        return value(row, key).toString();
    }
}
