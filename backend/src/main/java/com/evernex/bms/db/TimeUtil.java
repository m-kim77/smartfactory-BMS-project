package com.evernex.bms.db;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Matches the "YYYY-MM-DD HH:MM:SS" format produced by Node's toISOString() slice. */
public final class TimeUtil {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private TimeUtil() {}
    public static String nowISO() { return FMT.withZone(ZoneOffset.UTC).format(Instant.now()); }
    public static String fromMillis(long ms) { return FMT.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms)); }
}
