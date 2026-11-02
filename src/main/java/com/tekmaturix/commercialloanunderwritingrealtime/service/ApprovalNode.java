package com.tekmaturix.commercialloanunderwritingrealtime.service;

import java.util.HashMap;
import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;

import com.tekmaturix.commercialloanunderwritingrealtime.converter.ApprovalNodeBeanOutputConverter;
import com.tekmaturix.commercialloanunderwritingrealtime.model.UnderwritingState;

import lombok.extern.java.Log;

public class ApprovalNode implements NodeAction<UnderwritingState> {
	
    public static final String SYSTEM_PROMPT = """
        You are a senior commercial loan underwriting decision support specialist.

        Your responsibility is to evaluate the available underwriting results
        and recommend a loan decision based strictly on the evidence and the
        organization's configured lending policy.

        Follow these rules strictly:

        1. EVIDENCE-BASED DECISION
           - Use only the supplied underwriting data and documented policy.
           - Do not invent borrower information, financial figures, or findings.
           - Consider revenue, annual debt obligations, credit score,
             debt-to-income ratio (DTI), risk score, risk level, and compliance
             screening results when available.
           - Do not assume that missing information indicates low risk.

        2. DECISION RULES
           - APPROVED: Use only when all mandatory requirements are satisfied,
             sufficient evidence is available, and no unresolved critical
             compliance or policy issue prevents approval.
           - REJECTED: Use only when reliable evidence establishes that a
             mandatory lending requirement is not met under the supplied policy.
           - MANUAL_REVIEW: Use when information is missing, ambiguous,
             contradictory, insufficient, or requires human verification.
           - Do not invent lending thresholds or eligibility rules.
           - If no explicit lending policy is provided, do not independently
             assume approval or rejection criteria. Use MANUAL_REVIEW.

        3. COMPLIANCE SAFEGUARDS
           - Do not treat a PEP match alone as proof of wrongdoing.
           - Do not treat an unverified sanctions match as a confirmed match.
           - Escalate unresolved sanctions, identity, or potential fraud concerns
             for appropriate human or compliance review.
           - Do not infer criminal activity from document inconsistencies alone.
           - Do not use protected personal characteristics to make a decision.

        4. STATUS
           - Return COMPLETED when the assessment has produced a supported
             decision or recommendation.
           - Do not use COMPLETED to imply that a human review or external
             compliance verification has occurred.
           - If the schema supports a separate pending-review status, use it
             when a decision requires further review.

        5. OUTPUT
           - Return only the structured output required by the provided schema.
           - Ensure the decision value is exactly one of:
             APPROVED, REJECTED, MANUAL_REVIEW.
           - Do not include explanations outside the output structure.
        """;

    public final ChatClient chatClient;

    public ApprovalNode(ChatClient chatClient) {
        super();
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(UnderwritingState state) throws Exception {

    	System.out.println("Starting the ApprovalNode");
        BeanOutputConverter<ApprovalNodeBeanOutputConverter> opCon =
                new BeanOutputConverter<>(ApprovalNodeBeanOutputConverter.class);

        PromptTemplate template = new PromptTemplate("""
            Evaluate the following commercial loan underwriting results.

            Determine the appropriate decision:
            - APPROVED
            - REJECTED
            - MANUAL_REVIEW

            Decision requirements:
            - Apply only the lending policy and eligibility requirements explicitly
              provided in the underwriting data.
            - Consider the financial analysis, risk assessment, and compliance
              results when available.
            - Mostly, APPROVED or REJECTED, If and only If mandatory information is missing or a critical finding is
              unresolved, choose MANUAL_REVIEW.
            - Do not invent missing data or lending thresholds.
            - Do not automatically approve a loan merely because a previous
              node completed successfully.
            - Do not treat completion of this workflow as proof that all
              required human reviews have occurred.

            Underwriting data:
            {underwritingData}

            {format}
            """);

        String userPrompt = template.create(
                Map.of(
                        "underwritingData", state.toString(),
                        "format", opCon.getFormat()
                )
        ).getContents();

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        ApprovalNodeBeanOutputConverter result = opCon.convert(response);

        // Null-safe: always complete, but only overwrite DECISION when the model
        // actually returned one (Map.of rejects null values).
        Map<String, Object> out = new HashMap<>();
        out.put(UnderwritingState.STATUS, "COMPLETED");
        if (result != null && result.decision() != null) {
            out.put(UnderwritingState.DECISION, result.decision());
        }
        return out;
    }
}