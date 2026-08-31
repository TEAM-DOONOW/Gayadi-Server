package com.gayadi.server.friendship;

import com.gayadi.server.friendship.dto.response.UserSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 친구 관계 관련 HTTP 요청과 응답을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "친구", description = "친구로 추가할 사용자를 닉네임으로 찾습니다.")
@SecurityRequirement(name = "bearerAuth")
public class UserSearchController {

    private final FriendshipService service;

    public UserSearchController(FriendshipService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "친구 추가용 사용자 검색", description = "이메일은 검색하거나 응답하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "닉네임과 일치하는 공개 사용자 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = UserSearchResponse.class))))
    public List<UserSearchResponse> users(
            @AuthenticationPrincipal Long userId,
            @RequestParam
            @NotBlank(message = "검색할 닉네임을 입력해 주세요.")
            @Size(max = 30, message = "검색어는 30자까지 입력할 수 있습니다.") String query,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 개수는 1개 이상이어야 합니다.")
            @Max(value = 30, message = "한 번에 30개까지 조회할 수 있습니다.") int limit) {
        return service.searchUsers(userId, query, limit);
    }
}
