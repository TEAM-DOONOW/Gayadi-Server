package com.gayadi.server.route;

import com.gayadi.server.config.ApiSuccessSchemas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "경로")
@SecurityRequirement(name = "bearerAuth")
public class RouteController {

    private final RouteService service;

    public RouteController(RouteService service) {
        this.service = service;
    }

    @PostMapping("/route-recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "경로 추천",
            description = "출발·일정·귀가 경로를 계산해 추천안으로 저장합니다. userId는 인증 사용자 본인의 식별자만 받을 수 있습니다.")
    @ApiResponse(responseCode = "201", description = "저장한 추천 경로입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Route.class)))
    public Map<String, Object> recommendation(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive long tripId,
            @Valid @RequestBody RecommendationRequest request) {
        return service.recommendForUser(
                tripId, userId, service.routePhase(request.type()), request.userId());
    }

    @GetMapping("/route-selections")
    @Operation(summary = "선택한 경로 목록")
    @ApiResponse(responseCode = "200", description = "현재 선택한 경로 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiSuccessSchemas.Route.class))))
    public List<Map<String, Object>> selections(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive long tripId) {
        return service.selectionsForUser(tripId, userId);
    }

    @PutMapping("/route-selections/{type}")
    @Operation(summary = "추천 경로 선택",
            description = "서버 경로 번호 routeId 또는 앱 선택값 optionId 중 하나를 보냅니다.")
    @ApiResponse(responseCode = "200", description = "선택한 경로입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Route.class)))
    public Map<String, Object> selection(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive long tripId,
            @PathVariable String type,
            @Valid @RequestBody SelectionRequest request) {
        return service.selectForUser(
                tripId, userId, service.routePhase(type), request.routeId(),
                request.optionId(), request.userId());
    }

    @DeleteMapping("/route-selections/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "선택한 경로 해제")
    public void selectionDeletion(
            @AuthenticationPrincipal Long actorUserId,
            @PathVariable @Positive long tripId,
            @PathVariable String type,
            @RequestParam(name = "userId", required = false) @Positive Long requestedUserId) {
        service.clearSelectionForUser(
                tripId, actorUserId, service.routePhase(type), requestedUserId);
    }

    public record RecommendationRequest(@NotBlank String type, @Positive Long userId) {
    }

    public record SelectionRequest(
            @Positive Long routeId,
            @Size(max = 20) String optionId,
            @Positive Long userId) {
    }
}
