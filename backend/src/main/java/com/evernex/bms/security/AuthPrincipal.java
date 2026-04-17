package com.evernex.bms.security;

import java.util.List;

/** Per-request auth context. Mirrors req.user + req.allowedFactoryIds + req.isAdminScope. */
public record AuthPrincipal(
    long uid,
    String role,
    String email,
    String name,
    List<Long> allowedFactoryIds,
    boolean adminScope
) {
    public boolean isAdmin() { return "admin".equals(role); }
}
