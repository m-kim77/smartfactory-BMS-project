package com.evernex.bms.service;

import com.evernex.bms.db.TimeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Port of services/settings.js. */
@Service
public class SettingsService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public SettingsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Object get(String key, Object fallback) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT setting_value, setting_type FROM admin_settings WHERE setting_key=?", key);
        if (rows.isEmpty()) return fallback;
        String v = String.valueOf(rows.get(0).get("setting_value"));
        String t = String.valueOf(rows.get(0).get("setting_type"));
        try {
            return switch (t) {
                case "INTEGER" -> Integer.parseInt(v);
                case "FLOAT" -> Double.parseDouble(v);
                case "JSON" -> (Object) json.readTree(v);
                default -> v;
            };
        } catch (Exception e) {
            return fallback;
        }
    }

    public int getInt(String key, int fallback) {
        Object v = get(key, fallback);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return fallback; }
    }

    public double getDouble(String key, double fallback) {
        Object v = get(key, fallback);
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return fallback; }
    }

    public String getString(String key, String fallback) {
        Object v = get(key, fallback);
        if (v instanceof JsonNode node) return node.asText();
        return v == null ? fallback : String.valueOf(v);
    }

    public void set(String key, Object value, Long userId) {
        jdbc.update(
            "UPDATE admin_settings SET setting_value=?, updated_at=?, updated_by_user_id=? WHERE setting_key=?",
            String.valueOf(value), TimeUtil.nowISO(), userId, key);
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
            "SELECT setting_key, setting_value, setting_type, description, updated_at FROM admin_settings");
    }
}
