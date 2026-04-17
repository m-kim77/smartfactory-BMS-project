package com.evernex.bms.service;

import com.evernex.bms.db.FactoryScope;
import com.evernex.bms.db.FactoryScope.Clause;
import com.evernex.bms.db.TimeUtil;
import com.evernex.bms.domain.Constants;
import com.evernex.bms.security.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of routes/chat.js (business logic extracted out of the controller). */
@Service
public class ChatService {

    public static final String SYSTEM_PROMPT_KO = """
        You are a BMS quality inspection assistant for EverNex smart factory.
        Rules:
        - Answer ONLY based on provided data. Never invent data.
        - Provide evidence (vehicle IDs, values, timestamps).
        - When suggesting actions, specify vehicle and action.
        - Use Korean.
        - Be concise and actionable.""";

    public static final String SYSTEM_PROMPT_EN = """
        You are a BMS quality inspection assistant for EverNex smart factory.
        Rules:
        - Answer ONLY based on provided data. Never invent data.
        - Provide evidence (vehicle IDs, values, timestamps).
        - When suggesting actions, specify vehicle and action.
        - Respond in English only. Translate any Korean factory/model/country names into their English equivalents (e.g., 청림공장 → Cheongrim Plant, 노바 X5 → Nova X5, 에버랜드 → EverLand, 노바 → Nova, 벡터 → Vector).
        - Be concise and actionable.""";

    public static final String SQL_SYSTEM_PROMPT = """
        You are a SQLite SQL generator for a BMS (Battery Management System) factory database.
        Your job: convert the user's Korean question into a single SELECT query.

        STRICT RULES:
        - Return ONLY a JSON object: {"sql": "<SELECT ...>", "reasoning": "<short why>"}
        - SQL must be a single SELECT statement. No INSERT/UPDATE/DELETE/DROP.
        - Use only the tables and columns shown in the schema below.
        - Prefer explicit JOINs over subqueries when possible.
        - Add LIMIT 100 if the query could return many rows.
        - Do NOT include semicolons at the end.
        - Do NOT wrap SQL in markdown code blocks.""";

    public static final String SCHEMA_PROMPT = """
        # Schema (SQLite)
        cars(car_id PK, model_name, factory_id FK, destination_country, production_date, current_status, created_at, updated_at)
        factories(factory_id PK, factory_name, region, country, brand, is_active)
        batteries(battery_id PK, car_id FK, battery_serial_number, manufacture_date)
        battery_measurements(measurement_id PK, battery_id FK, soc, soh, sop, avg_voltage, avg_temperature, inspected_at)
        battery_cells(cell_id PK, battery_id FK, cell_number)
        battery_cell_measurements(cell_measurement_id PK, cell_id FK, cell_temperature, cell_voltage, measured_at)
        alerts(alert_id PK, car_id FK, alert_type, alert_message, severity, current_status, occurred_at, resolved_at)
        countries(country_id PK, country_name, country_code, is_allowed)
        car_status_histories(car_status_history_id PK, car_id, status, changed_at, changed_by_user_id)
        alert_status_histories(history_id PK, alert_id, status, changed_at, changed_by_user_id)
        process_step_histories(process_history_id PK, car_id, step_name, step_status, started_at, ended_at)
        inspection_results(result_id PK, car_id, status, performance_status, safety_status, finalized_at)

        # Enum values (use English UPPERCASE only in WHERE)
        cars.current_status: ARRIVAL(입고) | BATTERY_INSPECTION(배터리검사중) | CELL_INSPECTION(셀검사중) | ANOMALY_DETECTED(이상) | QA_MAINTENANCE(정비중) | RE_INSPECTION_WAITING(재검사대기) | RE_INSPECTION(재검사중) | BATTERY_QC_COMPLETE(QC완료) | SHIPMENT_WAITING(출고대기) | SHIPMENT_COMPLETE(출고완료)
        alerts.severity: LOW | MEDIUM | HIGH | CRITICAL
        alerts.current_status: OPEN | ACKNOWLEDGED | RESOLVED

        # Notes
        - car_id format: VH-YYYYMMDD-NNNN
        - factory_name (Korean): 청림공장, 은하공장, 백운공장, 단풍공장, 태양공장, 한빛공장
        - model_name (Korean): 노바 X5, 벡터 E6, 볼트 S, etc.
        - NEVER use Korean labels in WHERE — only ENGLISH UPPERCASE codes for status/severity.
        - Date functions: datetime('now', '-1 hour'), date('now')
        """;

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "cars", "batteries", "battery_measurements", "alerts",
        "factories", "countries", "battery_cells", "battery_cell_measurements",
        "car_status_histories", "alert_status_histories",
        "process_step_histories", "inspection_results");

    private static final Set<String> SCOPED_TABLES = Set.of(
        "cars", "alerts", "batteries", "battery_measurements",
        "battery_cells", "battery_cell_measurements",
        "car_status_histories", "alert_status_histories",
        "process_step_histories", "inspection_results");

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final LlmClient llm;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${bms.llm.base-url:http://127.0.0.1:1234}")
    private String defaultBaseUrl;

    @Value("${bms.llm.model:local-model}")
    private String defaultModel;

    public ChatService(JdbcTemplate jdbc, SettingsService settings, LlmClient llm) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.llm = llm;
    }

    public String systemPromptForLocale(String locale) {
        return "en".equals(locale) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_KO;
    }

    public String baseUrl() {
        String dbUrl = settings.getString("llm_base_url", "");
        return (dbUrl != null && !dbUrl.isBlank()) ? dbUrl : defaultBaseUrl;
    }
    public String currentModel() { return settings.getString("llm_model", defaultModel); }
    public String currentMode() { return settings.getString("llm_mode", "rag_lite"); }
    public String currentProvider() { return settings.getString("llm_provider", LlmClient.PROVIDER_LM_STUDIO); }

    public LlmClient.Config llmConfig() {
        String provider = currentProvider();
        String apiKey = switch (provider) {
            case LlmClient.PROVIDER_OPENAI -> settings.getString("llm_openai_api_key", "");
            case LlmClient.PROVIDER_GEMINI -> settings.getString("llm_gemini_api_key", "");
            default -> null;
        };
        return new LlmClient.Config(provider, baseUrl(), currentModel(), apiKey);
    }

    public void validateSql(String sql, List<Long> allowedFactoryIds, boolean isAdminScope) {
        String clean = sql
            .replaceAll("(?m)--.*$", "")
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .trim();
        if (!clean.matches("(?is)^\\s*SELECT\\b.*")) {
            throw new ApiException(502, "SELECT 쿼리만 허용됩니다");
        }
        int semiIdx = clean.indexOf(';');
        if (semiIdx != -1 && semiIdx < clean.length() - 1) {
            throw new ApiException(502, "다중 구문은 허용되지 않습니다");
        }

        List<String> tables = new ArrayList<>();
        Matcher m = Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([a-zA-Z_][\\w]*)").matcher(clean);
        while (m.find()) tables.add(m.group(1).toLowerCase());
        for (String t : tables) {
            if (!ALLOWED_TABLES.contains(t)) throw new ApiException(502, "허용되지 않는 테이블: " + t);
        }

        if (isAdminScope) return;
        boolean touchesScoped = tables.stream().anyMatch(SCOPED_TABLES::contains);
        if (!touchesScoped) return;
        if (!tables.contains("cars")) {
            throw new ApiException(502, "차량 관련 조회는 cars 테이블을 JOIN해야 합니다 (권한 검증용)");
        }
        Matcher in = Pattern.compile("(?i)factory_id\\s+IN\\s*\\(([^)]+)\\)").matcher(clean);
        if (!in.find()) {
            throw new ApiException(502, "factory_id 필터 누락 — 차량/배터리/경보 관련 조회 시 cars.factory_id IN (...) 필수");
        }
        List<Long> requestedIds = new ArrayList<>();
        for (String s : in.group(1).split(",")) {
            try { requestedIds.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        Set<Long> allowedSet = new HashSet<>(allowedFactoryIds);
        List<Long> oos = new ArrayList<>();
        for (Long id : requestedIds) if (!allowedSet.contains(id)) oos.add(id);
        if (!oos.isEmpty()) {
            throw new ApiException(502, "허용되지 않은 공장: " + oos);
        }
    }

    public String injectLimit(String sql, int defaultLimit) {
        String stripped = sql.replaceAll(";\\s*$", "").trim();
        if (stripped.matches("(?is).*\\bLIMIT\\s+\\d+.*")) return stripped;
        return stripped + " LIMIT " + defaultLimit;
    }

    public JsonNode extractJson(String text) {
        Matcher fenced = Pattern.compile("(?is)```(?:json)?\\s*(.*?)```").matcher(text);
        String candidate = fenced.find() ? fenced.group(1) : text;
        Matcher obj = Pattern.compile("(?s)\\{.*\\}").matcher(candidate);
        if (!obj.find()) throw new ApiException(502, "JSON 객체를 찾을 수 없습니다");
        try {
            return json.readTree(obj.group());
        } catch (Exception e) {
            throw new ApiException(502, "JSON 파싱 실패", e.getMessage());
        }
    }

    public String buildScopePromptAddendum(List<Long> allowed, boolean isAdminScope) {
        if (isAdminScope) return "";
        if (allowed == null || allowed.isEmpty()) {
            return "\n\n## 보안 규칙 (필수)\n현재 사용자는 어떤 공장 데이터도 접근할 수 없습니다. 차량/배터리/경보 조회 시 결과가 반드시 0건이 되어야 합니다. SQL: SELECT 0 AS no_access LIMIT 1 으로 응답하세요.";
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < allowed.size(); i++) {
            if (i > 0) list.append(", ");
            list.append(allowed.get(i));
        }
        String l = list.toString();
        return "\n\n## 보안 규칙 (필수)\n현재 사용자는 factory_id IN (" + l + ") 인 공장 데이터만 조회할 수 있습니다.\n"
            + "- 차량 관련 테이블(cars, alerts, batteries, battery_measurements, battery_cells, battery_cell_measurements, car_status_histories, alert_status_histories, process_step_histories, inspection_results) 중 하나라도 참조하면 반드시 cars 테이블을 JOIN하고 `WHERE cars.factory_id IN (" + l + ")` 조건을 추가해야 합니다.\n"
            + "- 예: `SELECT bm.soc FROM battery_measurements bm JOIN batteries b ON b.battery_id=bm.battery_id JOIN cars c ON c.car_id=b.car_id WHERE c.factory_id IN (" + l + ")`\n"
            + "- alerts 조회 시도 cars JOIN 필수: `JOIN cars c ON c.car_id=a.car_id WHERE c.factory_id IN (" + l + ")`\n"
            + "- 위 IN-list에 없는 factory_id를 SQL에 절대 포함하지 마세요. 검증기가 차단합니다.\n"
            + "- factories/countries 메타 테이블만 조회하는 경우는 예외입니다.";
    }

    public Map<String, Object> buildContext(String message, List<Long> allowedFactoryIds) {
        Clause fScope = FactoryScope.clause(allowedFactoryIds, "factory_id");
        Clause aScope = FactoryScope.clause(allowedFactoryIds, "c.factory_id");

        int alertsLimit = Math.max(1, Math.min(500, settings.getInt("llm_context_alerts", 8)));
        int carsLimit = Math.max(1, Math.min(500, settings.getInt("llm_context_cars", 20)));
        int msgMax = Math.max(0, Math.min(2000, settings.getInt("llm_alert_msg_max", 80)));

        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cars WHERE 1=1" + fScope.sql(), Integer.class, fScope.params().toArray());
        List<Map<String, Object>> statusRows = jdbc.queryForList(
            "SELECT current_status AS status, COUNT(*) AS count FROM cars WHERE 1=1" + fScope.sql() + " GROUP BY current_status",
            fScope.params().toArray());

        String msgExpr = msgMax > 0 ? "substr(a.alert_message, 1, " + msgMax + ")" : "a.alert_message";
        List<Map<String, Object>> openAlerts = jdbc.queryForList(
            "SELECT a.alert_id, a.car_id, a.alert_type, " + msgExpr + " AS alert_message, a.severity, a.occurred_at, a.current_status, c.model_name " +
            "FROM alerts a LEFT JOIN cars c ON c.car_id=a.car_id " +
            "WHERE a.current_status!='RESOLVED'" + aScope.sql() +
            " ORDER BY a.occurred_at DESC LIMIT " + alertsLimit,
            aScope.params().toArray());

        List<Map<String, Object>> filteredCars = new ArrayList<>();
        Object[][] keywordToStatus = {
            {"검사중", new String[]{Constants.BATTERY_INSPECTION, Constants.CELL_INSPECTION, Constants.RE_INSPECTION}},
            {"출고대기", new String[]{Constants.SHIPMENT_WAITING}},
            {"출고완료", new String[]{Constants.SHIPMENT_COMPLETE}},
            {"이상", new String[]{Constants.ANOMALY_DETECTED, Constants.QA_MAINTENANCE}},
            {"경고", new String[]{Constants.ANOMALY_DETECTED, Constants.QA_MAINTENANCE}},
            {"입고", new String[]{Constants.ARRIVAL}}
        };
        if (message != null) {
            for (Object[] kv : keywordToStatus) {
                String kw = (String) kv[0];
                if (message.contains(kw)) {
                    String[] statuses = (String[]) kv[1];
                    if (allowedFactoryIds != null && !allowedFactoryIds.isEmpty()) {
                        String ph = String.join(",", java.util.Collections.nCopies(statuses.length, "?"));
                        List<Object> params = new ArrayList<>(Arrays.asList(statuses));
                        params.addAll(fScope.params());
                        filteredCars = jdbc.queryForList(
                            "SELECT car_id, model_name, current_status, destination_country FROM cars " +
                            "WHERE current_status IN (" + ph + ")" + fScope.sql() +
                            " ORDER BY updated_at DESC LIMIT " + carsLimit,
                            params.toArray());
                    }
                    break;
                }
            }
        }

        Map<String, Object> selectedVehicle = null;
        if (message != null) {
            Matcher m = Pattern.compile("(?i)VH-\\d{8}-\\d{4}").matcher(message);
            if (m.find()) {
                String carId = m.group().toUpperCase();
                List<Object> p = new ArrayList<>();
                p.add(carId);
                p.addAll(fScope.params());
                List<Map<String, Object>> carRows = jdbc.queryForList(
                    "SELECT * FROM cars WHERE car_id=?" + fScope.sql(), p.toArray());
                if (!carRows.isEmpty()) {
                    Map<String, Object> car = carRows.get(0);
                    List<Map<String, Object>> bRows = jdbc.queryForList("SELECT * FROM batteries WHERE car_id=?", carId);
                    Map<String, Object> measurement = null;
                    if (!bRows.isEmpty()) {
                        Long bid = ((Number) bRows.get(0).get("battery_id")).longValue();
                        List<Map<String, Object>> mRows = jdbc.queryForList(
                            "SELECT * FROM battery_measurements WHERE battery_id=? ORDER BY inspected_at DESC LIMIT 1", bid);
                        if (!mRows.isEmpty()) measurement = mRows.get(0);
                    }
                    selectedVehicle = new LinkedHashMap<>();
                    selectedVehicle.put("car", car);
                    selectedVehicle.put("measurement", measurement);
                }
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total == null ? 0 : total);
        stats.put("byStatus", statusRows);

        List<Map<String, Object>> alertsOut = new ArrayList<>();
        for (Map<String, Object> a : openAlerts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("car_id", a.get("car_id"));
            row.put("type", a.get("alert_type"));
            row.put("msg", a.get("alert_message"));
            row.put("sev", a.get("severity"));
            row.put("at", a.get("occurred_at"));
            alertsOut.add(row);
        }

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("stats", stats);
        ctx.put("openAlerts", alertsOut);
        ctx.put("filteredCars", filteredCars);
        ctx.put("selectedVehicle", selectedVehicle);
        return ctx;
    }

    public Map<String, Object> handleRagLite(long userId, String sessionId, String message, String locale) throws Exception {
        Map<String, Object> context = buildContext(message, com.evernex.bms.security.AuthContext.require().allowedFactoryIds());
        String contextText = json.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        String qLabel = "en".equals(locale) ? "User question" : "사용자 질문";
        String dLabel = "en".equals(locale) ? "Reference data (JSON)" : "참고 데이터 (JSON)";
        String userContent = qLabel + ": " + message + "\n\n" + dLabel + ":\n" + contextText;

        LlmClient.Result r = llm.call(llmConfig(), systemPromptForLocale(locale), userContent, null);

        Map<String, Object> logCtx = new LinkedHashMap<>();
        logCtx.put("mode", "rag_lite");
        logCtx.putAll(context);
        String ctxJson = json.writeValueAsString(logCtx);
        jdbc.update(
            "INSERT INTO llm_chat_logs (user_id,session_id,user_message,assistant_message,context_data,created_at) VALUES (?,?,?,?,?,?)",
            userId, sessionId, message, r.answer(),
            ctxJson.substring(0, Math.min(4000, ctxJson.length())),
            TimeUtil.nowISO());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("answer", r.answer());
        resp.put("context", context);
        resp.put("mode", "rag_lite");
        return resp;
    }

    public Map<String, Object> handleTextToSql(long userId, String sessionId, String message, String locale,
                                                List<Long> allowed, boolean isAdminScope) throws Exception {
        String scopeAddendum = buildScopePromptAddendum(allowed, isAdminScope);
        String qLabel = "en".equals(locale) ? "User question" : "사용자 질문";
        String jsonHint = "en".equals(locale) ? "Respond with JSON only" : "JSON으로만 답하세요";
        String sqlUserContent = SCHEMA_PROMPT + scopeAddendum + "\n\n" + qLabel + ": " + message
            + "\n\n" + jsonHint + ": {\"sql\": \"...\", \"reasoning\": \"...\"}";
        LlmClient.Config cfg = llmConfig();
        LlmClient.Result sqlStep = llm.call(cfg, SQL_SYSTEM_PROMPT, sqlUserContent, null);

        JsonNode parsed;
        try {
            parsed = extractJson(sqlStep.answer());
        } catch (ApiException e) {
            throw new ApiException(502, "LLM이 생성한 SQL JSON을 파싱할 수 없습니다: " + e.getMessage(),
                sqlStep.answer().substring(0, Math.min(600, sqlStep.answer().length())));
        }
        JsonNode sqlNode = parsed.path("sql");
        if (sqlNode.isMissingNode() || !sqlNode.isTextual()) {
            throw new ApiException(502, "LLM 응답에 sql 필드가 없습니다", parsed.toString().substring(0, Math.min(600, parsed.toString().length())));
        }
        String rawSql = sqlNode.asText();
        try {
            validateSql(rawSql, allowed, isAdminScope);
        } catch (ApiException e) {
            throw new ApiException(502, "SQL 검증 실패: " + e.getMessage(), "생성된 SQL: " + rawSql);
        }
        String finalSql = injectLimit(rawSql, 100);

        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(finalSql);
        } catch (Exception e) {
            throw new ApiException(502, "SQL 실행 실패: " + e.getMessage(), "SQL: " + finalSql);
        }

        String resultsText = json.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
        String answerUserContent = "en".equals(locale)
            ? ("User question: " + message + "\n\nExecuted SQL:\n" + finalSql + "\n\nQuery results (" + rows.size() + " rows, JSON):\n" + resultsText + "\n\nBased on the results above, answer concisely in English. If the result is empty, say \"No matching data found\". Translate Korean factory/model/country names to English.")
            : ("사용자 질문: " + message + "\n\n실행한 SQL:\n" + finalSql + "\n\n쿼리 결과 (" + rows.size() + "건, JSON):\n" + resultsText + "\n\n위 결과를 바탕으로 한국어로 간결하게 답변하세요. 결과가 비어있으면 \"해당하는 데이터가 없습니다\"라고 알려주세요.");

        LlmClient.Result ans = llm.call(cfg, systemPromptForLocale(locale), answerUserContent, null);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("mode", "text_to_sql");
        context.put("sql", finalSql);
        context.put("reasoning", parsed.path("reasoning").isMissingNode() ? null : parsed.path("reasoning").asText());
        context.put("rows_count", rows.size());
        context.put("rows", rows.subList(0, Math.min(50, rows.size())));

        String ctxJson = json.writeValueAsString(context);
        jdbc.update(
            "INSERT INTO llm_chat_logs (user_id,session_id,user_message,assistant_message,context_data,created_at) VALUES (?,?,?,?,?,?)",
            userId, sessionId, message, ans.answer(),
            ctxJson.substring(0, Math.min(4000, ctxJson.length())),
            TimeUtil.nowISO());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("answer", ans.answer());
        resp.put("context", context);
        resp.put("mode", "text_to_sql");
        return resp;
    }
}
