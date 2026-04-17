package com.evernex.bms.security;

/** ThreadLocal holder — equivalent to attaching user info on the Express req object. */
public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();
    private AuthContext() {}

    public static void set(AuthPrincipal p) { HOLDER.set(p); }
    public static AuthPrincipal get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static AuthPrincipal require() {
        AuthPrincipal p = HOLDER.get();
        if (p == null) throw new IllegalStateException("AuthContext not populated");
        return p;
    }
}
