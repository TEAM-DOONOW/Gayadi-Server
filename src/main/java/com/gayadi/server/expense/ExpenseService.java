package com.gayadi.server.expense;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.expense.dto.request.ExpenseRequest;
import com.gayadi.server.expense.dto.response.ExpenseResponse;
import com.gayadi.server.expense.dto.response.SettlementResponse;
import com.gayadi.server.expense.dto.response.SharedFundSummary;
import com.gayadi.server.expense.model.ExpensePaymentSource;
import com.gayadi.server.expense.query.ExpenseQueryResult;
import com.gayadi.server.expense.query.TripDateRangeQueryResult;
import com.gayadi.server.travel.TripErrorCode;
import com.gayadi.server.travel.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 여행 경비와 정산 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class ExpenseService {

    private final ExpenseRepository repository;
    private final TripService trips;

    public ExpenseService(ExpenseRepository repository, TripService trips) {
        this.repository = repository;
        this.trips = trips;
    }

    /** 참여 권한을 확인하고 여행 경비 목록을 반환합니다. */
    public List<ExpenseResponse> list(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        return responses(repository.findAll(tripId));
    }

    /** 여행 경비의 사용자별 정산 결과를 계산합니다. */
    public SettlementResponse settlement(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        return ExpenseSettlementCalculator.calculate(list(actorId, tripId), repository.findJoinedMemberIds(tripId));
    }

    /** 참여자와 금액을 검증해 새 여행 경비를 등록합니다. */
    @Transactional
    public ExpenseResponse create(long actorId, long tripId, ExpenseRequest request) {
        TripDateRangeQueryResult trip = lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        ValidatedExpense command = validate(tripId, trip, request, null);
        long expenseId = repository.create(
                tripId, actorId, request, command.payerId(), command.date(), command.time());
        repository.replaceParticipants(expenseId, command.participantIds());
        return getResponse(tripId, expenseId);
    }

    /** 권한과 버전을 검증해 여행 경비를 수정합니다. */
    @Transactional
    public ExpenseResponse update(long actorId, long tripId, long expenseId, ExpenseRequest request) {
        TripDateRangeQueryResult trip = lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        lockedExpense(tripId, expenseId);
        ValidatedExpense command = validate(tripId, trip, request, expenseId);
        repository.update(tripId, expenseId, request, command.payerId(), command.date(), command.time());
        repository.replaceParticipants(expenseId, command.participantIds());
        return getResponse(tripId, expenseId);
    }

    /** 권한을 검증해 여행 경비를 삭제합니다. */
    @Transactional
    public void delete(long actorId, long tripId, long expenseId) {
        lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        if (!repository.delete(tripId, expenseId)) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
        }
    }

    /** 공동 자금 관련 여행 경비 업무를 처리합니다. */
    public SharedFundSummary sharedFund(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        return repository.sharedFundSummary(tripId);
    }

    /** 공동 자금에 사용자의 분담금을 적립합니다. */
    @Transactional
    public SharedFundSummary contribute(long actorId, long tripId, long amount) {
        lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        repository.contribute(tripId, amount, actorId);
        return repository.sharedFundSummary(tripId);
    }

    private ValidatedExpense validate(long tripId, TripDateRangeQueryResult trip,
            ExpenseRequest request, Long excludedExpenseId) {
        LocalDate date = AppDateFormat.parseDate(request.date(), "지출 날짜");
        LocalTime time = AppDateFormat.parseTime(request.time(), "지출 시각");
        if (date.isBefore(trip.startDate()) || date.isAfter(trip.endDate())) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_DATE_OUTSIDE_TRIP);
        }
        Set<Long> participantIds = new LinkedHashSet<>(request.participantIds());
        if (participantIds.size() != request.participantIds().size()) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_PARTICIPANT_DUPLICATED);
        }
        requireJoinedMembers(tripId, participantIds);
        Long payerId = request.paymentSource() == ExpensePaymentSource.PERSONAL ? request.payerId() : null;
        if (request.paymentSource() == ExpensePaymentSource.PERSONAL && payerId == null) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_PAYER_REQUIRED);
        }
        if (payerId != null) {
            requireJoinedMembers(tripId, Set.of(payerId));
        }
        if (request.scheduleId() != null && !repository.scheduleExists(tripId, request.scheduleId())) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_SCHEDULE_INVALID);
        }
        if (request.paymentSource() == ExpensePaymentSource.SHARED_FUND
                && availableSharedFund(tripId, excludedExpenseId) < request.amount()) {
            throw new BusinessException(ExpenseErrorCode.SHARED_FUND_BALANCE_INSUFFICIENT);
        }
        return new ValidatedExpense(date, time, payerId, List.copyOf(participantIds));
    }

    private void requireJoinedMembers(long tripId, Set<Long> userIds) {
        if (!repository.areJoinedMembers(tripId, userIds)) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_PARTICIPANT_INVALID);
        }
    }

    private long availableSharedFund(long tripId, Long excludedExpenseId) {
        long available = repository.sharedFundSummary(tripId).balance();
        if (excludedExpenseId != null) {
            ExpenseQueryResult existing = lockedExpense(tripId, excludedExpenseId);
            if (existing.paymentSource() == ExpensePaymentSource.SHARED_FUND) {
                available = Math.addExact(available, existing.amount());
            }
        }
        return available;
    }

    private ExpenseResponse getResponse(long tripId, long expenseId) {
        ExpenseQueryResult expense = repository.find(tripId, expenseId)
                .orElseThrow(() -> new BusinessException(ExpenseErrorCode.EXPENSE_NOT_FOUND));
        return response(expense, repository.findParticipantIds(List.of(expenseId)).getOrDefault(expenseId, List.of()));
    }

    private List<ExpenseResponse> responses(List<ExpenseQueryResult> expenses) {
        Map<Long, List<Long>> participants = repository.findParticipantIds(
                expenses.stream().map(ExpenseQueryResult::id).toList());
        return expenses.stream()
                .map(expense -> response(expense, participants.getOrDefault(expense.id(), List.of())))
                .toList();
    }

    private ExpenseResponse response(ExpenseQueryResult expense, List<Long> participantIds) {
        return new ExpenseResponse(
                expense.id(), expense.tripId(), expense.scheduleId(), expense.title(), expense.memo(),
                expense.amount(), expense.payerId(), participantIds,
                AppDateFormat.date(expense.date()), AppDateFormat.time(expense.time()),
                expense.category(), expense.paymentSource(), expense.receiptImageUri(),
                expense.createdBy(), expense.createdAt(), expense.updatedAt());
    }

    private TripDateRangeQueryResult lockTrip(long tripId) {
        return repository.lockTrip(tripId)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
    }

    private ExpenseQueryResult lockedExpense(long tripId, long expenseId) {
        return repository.findForUpdate(tripId, expenseId)
                .orElseThrow(() -> new BusinessException(ExpenseErrorCode.EXPENSE_NOT_FOUND));
    }

    private record ValidatedExpense(
            LocalDate date,
            LocalTime time,
            Long payerId,
            List<Long> participantIds
    ) {
    }
}
