package com.evernex.bms.controller;

import com.evernex.bms.security.ApiException;
import com.evernex.bms.security.AuthContext;
import com.evernex.bms.service.LlmClient;
import com.evernex.bms.service.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private static final String FACTORY_ORDER =
        "CASE factory_name " +
        "WHEN '청림공장' THEN 1 " +
        "WHEN '은하공장' THEN 2 " +
        "WHEN '백운공장' THEN 3 " +
        "WHEN '단풍공장' THEN 4 " +
        "WHEN '태양공장' THEN 5 " +
        "WHEN '한빛공장' THEN 6 " +
        "ELSE 99 END";

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Value("${bms.llm.base-url:http://127.0.0.1:1234}")
    private String llmBaseUrl;

    public SettingsController(JdbcTemplate jdbc, SettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    @GetMapping("")
    public Map<String, Object> list() {
        AuthContext.require();
        return Map.of("items", settings.list());
    }

    @PutMapping("/{key}")
    public Map<String, Object> update(@PathVariable String key, @RequestBody Map<String, Object> body) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        if (!body.containsKey("value")) throw new ApiException(400, "value 필수");
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_settings WHERE setting_key=?", Integer.class, key);
        if (exists == null || exists == 0) throw new ApiException(404, "설정 키 없음");
        settings.set(key, body.get("value"), p.uid());
        return Map.of("ok", true);
    }

    @GetMapping("/countries")
    public Map<String, Object> countries() {
        AuthContext.require();
        return Map.of("items", jdbc.queryForList("SELECT * FROM countries ORDER BY country_name"));
    }

    @PutMapping("/countries/{id}")
    public Map<String, Object> updateCountry(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        Object v = body.get("is_allowed");
        int flag = Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v)) || Integer.valueOf(1).equals(v) ? 1 : 0;
        jdbc.update("UPDATE countries SET is_allowed=? WHERE country_id=?", flag, id);
        return Map.of("ok", true);
    }

    @GetMapping("/factories")
    public Map<String, Object> factories() {
        var p = AuthContext.require();
        List<Long> allowed = p.allowedFactoryIds();
        if (allowed == null || allowed.isEmpty()) return Map.of("items", List.of());
        String ph = String.join(",", Collections.nCopies(allowed.size(), "?"));
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT * FROM factories WHERE factory_id IN (" + ph + ") ORDER BY " + FACTORY_ORDER,
            allowed.toArray());
        return Map.of("items", items);
    }

    @GetMapping("/factories/all")
    public Map<String, Object> factoriesAll() {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        return Map.of("items", jdbc.queryForList(
            "SELECT * FROM factories WHERE is_active=1 ORDER BY " + FACTORY_ORDER));
    }

    private static final List<String> OPENAI_MODELS = List.of(
        "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4.1", "gpt-4.1-mini",
        "gpt-3.5-turbo", "o1-mini", "o3-mini"
    );

    private static final List<String> GEMINI_MODELS = List.of(
        "gemini-2.0-flash", "gemini-2.0-flash-lite",
        "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-1.5-pro"
    );

    @GetMapping("/llm/models")
    public Map<String, Object> llmModels() {
        AuthContext.require();
        String provider = settings.getString("llm_provider", LlmClient.PROVIDER_LM_STUDIO);
        if (LlmClient.PROVIDER_OPENAI.equals(provider)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (String id : OPENAI_MODELS) items.add(Map.of("id", id));
            return Map.of("items", items);
        }
        if (LlmClient.PROVIDER_GEMINI.equals(provider)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (String id : GEMINI_MODELS) items.add(Map.of("id", id));
            return Map.of("items", items);
        }
        String dbUrl = settings.getString("llm_base_url", "");
        String base = (dbUrl != null && !dbUrl.isBlank()) ? dbUrl : llmBaseUrl;
        base = base.replaceAll("/+$", "");
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/models"))
                .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                throw new ApiException(502, "LM Studio 응답 오류");
            }
            JsonNode node = json.readTree(r.body());
            List<Map<String, Object>> items = new ArrayList<>();
            JsonNode data = node.path("data");
            if (data.isArray()) {
                for (JsonNode m : data) {
                    items.add(Map.of("id", m.path("id").asText("")));
                }
            }
            return Map.of("items", items);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "LM Studio에 연결할 수 없습니다.", String.valueOf(e.getMessage()));
        }
    }
}
