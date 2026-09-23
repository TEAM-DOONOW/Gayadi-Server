package com.gayadi.server.notification;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "내 알림 목록 조회")
    public List<NotificationRepository.NotificationItem> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.list(userId, limit, offset);
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Long userId, @PathVariable long notificationId) {
        service.markRead(userId, notificationId);
    }

    @PutMapping("/device-token")
    @Operation(summary = "푸시 기기 토큰 등록")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveToken(@AuthenticationPrincipal Long userId, @Valid @RequestBody DeviceToken request) {
        service.saveToken(userId, request.token());
    }

    @DeleteMapping("/device-token")
    @Operation(summary = "푸시 기기 토큰 해제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeToken(@AuthenticationPrincipal Long userId, @Valid @RequestBody DeviceToken request) {
        service.removeToken(userId, request.token());
    }

    public record DeviceToken(@NotBlank @Size(max = 4096) String token) {
    }
}
