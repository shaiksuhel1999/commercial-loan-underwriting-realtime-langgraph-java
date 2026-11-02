package com.tekmaturix.commercialloanunderwritingrealtime.service;

import java.util.HashMap;
import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;

import com.tekmaturix.commercialloanunderwritingrealtime.model.UnderwritingState;

/**
 * Applies the human reviewer's decision and closes the application.
 *
 * This node is intentionally NOT LLM-backed: once a human has decided, the
 * outcome must be deterministic and auditable - no model should be able to
 * change or second-guess the person's call.
 *
 * It only ever runs AFTER a resume, because the graph is compiled with
 * interruptBefore("decision_finalization"). By the time apply() executes, the
 * service has already written the human's DECISION into the checkpointed state,
 * so state.decision() holds it here.
 */
public class DecisionFinalizationNode
        implements NodeAction<UnderwritingState> {

    @Override
    public Map<String, Object> apply(UnderwritingState state) {
    	
    	System.out.println("Starting the DecisionFinalizationNode");

        String decision = state.decision();
        if (decision == null || decision.isBlank()) {
            // Fail safe, not fail open: if we somehow resumed without a decision,
            // keep the loan rejected rather than silently approving it.
            decision = "REJECTED";
        }
        decision = decision.toUpperCase();

        System.out.println("\n[Decision Finalization] " + decision
                + " for " + state.applicationId());

        Map<String, Object> out = new HashMap<>();
        out.put(UnderwritingState.DECISION, decision);
        out.put(UnderwritingState.HUMAN_REVIEW_REQUIRED, false);
        // Clear the pending-review reason now that a decision has been made, so a
        // COMPLETED application no longer reports a "manual review" reason. The
        // full history remains in the audit log below.
        out.put(UnderwritingState.HUMAN_REVIEW_REASON, "");
        out.put(UnderwritingState.STATUS, "COMPLETED");
        out.put(UnderwritingState.AUDIT_LOG,
                "Human review finalised with decision: " + decision);

        return out;
    }
}
