package com.tekmaturix.commercialloanunderwritingrealtime.service;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.ai.chat.client.ChatClient;

import com.tekmaturix.commercialloanunderwritingrealtime.model.UnderwritingState;

/**
 * Builds and compiles the commercial-loan underwriting graph (the LLM-backed,
 * "realtime" variant - every analysis node calls the model through a ChatClient).
 *
 * This class answers two questions the framework needs:
 *   1. WHAT are the steps and how do they connect?  -> the nodes + edges below
 *   2. HOW do we pause for a human and come back?    -> the CompileConfig at the end
 *
 * The human-in-the-loop (HITL) design:
 *   - The LLM "human_review" node runs first and produces a reviewer summary and
 *     flips STATUS to WAITING_FOR_HUMAN. It does NOT decide anything.
 *   - We then pause the graph with interruptBefore("decision_finalization").
 *     At that point the state is checkpointed and control returns to the caller.
 *   - A human later submits APPROVED / REJECTED. The service writes that into the
 *     checkpoint and resumes; only then does "decision_finalization" run and END.
 */
public class UnderwritingStateGraph {

    // Node names kept as constants so the wiring, the interrupt rule and the
    // service that inspects "what runs next" all share one spelling.
    public static final String DOCUMENT_EXTRACTION = "document_extraction";
    public static final String VALIDATION = "validation";
    public static final String COMPLIANCE = "compliance";
    public static final String FINANCIAL_ANALYSIS = "financial_analysis";
    public static final String RISK_ASSESSMENT = "risk_assessment";
    public static final String APPROVAL = "approval";
    public static final String HUMAN_REVIEW = "human_review";
    public static final String DOCUMENT_REVIEW = "document_review";
    public static final String DOCUMENT_REVIEW_GATE = "document_review_gate";
    public static final String DECISION_FINALIZATION = "decision_finalization";

    /** Fraud score at/above which we force a human to look. */
    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("0.70");

    private UnderwritingStateGraph() {
        // utility class - only the static build() is used
    }

    /**
     * @param chatClient the Spring AI client the analysis nodes call
     * @param saver      the shared checkpoint store that makes pause/resume work
     */
    public static CompiledGraph<UnderwritingState> build(
            ChatClient chatClient,
            BaseCheckpointSaver saver) throws Exception {

        var graph =
                new StateGraph<>(
                        UnderwritingState.SCHEMA,
                        UnderwritingState::new
                )

                // ---- nodes (each LLM node gets the ChatClient) ----
                .addNode(DOCUMENT_EXTRACTION,
                        node_async(new DocumentExtractionNode(chatClient)))

                .addNode(VALIDATION,
                        node_async(new ValidationNode(chatClient)))

                .addNode(COMPLIANCE,
                        node_async(new ComplianceNode(chatClient)))

                .addNode(FINANCIAL_ANALYSIS,
                        node_async(new FinancialAnalysisNode(chatClient)))

                .addNode(RISK_ASSESSMENT,
                        node_async(new RiskAssesmentNode(chatClient)))

                .addNode(APPROVAL,
                        node_async(new ApprovalNode(chatClient)))

                .addNode(HUMAN_REVIEW,
                        node_async(new HumanReviewNode(chatClient)))

                // missing-documents gate: produces the reviewer summary, then
                // pauses. Approving it RESUMES underwriting rather than ending.
                .addNode(DOCUMENT_REVIEW,
                        node_async(new HumanReviewNode(chatClient)))

                // deterministic: applies the missing-doc reviewer's decision
                .addNode(DOCUMENT_REVIEW_GATE,
                        node_async(new DocumentReviewGateNode()))

                // deterministic: applies the human's decision after the pause
                .addNode(DECISION_FINALIZATION,
                        node_async(new DecisionFinalizationNode()))

                // ---- edges ----
                .addEdge(START, DOCUMENT_EXTRACTION)
                .addEdge(DOCUMENT_EXTRACTION, VALIDATION)

                // validation -> missing-docs review if any required information
                // is missing, otherwise continue to compliance. Without this a
                // run with missing documents would flow straight through to the
                // end and be marked COMPLETED even though it was never reviewed.
                // This gate is SEPARATE from the fraud/risk human review below:
                // approving it resumes underwriting (see DOCUMENT_REVIEW_GATE).
                .addConditionalEdges(
                        VALIDATION,
                        state -> {
                            if (state.missingDocuments() != null
                                    && !state.missingDocuments().isEmpty()) {
                                return CompletableFuture.completedFuture("review");
                            }
                            return CompletableFuture.completedFuture("compliance");
                        },
                        Map.of(
                                "review", DOCUMENT_REVIEW,
                                "compliance", COMPLIANCE
                        )
                )

                // document_review -> pause (interruptBefore the gate) -> on
                // resume the gate applies the reviewer's choice and routes:
                //   APPROVED -> back into compliance (underwriting continues)
                //   otherwise -> decision_finalization (application declined)
                .addEdge(DOCUMENT_REVIEW, DOCUMENT_REVIEW_GATE)
                .addConditionalEdges(
                        DOCUMENT_REVIEW_GATE,
                        state -> {
                            if ("APPROVED".equalsIgnoreCase(
                                    state.documentReviewDecision())) {
                                return CompletableFuture.completedFuture("approve");
                            }
                            return CompletableFuture.completedFuture("reject");
                        },
                        Map.of(
                                "approve", COMPLIANCE,
                                "reject", DECISION_FINALIZATION
                        )
                )

                // compliance -> human if fraud/PEP/sanctions, else financial
                .addConditionalEdges(
                        COMPLIANCE,
                        state -> {
                            if (state.fraudScore().compareTo(FRAUD_THRESHOLD) >= 0
                                    || Boolean.TRUE.equals(state.pepMatch())
                                    || Boolean.TRUE.equals(state.sanctionsMatch())) {
                                return CompletableFuture.completedFuture("human");
                            }
                            return CompletableFuture.completedFuture("financial");
                        },
                        Map.of(
                                "human", HUMAN_REVIEW,
                                "financial", FINANCIAL_ANALYSIS
                        )
                )

                .addEdge(FINANCIAL_ANALYSIS, RISK_ASSESSMENT)

                // risk -> auto-approve only if LOW, otherwise a human decides
                .addConditionalEdges(
                        RISK_ASSESSMENT,
                        state -> {
                            if ("LOW".equalsIgnoreCase(state.riskLevel())) {
                                return CompletableFuture.completedFuture("approve");
                            }
                            return CompletableFuture.completedFuture("human");
                        },
                        Map.of(
                                "approve", APPROVAL,
                                "human", HUMAN_REVIEW
                        )
                )

                // terminal / HITL wiring
                .addEdge(APPROVAL, END)
                .addEdge(HUMAN_REVIEW, DECISION_FINALIZATION)
                .addEdge(DECISION_FINALIZATION, END);

        // The two lines that enable human-in-the-loop:
        //   checkpointSaver  -> persist state so we can come back to it
        //   interruptBefore  -> stop right before committing the human decision
        var compileConfig =
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        // Two human-in-the-loop pause points:
                        //   DOCUMENT_REVIEW_GATE  -> missing-docs approval (resumes)
                        //   DECISION_FINALIZATION -> final loan decision (ends)
                        .interruptBefore(DOCUMENT_REVIEW_GATE, DECISION_FINALIZATION)
                        .build();

        return graph.compile(compileConfig);
    }
}
