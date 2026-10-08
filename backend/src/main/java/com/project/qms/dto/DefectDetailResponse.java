package com.project.qms.dto;

import java.util.List;

/**
 * Everything the defect detail screen needs, in one response.
 *
 * 'allowedNextStatuses' is the interesting part. The service computes it from
 * the SAME transition map it uses to validate a change, so the buttons the
 * user sees can never offer a move the server would reject. One rule, one
 * place, two uses (Phase 5, C2).
 *
 * The list is filtered by the logged-in user's role, because a Supervisor and
 * an Inspector are allowed different moves (Phase 5 §7.4).
 */
public record DefectDetailResponse(
        DefectResponse defect,
        List<DefectHistoryResponse> history,
        List<CorrectiveActionResponse> correctiveActions,
        List<String> allowedNextStatuses) {
}
