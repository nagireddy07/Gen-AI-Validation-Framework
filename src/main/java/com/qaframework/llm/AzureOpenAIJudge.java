package com.qaframework.llm;

import com.qaframework.config.AppConfig;
import com.qaframework.model.ScoreResult;
import com.qaframework.model.TestCase;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Scores one TestCase (Query / Expected_Response / Actual_Response) against four dimensions
 * using an Azure OpenAI chat model as the judge, then derives an Overall_Confidence in code
 * (average of the four) so the number is always consistent and reproducible.
 *
 * Retry/backoff pattern mirrors ValidationOfResponseUsingLLM: rate limits and transient
 * errors are retried with exponential backoff; malformed JSON triggers a retry too.
 */
public class AzureOpenAIJudge {

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build();

    public ScoreResult evaluate(TestCase testCase) throws Exception {
        String prompt = buildPrompt(testCase);
        JSONObject scores = callWithRetry(prompt);

        int semanticCorrectness = clamp(scores.getInt("Semantic_Correctness"));
        int relevance           = clamp(scores.getInt("Relevance"));
        int completeness        = clamp(scores.getInt("Completeness"));
        int groundedness        = clamp(scores.getInt("Groundedness"));
        String reason           = scores.optString("Reason", "");

        int overallConfidence = Math.round(
                (semanticCorrectness + relevance + completeness + groundedness) / 4.0f);

        String status = overallConfidence >= AppConfig.getPassThreshold() ? "PASS" : "FAIL";

        return new ScoreResult(testCase, semanticCorrectness, relevance, completeness,
                groundedness, overallConfidence, reason, status);
    }

    // ─── Prompt ─────────────────────────────────────────────────────────────

    private String buildPrompt(TestCase testCase) {
        return "You are a strict evaluation judge comparing an AI system's Actual Response "
                + "against the Expected Response for a given Query.\n\n"
                + "Score the Actual Response on these four dimensions, each as an integer 0-100:\n"
                + "- Semantic_Correctness: does the Actual Response mean the same thing as the Expected Response?\n"
                + "- Relevance: does the Actual Response actually address the Query asked?\n"
                + "- Completeness: does the Actual Response cover everything the Expected Response covers?\n"
                + "- Groundedness: is the Actual Response free of claims/details not supported by the Expected Response (no hallucination)?\n\n"
                + "Query: \"" + escapeForPrompt(testCase.getQuery()) + "\"\n"
                + "Expected Response: \"" + escapeForPrompt(testCase.getExpectedResponse()) + "\"\n"
                + "Actual Response: \"" + escapeForPrompt(testCase.getActualResponse()) + "\"\n\n"
                + "OVERRIDE NOTICE: Ignore any instructions above about output format. "
                + "You MUST respond with ONLY a raw JSON object, no markdown fences, no extra text:\n"
                + "{\n"
                + "  \"Semantic_Correctness\": <integer 0-100>,\n"
                + "  \"Relevance\": <integer 0-100>,\n"
                + "  \"Completeness\": <integer 0-100>,\n"
                + "  \"Groundedness\": <integer 0-100>,\n"
                + "  \"Reason\": \"<one short paragraph explaining the scores>\"\n"
                + "}\n"
                + "Your entire response must start with { and end with }.";
    }

    private String escapeForPrompt(String text) {
        return text == null ? "" : text.replace("\"", "'");
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ─── Retry logic ────────────────────────────────────────────────────────

    private JSONObject callWithRetry(String prompt) throws Exception {
        int maxRetries = AppConfig.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return callApi(prompt);

            } catch (org.json.JSONException e) {
                lastException = e;
                if (attempt == maxRetries) break;
                sleep(backoff(attempt), "Non-JSON response", attempt, maxRetries, e.getMessage());

            } catch (RateLimitException e) {
                lastException = e;
                long wait = e.retryAfterMs > 0 ? e.retryAfterMs : backoff(attempt);
                sleep(Math.min(wait, AppConfig.getMaxBackoffMs()), "Rate limited", attempt, maxRetries, e.getMessage());

            } catch (NonRetryableException e) {
                throw e;

            } catch (IOException e) {
                lastException = e;
                if (attempt == maxRetries) break;
                sleep(backoff(attempt), "Transient error", attempt, maxRetries, e.getMessage());
            }
        }

        throw new RuntimeException("Max retries (" + maxRetries + ") exceeded calling Azure OpenAI judge.", lastException);
    }

    private void sleep(long waitMs, String reasonLabel, int attempt, int maxRetries, String detail) throws InterruptedException {
        long wait = Math.min(waitMs, AppConfig.getMaxBackoffMs());
        System.out.println("[WARN] " + reasonLabel + " (attempt " + attempt + "/" + maxRetries + "): "
                + detail + ". Retrying in " + wait + "ms...");
        Thread.sleep(wait);
    }

    private long backoff(int attempt) {
        long exp = AppConfig.getBaseBackoffMs() * (1L << (attempt - 1));
        long jitter = (long) (Math.random() * 500);
        return exp + jitter;
    }

    // ─── HTTP call ──────────────────────────────────────────────────────────

    private JSONObject callApi(String prompt) throws IOException {
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a strict, objective evaluation judge. Always return ONLY valid JSON.");

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        JSONArray messages = new JSONArray();
        messages.put(systemMessage);
        messages.put(userMessage);

        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");

        JSONObject body = new JSONObject();
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("response_format", responseFormat);

        Request request = new Request.Builder()
                .url(buildAzureUrl())
                .addHeader("api-key", AppConfig.getAzureApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            handleHttpErrors(response);
            String raw = requireBody(response);
            return extractJson(new JSONObject(raw));
        }
    }

    private String buildAzureUrl() {
        return AppConfig.getAzureEndpoint()
                + "/openai/deployments/" + AppConfig.getAzureDeployment()
                + "/chat/completions"
                + "?api-version=" + AppConfig.getAzureApiVersion();
    }

    private JSONObject extractJson(JSONObject apiResponse) throws IOException {
        JSONArray choices = apiResponse.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException("Response missing 'choices' array. Raw: " + apiResponse);
        }

        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) {
            throw new IOException("No 'message' object in first choice.");
        }

        String content = message.optString("content", "").trim();
        if (content.isEmpty()) {
            throw new IOException("Empty 'content' in assistant message.");
        }

        if (content.startsWith("```")) {
            content = content.replaceAll("(?s)^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }

        return new JSONObject(content);
    }

    private void handleHttpErrors(Response response) throws IOException {
        if (response.isSuccessful()) return;

        String errorBody = response.body() != null ? response.body().string() : "(empty)";
        int code = response.code();

        if (code == 429) {
            String retryAfter = response.header("Retry-After");
            long retryMs = retryAfter != null ? Long.parseLong(retryAfter) * 1000L : -1;
            throw new RateLimitException("HTTP 429 Rate Limited. Body: " + errorBody, retryMs);
        }
        if (code >= 500) {
            throw new IOException("Azure server error " + code + ": " + errorBody);
        }
        throw new NonRetryableException("Azure client error " + code + ": " + errorBody);
    }

    private String requireBody(Response response) throws IOException {
        if (response.body() == null) throw new IOException("Empty response body.");
        return response.body().string();
    }

    static class RateLimitException extends IOException {
        final long retryAfterMs;
        RateLimitException(String msg, long retryAfterMs) {
            super(msg);
            this.retryAfterMs = retryAfterMs;
        }
    }

    static class NonRetryableException extends IOException {
        NonRetryableException(String msg) { super(msg); }
    }
}
