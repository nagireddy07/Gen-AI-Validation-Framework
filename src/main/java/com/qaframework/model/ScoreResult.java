package com.qaframework.model;

/** Holds the judge's scores for one query, plus the derived pass/fail status. */
public class ScoreResult {

    private final TestCase testCase;
    private final int semanticCorrectness;
    private final int relevance;
    private final int completeness;
    private final int groundedness;
    private final int overallConfidence;
    private final String reason;
    private final String status; // "PASS" or "FAIL"

    public ScoreResult(TestCase testCase, int semanticCorrectness, int relevance,
                        int completeness, int groundedness, int overallConfidence,
                        String reason, String status) {
        this.testCase = testCase;
        this.semanticCorrectness = semanticCorrectness;
        this.relevance = relevance;
        this.completeness = completeness;
        this.groundedness = groundedness;
        this.overallConfidence = overallConfidence;
        this.reason = reason;
        this.status = status;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public int getSemanticCorrectness() {
        return semanticCorrectness;
    }

    public int getRelevance() {
        return relevance;
    }

    public int getCompleteness() {
        return completeness;
    }

    public int getGroundedness() {
        return groundedness;
    }

    public int getOverallConfidence() {
        return overallConfidence;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPass() {
        return "PASS".equals(status);
    }
}
