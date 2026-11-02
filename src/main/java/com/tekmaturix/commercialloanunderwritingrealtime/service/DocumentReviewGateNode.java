package com.tekmaturix.commercialloanunderwritingrealtime.service;

import java.util.HashMap;
import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;

import com.tekmaturix.commercialloanunderwritingrealtime.model.UnderwritingState;

/**
 * Applies the human reviewer's decision on the MISSING-DOCUMENTS gate.
 *
 * This is different from {@link DecisionFinalizationNode}: approving here does
 * NOT finalize the loan. It only clears the missing-document hold so the
 * application can continue through the normal underwriting pipeline
 * (compliance -> financial -> risk -> ...). Only a rejection ends the run.
 *
 * Like decision finalization, it is deterministic and runs only AFTER a resume,
 * because the graph is compiled with interruptBefore(DOCUMENT_REVIEW_GATE). By
 * the time apply() runs, the service has written the reviewer's choice into
 * {@code documentReviewDecision}. The conditional edge out of this node then
 * routes APPROVED back to compliance and anything else to finalization.
 */
public class DocumentReviewGateNode implements NodeAction<UnderwritingState> {

    @Override
    public Map<String, Object> apply(UnderwritingState state) {

        System.out.println("Starting the DocumentReviewGateNode");

        String choice = state.documentReviewDecision();

        Map<String, Object> out = new HashMap<>();
        out.put(UnderwritingState.HUMAN_REVIEW_REQUIRED, false);

        if ("APPROVED".equalsIgnoreCase(choice)) {
            // Proceed with underwriting despite the missing documents.
            out.put(UnderwritingState.STATUS, "IN_PROGRESS");
            out.put(UnderwritingState.AUDIT_LOG,
                    "Missing-document review APPROVED; resuming underwriting");
        } else {
            // Anything other than an explicit approval declines the application
            // (fail-safe: a blank/unknown choice never auto-approves).
            out.put(UnderwritingState.DECISION, "REJECTED");
            out.put(UnderwritingState.AUDIT_LOG,
                    "Missing-document review REJECTED; declining application");
        }

        return out;
    }
}
