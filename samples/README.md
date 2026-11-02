# Synthetic test data

Two fictional commercial-loan applicants. All names, PANs, CINs and figures are
**synthetic** and exist only to exercise the underwriting graph end to end.
Each `documents[]` entry is the full text of one document (balance sheet,
P&L, bank statement, tax return) — this is what the customer sends over REST,
and what the LLM nodes read. Nothing is hardcoded in the app.

| File | Applicant | Expected path |
|------|-----------|---------------|
| `clean-application.json`  | Meridian Industrial Supplies | Strong financials, low DTI, credit 760, no compliance flags → **auto-approved (LOW risk)**, no human needed |
| `review-application.json` | Nova Horizon Traders | Net loss, high DTI, credit 585, turnover mismatch + round-tripping (fraud signals), PEP/sanctions name match → **pauses for human review** |

## 1. Start the app (needs your OpenAI key)

```powershell
$env:OPENAI_API_KEY = "sk-..."
mvn -f pom.xml spring-boot:run
```

## 2. Happy path — auto approval

```powershell
curl -X POST http://localhost:8080/api/v1/applications -H "Content-Type: application/json" -d "@samples/clean-application.json"
```
Expect `status: COMPLETED`, `decision: APPROVED`, `nextNode: null`.

## 3. Human-in-the-loop — pause, decide, resume

Start it — the graph runs to the human step and then **pauses**:
```powershell
curl -X POST http://localhost:8080/api/v1/applications -H "Content-Type: application/json" -d "@samples/review-application.json"
```
Expect `status: WAITING_FOR_HUMAN`, `nextNode: decision_finalization`, plus a
`humanReviewReason`.

(Optional) check it is still waiting:
```powershell
curl http://localhost:8080/api/v1/applications/LN-REVIEW-001
```

Submit the human decision — the graph **resumes from the checkpoint** and ends:
```powershell
curl -X POST http://localhost:8080/api/v1/applications/LN-REVIEW-001/decision -H "Content-Type: application/json" -d "{\"decision\":\"REJECTED\",\"reviewer\":\"suhel\",\"notes\":\"Turnover mismatch and unresolved sanctions match\"}"
```
Expect `status: COMPLETED`, `decision: REJECTED`, and the `auditLog` showing the
whole journey including the human decision.
```
