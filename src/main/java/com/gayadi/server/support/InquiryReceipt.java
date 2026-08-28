package com.gayadi.server.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "InquiryReceipt", description = "문의 접수 결과")
public record InquiryReceipt(
        long id,
        String category,
        String status,
        LocalDateTime createdAt
) {
}
