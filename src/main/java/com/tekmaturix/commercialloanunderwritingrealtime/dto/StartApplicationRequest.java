package com.tekmaturix.commercialloanunderwritingrealtime.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for POST /api/v1/applications.
 *
 * In this "realtime" variant the LLM nodes read the raw document text, so
 * {@code documents} is a list of the actual document CONTENT (not file names).
 * The model extracts revenue / debt / credit score, screens for compliance,
 * computes DTI and risk from that text.
 */
public record StartApplicationRequest(
        String applicationId,
        String companyName,
        BigDecimal loanAmount,
        List<String> documents) {
}
