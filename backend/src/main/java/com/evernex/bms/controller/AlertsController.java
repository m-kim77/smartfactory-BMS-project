package com.evernex.bms.controller;

import com.evernex.bms.db.FactoryScope;
import com.evernex.bms.db.FactoryScope.Clause;
import com.evernex.bms.db.TimeUtil;
import com.evernex.bms.security.ApiException;
import com.evernex.bms.security.AuthContext;
import com.evernex.bms.service.SimulationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertsController {

    private static final Map<String, String> DATE_FIELD_MAP = Map.of(
        "occurred_at", "a.occurred_at",
        "resolved_at", "a.resolved_at"
    );

    private final JdbcTemplate jdbc;
    private final SimulationService sim;

    public AlertsController(JdbcTemplate jdbc, SimulationService sim) {
        this.jdbc = jdbc;
        this.sim = sim;
    }

    @GetMapping("/unresolved-count")
    public Map<String, Object> unresolvedCount() {
        var p = AuthContext.require();
        Clause scope = FactoryScope.clause(p.allowedFactoryIds(), "c.factory_id");
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM alerts a JOIN cars c ON c.car_id=a.car_id WHERE a.current_status!='RESOLVED'" + scope.sql(),
            Integer.class, scope.params().toArray());
        return Map.of("count", count == null ? 0 : count);
    }

    @GetMapping("/facets")
    public Map<String, Object> facets() {
        var p = AuthContext.require();
        Clause scopeC = FactoryScope.clause(p.allowedFactoryIds(), "c.factory_id");
        Clause scopeF = FactoryScope.clause(p.allowedFactoryIds(), "factory_id");
        List<String> types = jdbc.queryForList(
            "SELECT DISTINCT a.alert_type FROM alerts a JOIN cars c ON c.car_id=a.car_id " +
            "WHERE a.alert_type IS NOT NULL" + scopeC.sql() + " ORDER BY a.alert_type",
            String.class, scopeC.params().toArray());
        List<String> models = jdbc.queryForList(
            "SELECT DISTINCT model_name FROM cars WHERE model_name IS NOT NULL" + scopeF.sql() + " ORDER BY model_name",
            String.class, scopeF.params().toArray());
        List<String> countries = jdbc.queryForList(
            "SELECT DISTINCT destination_country FROM cars WHERE destination_country IS NOT NULL" + scopeF.sql() + " ORDER BY destination_country",
            String.class, scopeF.params().toArray());
        return Map.of("alert_types", types, "models", models, "countries", countries);
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> q) {
        var p = AuthContext.require();
        List<Long> requested = FactoryScope.parseIdsParam(q.get("factory_ids"));
        List<Long> effective = FactoryScope.intersect(requested, p.allowedFactoryIds());
        if (effective.isEmpty()) return Map.of("items", List.of());
        Clause fScope = FactoryScope.clause(effective, "c.factory_id");

        StringBuilder sql = new StringBuilder(
            "SELECT a.*, c.model_name, c.destination_country, c.factory_id FROM alerts a LEFT JOIN cars c ON c.car_id=a.car_id WHERE 1=1" + fScope.sql());
        List<Object> params = new ArrayList<>(fScope.params());

        List<String> parts = new ArrayList<>();
        List<Object> cParams = new ArrayList<>();

        if (q.get("car_id") != null && !q.get("car_id").isBlank()) {
            parts.add("a.car_id LIKE ?"); cParams.add("%" + q.get("car_id") + "%");
        }
        addIn(parts, cParams, "a.current_status", FactoryScope.parseCsv(q.get("status")));
        addIn(parts, cParams, "a.severity", FactoryScope.parseCsv(q.get("severity")));
        addIn(parts, cParams, "a.alert_type", FactoryScope.parseCsv(q.get("alert_type")));
        addIn(parts, cParams, "c.model_name", FactoryScope.parseCsv(q.get("model")));
        addIn(parts, cParams, "c.destination_country", FactoryScope.parseCsv(q.get("country")));

        String dateCol = DATE_FIELD_MAP.getOrDefault(q.get("date_field"), DATE_FIELD_MAP.get("occurred_at"));
        if (q.get("date_from") != null && !q.get("date_from").isBlank()) {
            String s = q.get("date_from").replace('T', ' ');
            parts.add(dateCol + " >= ?");
            cParams.add(s.split(":").length == 3 ? s : s + ":00");
        }
        if (q.get("date_to") != null && !q.get("date_to").isBlank()) {
            String s = q.get("date_to").replace('T', ' ');
            parts.add(dateCol + " <= ?");
            cParams.add(s.split(":").length == 3 ? s : s + ":59");
        }

        if (!parts.isEmpty()) {
            String op = "or".equals(q.get("match_mode")) ? " OR " : " AND ";
            sql.append(" AND (").append(String.join(op, parts)).append(")");
            params.addAll(cParams);
        }
        sql.append(" ORDER BY a.occurred_at DESC LIMIT 500");
        return Map.of("items", jdbc.queryForList(sql.toString(), params.toArray()));
    }

    private static void addIn(List<String> parts, List<Object> params, String col, List<String> values) {
        if (values.isEmpty()) return;
        String ph = String.join(",", Collections.nCopies(values.size(), "?"));
        parts.add(col + " IN (" + ph + ")");
        params.addAll(values);
    }

    private Map<String, Object> scopeCheck(long alertId) {
        var p = AuthContext.require();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT a.*, c.factory_id FROM alerts a LEFT JOIN cars c ON c.car_id=a.car_id WHERE a.alert_id=?", alertId);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Object fid = row.get("factory_id");
        if (fid == null || !p.allowedFactoryIds().contains(((Number) fid).longValue())) return null;
        return row;
    }

    @PostMapping("/{alertId}/acknowledge")
    public Map<String, Object> ack(@PathVariable long alertId) {
        var p = AuthContext.require();
        Map<String, Object> row = scopeCheck(alertId);
        if (row == null) throw new ApiException(404, "경보를 찾을 수 없습니다.");
        String now = TimeUtil.nowISO();
        jdbc.update("UPDATE alerts SET current_status='ACKNOWLEDGED' WHERE alert_id=?", alertId);
        jdbc.update(
            "INSERT INTO alert_status_histories (alert_id,previous_status,new_status,changed_by_user_id,changed_at,note) VALUES (?,?,?,?,?,?)",
            alertId, row.get("current_status"), "ACKNOWLEDGED", p.uid(), now, "확인");
        return Map.of("ok", true);
    }

    @PostMapping("/bulk-delete")
    @Transactional
    public Map<String, Object> bulkDelete(@RequestBody Map<String, Object> body) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        Object raw = body.get("alert_ids");
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try { ids.add(Long.parseLong(String.valueOf(o))); } catch (NumberFormatException ignored) {}
            }
        }
        if (ids.isEmpty()) throw new ApiException(400, "선택된 경보가 없습니다.");
        String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] args = ids.toArray();
        jdbc.update("DELETE FROM alert_status_histories WHERE alert_id IN (" + ph + ")", args);
        int deleted = jdbc.update("DELETE FROM alerts WHERE alert_id IN (" + ph + ")", args);
        return Map.of("ok", true, "deleted", deleted);
    }

    @PostMapping("/{alertId}/resolve")
    public Map<String, Object> resolve(@PathVariable long alertId) {
        var p = AuthContext.require();
        Map<String, Object> row = scopeCheck(alertId);
        if (row == null) throw new ApiException(404, "경보를 찾을 수 없습니다.");
        sim.resolveAlertAndReinspect((String) row.get("car_id"), p.uid());
        return Map.of("ok", true);
    }
}
