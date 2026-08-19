package com.gayadi.server.tourapi;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 관광 API 커서. 상향 공공데이터 API는 offset(pageNo) 페이징만 지원해서
 * pageNo를 불투명한 커서로 감싸 프론트엔드에는 커서만 노출한다.
 */
final class TourCursor {

    private static final String PREFIX = "gayadi-tour:";

    private TourCursor() {
    }

    static String encode(int pageNo) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + pageNo).getBytes(StandardCharsets.UTF_8));
    }

    static int decodePageNo(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 1;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith(PREFIX)) {
                return 1;
            }
            int pageNo = Integer.parseInt(decoded.substring(PREFIX.length()));
            return pageNo < 1 ? 1 : pageNo;
        } catch (RuntimeException e) {
            return 1;
        }
    }
}
