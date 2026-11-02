package com.tekmaturix.commercialloanunderwritingrealtime.model;

import static java.util.Map.entry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

public class UnderwritingState extends AgentState {

	public static final String APPLICATION_ID = "applicationId";

	public static final String COMPANY_NAME = "companyName";

	public static final String LOAN_AMOUNT = "loanAmount";

	public static final String DOCUMENTS = "documents";

	public static final String MISSING_DOCUMENTS = "missingDocuments";

	public static final String REVENUE = "revenue";

	public static final String ANNUAL_DEBT = "annualDebt";

	public static final String CREDIT_SCORE = "creditScore";

	public static final String FRAUD_SCORE = "fraudScore";

	public static final String PEP_MATCH = "pepMatch";

	public static final String SANCTIONS_MATCH = "sanctionsMatch";

	public static final String DTI_RATIO = "dtiRatio";

	public static final String RISK_SCORE = "riskScore";

	public static final String RISK_LEVEL = "riskLevel";

	public static final String DECISION = "decision";

	public static final String DOCUMENT_REVIEW_DECISION = "documentReviewDecision";

	public static final String HUMAN_REVIEW_REQUIRED = "humanReviewRequired";

	public static final String HUMAN_REVIEW_REASON = "humanReviewReason";

	public static final String STATUS = "status";

	public static final String AUDIT_LOG = "auditLog";

	public static final Map<String, Channel<?>> SCHEMA = Map.<String, Channel<?>>ofEntries(
			entry(APPLICATION_ID, Channels.base(() -> "")), entry(COMPANY_NAME, Channels.base(() -> "")),
			entry(LOAN_AMOUNT, Channels.base(() -> BigDecimal.ZERO)), entry(DOCUMENTS, Channels.base(ArrayList::new)),
			entry(MISSING_DOCUMENTS, Channels.base(ArrayList::new)),
			entry(REVENUE, Channels.base(() -> BigDecimal.ZERO)),
			entry(ANNUAL_DEBT, Channels.base(() -> BigDecimal.ZERO)), entry(CREDIT_SCORE, Channels.base(() -> 0)),
			entry(FRAUD_SCORE, Channels.base(() -> BigDecimal.ZERO)), entry(PEP_MATCH, Channels.base(() -> false)),
			entry(SANCTIONS_MATCH, Channels.base(() -> false)), entry(DTI_RATIO, Channels.base(() -> BigDecimal.ZERO)),
			entry(RISK_SCORE, Channels.base(() -> BigDecimal.ZERO)), entry(RISK_LEVEL, Channels.base(() -> "")),
			entry(DECISION, Channels.base(() -> "")),
			entry(DOCUMENT_REVIEW_DECISION, Channels.base(() -> "")),
			entry(HUMAN_REVIEW_REQUIRED, Channels.base(() -> false)),
			entry(HUMAN_REVIEW_REASON, Channels.base(() -> "")), entry(STATUS, Channels.base(() -> "")),
			entry(AUDIT_LOG, Channels.appender(ArrayList::new)));

	public UnderwritingState(Map<String, Object> initData) {
		super(initData);
	}

	public String applicationId() {
		return this.<String>value(APPLICATION_ID).orElse("");
	}

	public String companyName() {
		return this.<String>value(COMPANY_NAME).orElse("");
	}

	public BigDecimal loanAmount() {
		return this.<BigDecimal>value(LOAN_AMOUNT).orElse(BigDecimal.ZERO);
	}

	@SuppressWarnings("unchecked")
	public List<String> documents() {
		return this.<List<String>>value(DOCUMENTS).orElse(List.of());
	}

	@SuppressWarnings("unchecked")
	public List<String> missingDocuments() {
		return this.<List<String>>value(MISSING_DOCUMENTS).orElse(List.of());
	}

	public BigDecimal revenue() {
		return this.<BigDecimal>value(REVENUE).orElse(BigDecimal.ZERO);
	}

	public BigDecimal annualDebt() {
		return this.<BigDecimal>value(ANNUAL_DEBT).orElse(BigDecimal.ZERO);
	}

	public Integer creditScore() {
		return this.<Integer>value(CREDIT_SCORE).orElse(0);
	}

	public BigDecimal fraudScore() {
		return this.<BigDecimal>value(FRAUD_SCORE).orElse(BigDecimal.ZERO);
	}

	public Boolean pepMatch() {
		return this.<Boolean>value(PEP_MATCH).orElse(false);
	}

	public Boolean sanctionsMatch() {
		return this.<Boolean>value(SANCTIONS_MATCH).orElse(false);
	}

	public BigDecimal dtiRatio() {
		return this.<BigDecimal>value(DTI_RATIO).orElse(BigDecimal.ZERO);
	}

	public BigDecimal riskScore() {
		return this.<BigDecimal>value(RISK_SCORE).orElse(BigDecimal.ZERO);
	}

	public String riskLevel() {
		return this.<String>value(RISK_LEVEL).orElse("");
	}

	public String decision() {
		return this.<String>value(DECISION).orElse("");
	}

	public String documentReviewDecision() {
		return this.<String>value(DOCUMENT_REVIEW_DECISION).orElse("");
	}

	public Boolean humanReviewRequired() {
		return this.<Boolean>value(HUMAN_REVIEW_REQUIRED).orElse(false);
	}

	public String humanReviewReason() {
		return this.<String>value(HUMAN_REVIEW_REASON).orElse("");
	}

	public String status() {
		return this.<String>value(STATUS).orElse("");
	}

	@SuppressWarnings("unchecked")
	public List<String> auditLog() {
		return this.<List<String>>value(AUDIT_LOG).orElse(List.of());
	}

}
