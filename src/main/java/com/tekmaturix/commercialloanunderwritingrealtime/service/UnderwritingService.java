package com.tekmaturix.commercialloanunderwritingrealtime.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.stereotype.Service;

import com.tekmaturix.commercialloanunderwritingrealtime.dto.HumanDecisionRequest;
import com.tekmaturix.commercialloanunderwritingrealtime.dto.StartApplicationRequest;
import com.tekmaturix.commercialloanunderwritingrealtime.dto.UnderwritingResponse;
import com.tekmaturix.commercialloanunderwritingrealtime.model.UnderwritingState;

/**
 * Orchestrates the underwriting graph for the REST layer.
 *
 * Three operations map 1:1 to the human-in-the-loop lifecycle:
 *   start(req)            -> run until the graph finishes OR pauses for a human
 *   status(id)            -> peek at where an application currently sits
 *   resume(id, decision)  -> inject the human's decision and run to the end
 *
 * The thread that ties the three separate HTTP calls together is the threadId.
 * We use the applicationId as the threadId, so every call for the same
 * application reads/writes the same checkpoint history in the shared saver.
 */
@Service
public class UnderwritingService {

    private final CompiledGraph<UnderwritingState> graph;

    public UnderwritingService(CompiledGraph<UnderwritingState> graph) {
        this.graph = graph;
    }

    /**
     * Starts a new underwriting run.
     *
     * Because the graph is compiled with interruptBefore("decision_finalization"),
     * invoke() returns either at END (auto-approved low-risk case) or paused just
     * before decision_finalization (human needed). toResponse() reports which.
     */
    public UnderwritingResponse start(StartApplicationRequest req) throws Exception {

        RunnableConfig config = configFor(req.applicationId());

        graph.invoke(buildInput(req), config);

        return toResponse(req.applicationId(), config);
    }

    /**
     * Resumes a paused application with a human decision. Order matters:
     *   1. updateState(...) writes the decision into the stored checkpoint.
     *   2. invoke(null, config) continues from the checkpoint (null = "resume,
     *      don't start over"). decision_finalization then runs and the graph ends.
     */
    public UnderwritingResponse resume(String applicationId, HumanDecisionRequest decision) throws Exception {

        RunnableConfig config = configFor(applicationId);

        Optional<StateSnapshot<UnderwritingState>> snapshot = graph.stateOf(config);
        if (snapshot.isEmpty()) {
            throw new IllegalStateException(
                    "No application found for id '" + applicationId + "'");
        }

        String nextNode = snapshot.get().next();
        boolean atDocGate = UnderwritingStateGraph.DOCUMENT_REVIEW_GATE.equals(nextNode);
        boolean atFinalGate = UnderwritingStateGraph.DECISION_FINALIZATION.equals(nextNode);
        if (!atDocGate && !atFinalGate) {
            throw new IllegalStateException(
                    "Application '" + applicationId
                            + "' is not waiting for a human decision (current status: "
                            + snapshot.get().state().status() + ")");
        }

        String choice = decision.decision() == null ? "" : decision.decision().toUpperCase();

        Map<String, Object> humanUpdate = new HashMap<>();
        // The missing-docs gate reads a SEPARATE field: approving it resumes
        // underwriting and must not pre-fill the final loan decision. The final
        // gate reads DECISION, which decision_finalization applies.
        if (atDocGate) {
            humanUpdate.put(UnderwritingState.DOCUMENT_REVIEW_DECISION, choice);
        } else {
            humanUpdate.put(UnderwritingState.DECISION, choice);
        }
        humanUpdate.put(UnderwritingState.AUDIT_LOG,
                "Human decision received: " + choice
                        + " by " + safe(decision.reviewer())
                        + (decision.notes() == null ? "" : " - " + decision.notes()));

        // 1) write the decision into the checkpoint...
        graph.updateState(config, humanUpdate);

        // 2) ...then resume from where we paused (null input = continue).
        graph.invoke((Map<String, Object>) null, config);

        return toResponse(applicationId, config);
    }

    /** Read-only view of the current state of an application. */
    public UnderwritingResponse status(String applicationId) {
        return toResponse(applicationId, configFor(applicationId));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private RunnableConfig configFor(String applicationId) {
        return RunnableConfig.builder()
                .threadId(applicationId)   // ties start/status/resume to one run
                .build();
    }

    private Map<String, Object> buildInput(StartApplicationRequest req) {

        Map<String, Object> input = new HashMap<>();
        input.put(UnderwritingState.APPLICATION_ID, req.applicationId());

        if (req.companyName() != null) {
            input.put(UnderwritingState.COMPANY_NAME, req.companyName());
        }
        if (req.loanAmount() != null) {
            input.put(UnderwritingState.LOAN_AMOUNT, req.loanAmount());
        }
        input.put(UnderwritingState.DOCUMENTS,
                req.documents() != null ? req.documents() : List.of());

        return input;
    }

    private UnderwritingResponse toResponse(String applicationId, RunnableConfig config) {

        StateSnapshot<UnderwritingState> snapshot =
                graph.stateOf(config)
                        .orElseThrow(() -> new IllegalStateException(
                                "No application found for id '" + applicationId + "'"));

        UnderwritingState s = snapshot.state();

        // next() is the node the graph would run next. If it is either HITL gate
        // (missing-docs review or final decision), the run is paused for a human.
        String nextNode = snapshot.next();
        boolean paused =
                UnderwritingStateGraph.DECISION_FINALIZATION.equals(nextNode)
                        || UnderwritingStateGraph.DOCUMENT_REVIEW_GATE.equals(nextNode);

        String status = paused ? "WAITING_FOR_HUMAN" : s.status();

        return new UnderwritingResponse(
                s.applicationId(),
                s.companyName(),
                s.loanAmount(),
                status,
                s.decision(),
                paused || Boolean.TRUE.equals(s.humanReviewRequired()),
                s.humanReviewReason(),
                nextNode,
                s.creditScore(),
                s.fraudScore(),
                s.pepMatch(),
                s.sanctionsMatch(),
                s.dtiRatio(),
                s.riskScore(),
                s.riskLevel(),
                s.missingDocuments(),
                s.auditLog());
    }

    private static String safe(String v) {
        return v == null ? "unknown" : v;
    }
}
