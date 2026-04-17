package com.evernex.bms.security;

/** Uniform error → JSON body used by the Express routes. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String detail;

    public ApiException(int status, String message) { this(status, message, null); }
    public ApiException(int status, String message, String detail) {
        super(message);
        this.status = status;
        this.detail = detail;
    }
    public int getStatus() { return status; }
    public String getDetail() { return detail; }
}
