package com.tekmaturix.commercialloanunderwritingrealtime.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A flattened, API-friendly snapshot of the graph state.
 *
 * The key field for human-in-the-loop is {@code nextNode}: when it equals
 * "decision_finalization" the workflow is PAUSED and waiting for a decision
 * call ({@code status} will be WAITING_FOR_HUMAN).
 */
public record UnderwritingResponse(
        String applicationId,
        String companyName,
        BigDecimal loanAmount,
        String status,
        String decision,
        boolean humanReviewRequired,
        String humanReviewReason,
        String nextNode,
        Integer creditScore,
        BigDecimal fraudScore,
        Boolean pepMatch,
        Boolean sanctionsMatch,
        BigDecimal dtiRatio,
        BigDecimal riskScore,
        String riskLevel,
        List<String> missingDocuments,
        List<String> auditLog) {
}
