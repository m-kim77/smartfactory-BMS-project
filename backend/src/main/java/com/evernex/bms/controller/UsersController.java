package com.evernex.bms.controller;

import com.evernex.bms.security.ApiException;
import com.evernex.bms.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    private final JdbcTemplate jdbc;

    public UsersController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void adminOnly() {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
    }

    @GetMapping("")
    public Map<String, Object> list() {
        adminOnly();
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT user_id, email, role, name, created_at FROM users ORDER BY created_at DESC");
        List<Map<String, Object>> counts = jdbc.queryForList(
            "SELECT user_id, COUNT(*) AS c FROM user_factories GROUP BY user_id");
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> r : counts) {
            countMap.put(((Number) r.get("user_id")).longValue(), ((Number) r.get("c")).intValue());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> u : items) {
            Map<String, Object> copy = new LinkedHashMap<>(u);
            Long uid = ((Number) u.get("user_id")).longValue();
            copy.put("factory_count", countMap.getOrDefault(uid, 0));
            out.add(copy);
        }
        return Map.of("items", out);
    }

    @PutMapping("/{userId}/role")
    public Map<String, Object> updateRole(@PathVariable("userId") long userId, @RequestBody Map<String, Object> body) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        String role = (String) body.get("role");
        if (!("admin".equals(role) || "operator".equals(role))) {
            throw new ApiException(400, "유효하지 않은 역할입니다.");
        }
        if (userId == p.uid()) throw new ApiException(400, "본인의 역할은 변경할 수 없습니다.");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, role, email FROM users WHERE user_id=?", userId);
        if (rows.isEmpty()) throw new ApiException(404, "사용자를 찾을 수 없습니다.");
        Map<String, Object> target = rows.get(0);
        String curRole = (String) target.get("role");
        if (role.equals(curRole)) throw new ApiException(400, "이미 해당 역할입니다.");
        if ("admin".equals(curRole) && "operator".equals(role)) {
            Integer adminCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role='admin'", Integer.class);
            if (adminCount != null && adminCount <= 1) {
                throw new ApiException(400, "마지막 관리자는 강등할 수 없습니다.");
            }
        }
        jdbc.update("UPDATE users SET role=? WHERE user_id=?", role, userId);
        jdbc.update("DELETE FROM user_factories WHERE user_id=?", userId);
        return Map.of("ok", true);
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> delete(@PathVariable("userId") long userId) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        if (userId == p.uid()) throw new ApiException(400, "본인 계정은 삭제할 수 없습니다.");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT role, email FROM users WHERE user_id=?", userId);
        if (rows.isEmpty()) throw new ApiException(404, "사용자를 찾을 수 없습니다.");
        String role = (String) rows.get(0).get("role");
        if ("admin".equals(role)) {
            Integer adminCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role='admin'", Integer.class);
            if (adminCount != null && adminCount <= 1) {
                throw new ApiException(400, "마지막 관리자는 삭제할 수 없습니다.");
            }
        }
        jdbc.update("DELETE FROM user_factories WHERE user_id=?", userId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        return Map.of("ok", true);
    }

    @GetMapping("/{userId}/factories")
    public Map<String, Object> getFactories(@PathVariable("userId") long userId) {
        adminOnly();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, role FROM users WHERE user_id=?", userId);
        if (rows.isEmpty()) throw new ApiException(404, "사용자를 찾을 수 없습니다.");
        List<Long> factoryIds = jdbc.queryForList(
            "SELECT factory_id FROM user_factories WHERE user_id=?", Long.class, userId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("factory_ids", factoryIds);
        resp.put("role", rows.get(0).get("role"));
        return resp;
    }

    @PutMapping("/{userId}/factories")
    @Transactional
    public Map<String, Object> setFactories(@PathVariable("userId") long userId, @RequestBody Map<String, Object> body) {
        adminOnly();
        Object raw = body.get("factory_ids");
        if (!(raw instanceof List<?> rawList)) {
            throw new ApiException(400, "factory_ids 배열이 필요합니다.");
        }
        List<Map<String, Object>> targetRows = jdbc.queryForList(
            "SELECT user_id, role, email FROM users WHERE user_id=?", userId);
        if (targetRows.isEmpty()) throw new ApiException(404, "사용자를 찾을 수 없습니다.");
        if ("admin".equals(targetRows.get(0).get("role"))) {
            throw new ApiException(400, "관리자는 별도 공장 매핑이 필요하지 않습니다 (전체 접근).");
        }

        LinkedHashSet<Long> cleanSet = new LinkedHashSet<>();
        for (Object o : rawList) {
            try { cleanSet.add(Long.parseLong(String.valueOf(o))); } catch (NumberFormatException ignored) {}
        }
        List<Long> cleanIds = new ArrayList<>(cleanSet);
        if (!cleanIds.isEmpty()) {
            String ph = String.join(",", Collections.nCopies(cleanIds.size(), "?"));
            List<Long> found = jdbc.queryForList(
                "SELECT factory_id FROM factories WHERE factory_id IN (" + ph + ")",
                Long.class, cleanIds.toArray());
            if (found.size() != cleanIds.size()) {
                throw new ApiException(400, "존재하지 않는 공장 ID가 포함되어 있습니다.");
            }
        }

        jdbc.update("DELETE FROM user_factories WHERE user_id=?", userId);
        for (Long fid : cleanIds) {
            jdbc.update("INSERT INTO user_factories (user_id, factory_id) VALUES (?, ?)", userId, fid);
        }
        return Map.of("ok", true, "factory_ids", cleanIds);
    }
}
