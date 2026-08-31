package com.gayadi.server.support;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InquiryRequest(
        @NotBlank
        @Pattern(regexp = "(?i)BUG|FEATURE|ACCOUNT|ETC", message = "문의 종류가 올바르지 않습니다.")
        String category,

        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 3000)
        String message,

        @NotBlank
        @Email
        @Size(max = 255)
        String contactEmail
) {
    public InquiryCategory parsedCategory() {
        return InquiryCategory.valueOf(category.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
