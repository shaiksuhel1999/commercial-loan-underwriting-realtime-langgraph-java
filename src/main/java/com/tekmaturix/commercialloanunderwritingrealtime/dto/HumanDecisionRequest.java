package com.tekmaturix.commercialloanunderwritingrealtime.dto;

/**
 * Body for POST /api/v1/applications/{id}/decision.
 *
 * @param decision "APPROVED" or "REJECTED"
 * @param reviewer who made the call (captured in the audit trail)
 * @param notes    optional free-text justification
 */
public record HumanDecisionRequest(
        String decision,
        String reviewer,
        String notes) {
}
