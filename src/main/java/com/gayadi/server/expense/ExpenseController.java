package com.gayadi.server.expense;

import com.gayadi.server.expense.dto.request.ExpenseRequest;
import com.gayadi.server.expense.dto.request.SharedFundContributionRequest;
import com.gayadi.server.expense.dto.response.ExpenseResponse;
import com.gayadi.server.expense.dto.response.SettlementResponse;
import com.gayadi.server.expense.dto.response.SharedFundSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 여행 경비와 정산 관련 HTTP 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "여행 경비", description = "여행 지출, 공동 경비 잔액과 참여자 정산")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping("/expenses")
    @Operation(summary = "여행 경비 목록")
    @ApiResponse(responseCode = "200", description = "날짜와 시각 순으로 정렬된 경비",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ExpenseResponse.class))))
    public List<ExpenseResponse> list(
            @AuthenticationPrincipal Long userId, @PathVariable long tripId) {
        return service.list(userId, tripId);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "여행 경비 추가")
    public ExpenseResponse create(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody ExpenseRequest request) {
        return service.create(userId, tripId, request);
    }

    @PatchMapping("/expenses/{expenseId}")
    @Operation(summary = "여행 경비 수정")
    public ExpenseResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long expenseId,
            @Valid @RequestBody ExpenseRequest request) {
        return service.update(userId, tripId, expenseId, request);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "여행 경비 삭제")
    public void delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long expenseId) {
        service.delete(userId, tripId, expenseId);
    }

    @GetMapping("/expense-settlement")
    @Operation(summary = "여행 경비 정산",
            description = "원 단위 나머지를 사용자 번호순으로 배분하고 최소한의 송금 목록을 제안합니다.")
    public SettlementResponse settlement(
            @AuthenticationPrincipal Long userId, @PathVariable long tripId) {
        return service.settlement(userId, tripId);
    }

    @GetMapping("/shared-fund")
    @Operation(summary = "공동 경비 잔액")
    public SharedFundSummary sharedFund(
            @AuthenticationPrincipal Long userId, @PathVariable long tripId) {
        return service.sharedFund(userId, tripId);
    }

    @PostMapping("/shared-fund/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "공동 경비 충전")
    public SharedFundSummary contribute(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody SharedFundContributionRequest request) {
        return service.contribute(userId, tripId, request.amount());
    }
}
