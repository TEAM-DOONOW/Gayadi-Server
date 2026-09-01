package com.gayadi.server.event.command;

import com.gayadi.server.event.model.ChangeProposalType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 상황대처 Agent의 판단을 승인 가능한 일정 변경 제안으로 전달하는 내부 명령입니다. */
public record AiChangeProposalCommand(
        ChangeProposalType proposalType,
        String reason,
        Map<String, Object> situationData,
        List<AiChangeProposalOption> options,
        boolean requireIndoor
) {
    public AiChangeProposalCommand {
        Objects.requireNonNull(proposalType, "proposalType");
        situationData = situationData == null ? Map.of() : Map.copyOf(situationData);
        options = options == null ? List.of() : List.copyOf(options);
    }
}
