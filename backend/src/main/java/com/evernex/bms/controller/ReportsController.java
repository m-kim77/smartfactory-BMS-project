package com.evernex.bms.controller;

import com.evernex.bms.security.ApiException;
import com.evernex.bms.security.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public ReportsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("")
    public Map<String, Object> list() {
        var p = AuthContext.require();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT report_id, title, summary, source_session_id, llm_mode, llm_model, " +
            "message_count, car_ids, created_at FROM reports WHERE user_id=? " +
            "ORDER BY created_at DESC LIMIT 200", p.uid());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(r);
            copy.put("car_ids", parseCarIds(r.get("car_ids")));
            items.add(copy);
        }
        return Map.of("items", items);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        var p = AuthContext.require();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT report_id, user_id, title, summary, content, source_session_id, " +
            "llm_mode, llm_model, message_count, car_ids, created_at FROM reports WHERE report_id=?", id);
        if (rows.isEmpty()) throw new ApiException(404, "리포트를 찾을 수 없습니다");
        Map<String, Object> row = rows.get(0);
        long ownerId = ((Number) row.get("user_id")).longValue();
        if (ownerId != p.uid()) throw new ApiException(403, "본인이 작성한 리포트만 조회할 수 있습니다");

        Map<String, Object> out = new LinkedHashMap<>(row);
        Object content = row.get("content");
        Object parsed;
        try {
            parsed = content instanceof String s ? json.readValue(s, Map.class) : Map.of("messages", List.of());
        } catch (Exception e) {
            parsed = Map.of("messages", List.of());
        }
        out.put("content", parsed);
        out.put("car_ids", parseCarIds(row.get("car_ids")));
        return out;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        var p = AuthContext.require();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id FROM reports WHERE report_id=?", id);
        if (rows.isEmpty()) throw new ApiException(404, "리포트를 찾을 수 없습니다");
        long ownerId = ((Number) rows.get(0).get("user_id")).longValue();
        if (ownerId != p.uid()) throw new ApiException(403, "본인이 작성한 리포트만 삭제할 수 있습니다");
        jdbc.update("DELETE FROM reports WHERE report_id=?", id);
        return Map.of("ok", true);
    }

    private List<String> parseCarIds(Object raw) {
        if (!(raw instanceof String s) || s.isBlank()) return List.of();
        try {
            List<?> list = json.readValue(s, List.class);
            List<String> out = new ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
