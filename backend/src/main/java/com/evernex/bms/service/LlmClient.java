package com.evernex.bms.service;

import com.evernex.bms.security.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmClient {

    public static final String PROVIDER_LM_STUDIO = "lm_studio";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_GEMINI = "gemini";

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";

    private final SettingsService settings;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public LlmClient(SettingsService settings) { this.settings = settings; }

    public record Result(String answer, JsonNode raw) {}
    public record Config(String provider, String baseURL, String model, String apiKey) {}

    public static String providerLabel(String provider) {
        return switch (provider == null ? "" : provider) {
            case PROVIDER_OPENAI -> "OpenAI";
            case PROVIDER_GEMINI -> "Google Gemini";
            default -> "LM Studio";
        };
    }

    private static String endpointFor(Config cfg) {
        return switch (cfg.provider() == null ? "" : cfg.provider()) {
            case PROVIDER_OPENAI -> OPENAI_ENDPOINT;
            case PROVIDER_GEMINI -> GEMINI_ENDPOINT;
            default -> {
                String base = cfg.baseURL() == null ? "" : cfg.baseURL().replaceAll("/+$", "");
                yield base + "/v1/chat/completions";
            }
        };
    }

    public Result call(Config cfg, String systemPrompt, String userContent, Integer maxTokensOverride) {
        String provider = cfg.provider() == null ? PROVIDER_LM_STUDIO : cfg.provider();
        String label = providerLabel(provider);
        boolean needsKey = PROVIDER_OPENAI.equals(provider) || PROVIDER_GEMINI.equals(provider);
        if (needsKey && (cfg.apiKey() == null || cfg.apiKey().isBlank())) {
            throw new ApiException(502, label + " API 키가 설정되지 않았습니다.",
                "설정 > LLM 에서 API 키를 입력하세요.");
        }

        int effectiveMax = (maxTokensOverride != null && maxTokensOverride > 0)
            ? maxTokensOverride
            : Math.max(100, Math.min(32000, settings.getInt("llm_max_tokens", 3000)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.model());
        body.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.2);
        body.put("max_tokens", effectiveMax);

        String reqJson;
        try { reqJson = json.writeValueAsString(body); }
        catch (Exception e) { throw new ApiException(500, "LLM 요청 직렬화 실패", e.getMessage()); }

        String url = endpointFor(cfg);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(reqJson));
        if (needsKey) builder.header("Authorization", "Bearer " + cfg.apiKey());

        HttpRequest req = builder.build();

        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new ApiException(502, label + "(" + url + ") 연결 실패. 서버/네트워크 상태를 확인하세요.",
                e.getMessage());
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            String hint = label + " 서버에 연결할 수 없습니다.";
            if (m != null && m.matches("(?i).*(timeout|aborted).*")) {
                hint = label + " 응답 대기 시간 초과. (모델 첫 추론 cold start일 수 있음)";
            }
            throw new ApiException(502, hint, m);
        }

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String text = res.body() == null ? "" : res.body();
            String hint = label + " 응답 오류 (HTTP " + res.statusCode() + ")";
            if (PROVIDER_LM_STUDIO.equals(provider)) {
                if (text.matches("(?is).*No models loaded.*")) {
                    hint = "LM Studio에 로드된 모델이 없습니다. 모델을 로드해주세요.";
                } else if (text.matches("(?is).*model.*not found.*")) {
                    hint = "요청한 모델(" + cfg.model() + ")을 찾을 수 없습니다.";
                } else if (text.matches("(?is).*(n_keep|n_ctx|context length|context_length|too many tokens|exceeds.*context).*")) {
                    hint = "LLM 모델의 컨텍스트 길이가 부족합니다. LM Studio에서 모델을 더 큰 컨텍스트(권장 8192 이상)로 로드하세요.";
                }
            } else if (res.statusCode() == 401 || res.statusCode() == 403) {
                hint = label + " 인증 실패 — API 키를 확인하세요.";
            } else if (res.statusCode() == 429) {
                hint = label + " 요청 제한 초과 — 잠시 후 다시 시도하세요.";
            } else if (res.statusCode() == 404) {
                hint = label + " 모델(" + cfg.model() + ")을 찾을 수 없습니다.";
            }
            throw new ApiException(502, hint, text.substring(0, Math.min(400, text.length())));
        }

        JsonNode data;
        try { data = json.readTree(res.body()); }
        catch (Exception e) { throw new ApiException(502, "LLM 응답 파싱 실패", e.getMessage()); }

        JsonNode msg = data.path("choices").path(0).path("message");
        String answer = msg.path("content").asText("");
        if (answer.isBlank()) {
            String reasoning = msg.path("reasoning_content").asText("");
            if (!reasoning.isBlank()) {
                answer = reasoning;
            } else {
                String finish = data.path("choices").path(0).path("finish_reason").asText("");
                if ("length".equals(finish)) {
                    throw new ApiException(502, "응답이 토큰 한도에 도달했습니다.",
                        "finish_reason=length, usage=" + data.path("usage"));
                }
                throw new ApiException(502, "LLM이 빈 응답을 반환했습니다.", "finish_reason=" + finish);
            }
        }
        return new Result(answer, data);
    }
}
