package com.tekmaturix.commercialloanunderwritingrealtime.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.commercialloanunderwritingrealtime.dto.HumanDecisionRequest;
import com.tekmaturix.commercialloanunderwritingrealtime.dto.StartApplicationRequest;
import com.tekmaturix.commercialloanunderwritingrealtime.dto.UnderwritingResponse;
import com.tekmaturix.commercialloanunderwritingrealtime.service.UnderwritingService;

/**
 * REST surface for the underwriting workflow.
 *
 * The three endpoints mirror the human-in-the-loop lifecycle:
 *
 *   POST /api/v1/applications                  -> start; may return WAITING_FOR_HUMAN
 *   GET  /api/v1/applications/{id}             -> current status / result
 *   POST /api/v1/applications/{id}/decision    -> submit human decision, resumes to END
 *
 * Why a separate "decision" endpoint instead of doing it all in one call?
 * Because the human step happens out-of-band and possibly minutes or days later.
 * The graph pauses, the HTTP request returns, and the workflow waits in the
 * checkpoint until a person calls back with a decision.
 */
@RestController
@RequestMapping("/api/v1")
public class UnderwritingController {

    private final UnderwritingService underwritingService;

    public UnderwritingController(UnderwritingService underwritingService) {
        this.underwritingService = underwritingService;
    }

    /** Start a new underwriting run. */
    @PostMapping("/applications")
    public UnderwritingResponse start(
            @RequestBody StartApplicationRequest request) throws Exception {

        return underwritingService.start(request);
    }

    /** Check where an application currently sits. */
    @GetMapping("/applications/{applicationId}")
    public UnderwritingResponse status(
            @PathVariable String applicationId) {

        return underwritingService.status(applicationId);
    }

    /** Submit the human reviewer's decision; resumes the paused graph to the end. */
    @PostMapping("/applications/{applicationId}/decision")
    public UnderwritingResponse decide(
            @PathVariable String applicationId,
            @RequestBody HumanDecisionRequest decision) throws Exception {

        return underwritingService.resume(applicationId, decision);
    }

    /** Turn "not waiting" / "not found" guard failures into a clean 409. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }
}
