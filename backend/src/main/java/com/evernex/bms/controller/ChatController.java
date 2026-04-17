package com.evernex.bms.controller;

import com.evernex.bms.db.TimeUtil;
import com.evernex.bms.security.ApiException;
import com.evernex.bms.security.AuthContext;
import com.evernex.bms.service.ChatService;
import com.evernex.bms.service.LlmClient;
import com.evernex.bms.service.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final String REPORT_SYSTEM_PROMPT_KO = """
        당신은 BMS 품질검사 대시보드의 리포트 작성 전문가입니다.
        사용자와 챗봇의 대화를 토대로 운영자가 빠르게 읽을 수 있는 리포트를 작성합니다.
        반드시 다음 JSON 형식으로만 응답하세요 (마크다운 코드블록 없이):
        {
          "title": "리포트 제목 (40자 이내, 핵심 주제)",
          "summary": "2~3 문장 요약",
          "key_findings": ["핵심 발견 1", "핵심 발견 2", ...],
          "action_items": ["권장 액션 1", "권장 액션 2", ...],
          "data_points": ["인용 데이터 (차량 ID, 수치 등)", ...]
        }
        제공된 대화에 없는 내용은 절대 만들어내지 마세요. 데이터/숫자/차량ID는 대화에 등장한 그대로 인용하세요.""";

    private static final String REPORT_SYSTEM_PROMPT_EN = """
        You are a report writing specialist for the BMS quality inspection dashboard.
        Write a concise report from the user-chatbot conversation that an operator can read quickly.
        Respond ONLY with JSON in this format (no markdown code blocks):
        {
          "title": "Report title (under 40 chars, core topic)",
          "summary": "2-3 sentence summary",
          "key_findings": ["Finding 1", "Finding 2", ...],
          "action_items": ["Recommended action 1", ...],
          "data_points": ["Cited data (vehicle IDs, values, etc.)", ...]
        }
        NEVER fabricate content not in the conversation. Cite data/numbers/vehicle IDs verbatim.""";

    private final ChatService chat;
    private final LlmClient llm;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public ChatController(ChatService chat, LlmClient llm, SettingsService settings, JdbcTemplate jdbc) {
        this.chat = chat;
        this.llm = llm;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    @PostMapping("")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) throws Exception {
        var p = AuthContext.require();
        String message = (String) body.get("message");
        if (message == null || message.isBlank()) throw new ApiException(400, "message 필수");
        String sessionId = (String) body.get("session_id");
        String locale = (String) body.get("locale");

        String mode = chat.currentMode();
        try {
            if ("text_to_sql".equals(mode)) {
                return chat.handleTextToSql(p.uid(), sessionId, message, locale,
                    p.allowedFactoryIds(), p.adminScope());
            }
            return chat.handleRagLite(p.uid(), sessionId, message, locale);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            String hint = "LLM 서버에 연결할 수 없습니다. LM Studio가 실행 중인지 확인해주세요.";
            if (msg != null && msg.matches("(?is).*(ECONNREFUSED|Connection refused|fetch failed|ENOTFOUND|Unable to connect).*")) {
                hint = "LM Studio(" + chat.baseUrl() + ")에 연결 실패. 서버가 실행 중인지 확인하세요.";
            } else if (msg != null && msg.matches("(?is).*(timeout|aborted|timed out).*")) {
                hint = "LLM 응답 대기 시간 초과. (모델 첫 추론 cold start일 수 있음 — 잠시 후 다시 시도)";
            }
            throw new ApiException(502, hint, msg);
        }
    }

    @PostMapping("/report")
    public Map<String, Object> report(@RequestBody Map<String, Object> body) throws Exception {
        var p = AuthContext.require();
        Object raw = body.get("messages");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new ApiException(400, "messages 배열이 비어있습니다");
        }
        String sessionId = (String) body.get("session_id");
        String locale = (String) body.get("locale");
        String userTitle = (String) body.get("title");
        boolean isEn = "en".equals(locale);

        List<Map<String, Object>> cleaned = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object role = m.get("role");
            Object content = m.get("content");
            if (!("user".equals(role) || "assistant".equals(role))) continue;
            if (!(content instanceof String cs) || cs.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", (String) role);
            row.put("content", cs.trim());
            // Text-to-SQL assistant 메시지에 붙은 SQL/근거 메타 보존
            if ("assistant".equals(role)) {
                Object sql = m.get("sql");
                if (sql instanceof String sqlStr && !sqlStr.isBlank()) {
                    row.put("sql", sqlStr);
                }
                Object reasoning = m.get("reasoning");
                if (reasoning instanceof String rs && !rs.isBlank()) {
                    row.put("reasoning", rs);
                }
                Object rowsCount = m.get("rows_count");
                if (rowsCount instanceof Number n) {
                    row.put("rows_count", n.intValue());
                }
                Object rows = m.get("rows");
                if (rows instanceof List<?> rl && !rl.isEmpty()) {
                    row.put("rows", rl);
                }
            }
            cleaned.add(row);
        }
        if (cleaned.isEmpty()) throw new ApiException(400, "유효한 메시지가 없습니다");

        StringBuilder convo = new StringBuilder();
        for (int i = 0; i < cleaned.size(); i++) {
            Map<String, Object> m = cleaned.get(i);
            String tag = "user".equals(m.get("role"))
                ? (isEn ? "User" : "사용자")
                : (isEn ? "Assistant" : "챗봇");
            if (i > 0) convo.append("\n\n");
            convo.append("[").append(i + 1).append("] ").append(tag).append(":\n").append(m.get("content"));
        }

        Set<String> carIdSet = new LinkedHashSet<>();
        Pattern carPat = Pattern.compile("VH-\\d{8}-\\d{4}");
        for (Map<String, Object> m : cleaned) {
            Matcher matcher = carPat.matcher(String.valueOf(m.get("content")));
            while (matcher.find()) carIdSet.add(matcher.group());
        }
        List<String> carIds = new ArrayList<>(carIdSet);

        Map<String, Object> summaryJson = new LinkedHashMap<>();
        String llmError = null;
        try {
            String userContent = isEn
                ? ("Below is a conversation between an operator and the BMS chatbot. Generate a JSON report.\n\n--- Conversation ---\n" + convo + "\n--- End ---")
                : ("아래는 운영자와 BMS 챗봇의 대화입니다. JSON 리포트를 생성하세요.\n\n--- 대화 시작 ---\n" + convo + "\n--- 대화 끝 ---");
            LlmClient.Result r = llm.call(chat.llmConfig(),
                isEn ? REPORT_SYSTEM_PROMPT_EN : REPORT_SYSTEM_PROMPT_KO, userContent, 2000);
            JsonNode node = chat.extractJson(r.answer());
            summaryJson.put("title", node.path("title").asText(null));
            summaryJson.put("summary", node.path("summary").asText(""));
            summaryJson.put("key_findings", asStringList(node.path("key_findings")));
            summaryJson.put("action_items", asStringList(node.path("action_items")));
            summaryJson.put("data_points", asStringList(node.path("data_points")));
        } catch (Exception e) {
            llmError = String.valueOf(e.getMessage());
            if (llmError != null && llmError.length() > 200) llmError = llmError.substring(0, 200);
            summaryJson.put("title", userTitle != null && !userTitle.isBlank()
                ? userTitle : (isEn ? "Conversation Report" : "대화 리포트"));
            summaryJson.put("summary", isEn
                ? "(Auto-summary failed; raw conversation preserved.)"
                : "(자동 요약 실패 — 원본 대화는 보존됨.)");
            summaryJson.put("key_findings", List.of());
            summaryJson.put("action_items", List.of());
            summaryJson.put("data_points", List.of());
        }

        String finalTitle;
        if (userTitle != null && !userTitle.isBlank()) {
            finalTitle = userTitle;
        } else {
            Object t = summaryJson.get("title");
            finalTitle = t instanceof String s && !s.isBlank() ? s : (isEn ? "Untitled Report" : "제목 없음");
        }
        if (finalTitle.length() > 200) finalTitle = finalTitle.substring(0, 200);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("summary", summaryJson.get("summary"));
        content.put("key_findings", summaryJson.get("key_findings"));
        content.put("action_items", summaryJson.get("action_items"));
        content.put("data_points", summaryJson.get("data_points"));
        content.put("messages", cleaned);
        content.put("locale", isEn ? "en" : "ko");
        content.put("generated_at", TimeUtil.nowISO());
        content.put("_llm_error", llmError);

        String summaryText = String.valueOf(summaryJson.getOrDefault("summary", ""));
        if (summaryText.length() > 500) summaryText = summaryText.substring(0, 500);

        org.springframework.jdbc.support.GeneratedKeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        final String finalTitleF = finalTitle;
        final String summaryTextF = summaryText;
        final String contentJson = json.writeValueAsString(content);
        final String carIdsJson = json.writeValueAsString(carIds);
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO reports (user_id, title, summary, content, source_session_id, llm_mode, llm_model, message_count, car_ids, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, p.uid());
            ps.setString(2, finalTitleF);
            ps.setString(3, summaryTextF);
            ps.setString(4, contentJson);
            if (sessionId == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, sessionId);
            ps.setString(6, chat.currentMode());
            ps.setString(7, chat.currentModel());
            ps.setInt(8, cleaned.size());
            ps.setString(9, carIdsJson);
            ps.setString(10, TimeUtil.nowISO());
            return ps;
        }, kh);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("report_id", kh.getKey() == null ? null : kh.getKey().longValue());
        resp.put("title", finalTitle);
        return resp;
    }

    private static List<String> asStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) out.add(n.asText(""));
        }
        return out;
    }
}
