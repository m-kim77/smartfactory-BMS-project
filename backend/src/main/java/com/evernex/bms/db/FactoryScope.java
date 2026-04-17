package com.evernex.bms.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Port of db/factoryScope.js — WHERE clause builder + intersection helpers. */
public final class FactoryScope {
    private FactoryScope() {}

    public record Clause(String sql, List<Object> params) {}

    public static Clause clause(List<Long> allowedIds, String column) {
        if (allowedIds == null || allowedIds.isEmpty()) {
            return new Clause(" AND 1=0", Collections.emptyList());
        }
        String placeholders = String.join(",", Collections.nCopies(allowedIds.size(), "?"));
        List<Object> params = new ArrayList<>(allowedIds);
        return new Clause(" AND " + column + " IN (" + placeholders + ")", params);
    }

    public static List<Long> intersect(List<Long> requested, List<Long> allowedIds) {
        List<Long> allowed = allowedIds == null ? Collections.emptyList() : allowedIds;
        if (requested == null || requested.isEmpty()) return new ArrayList<>(allowed);
        HashSet<Long> allowSet = new HashSet<>(allowed);
        List<Long> out = new ArrayList<>();
        for (Long id : requested) if (allowSet.contains(id)) out.add(id);
        return out;
    }

    public static List<Long> parseIdsParam(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<Long> ids = new ArrayList<>();
        for (String s : raw.split(",")) {
            try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return ids.isEmpty() ? null : ids;
    }

    public static List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
