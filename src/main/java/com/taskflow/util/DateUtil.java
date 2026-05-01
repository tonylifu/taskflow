package com.taskflow.util;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Mirrors src/utils/dateUtils.ts (formatDate, formatRelative, isOverdue, isDueSoon, etc.).
 *
 * <p>Display formatting uses the JVM's default locale and the system zone — keep
 * this consistent with the React app, which used the browser's locale and zone.
 */
public final class DateUtil {

    private DateUtil() {}

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH);

    /** "Mar 14, 2026" — for the date stamp on each card and tooltips. */
    public static String formatDate(OffsetDateTime when) {
        if (when == null) return "—";
        return when.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_FMT);
    }

    /** "Mar 14, 2026 09:30" — for "Updated …" tooltips. */
    public static String formatDateTime(OffsetDateTime when) {
        if (when == null) return "—";
        return when.atZoneSameInstant(ZoneId.systemDefault()).format(DATETIME_FMT);
    }

    /** "5 minutes ago", "in 2 days" etc — used by TaskCard footer. */
    public static String formatRelative(OffsetDateTime when) {
        if (when == null) return "—";
        OffsetDateTime now = OffsetDateTime.now();
        Duration d = Duration.between(when, now);
        boolean past = !d.isNegative();
        long abs = Math.abs(d.getSeconds());

        String quantity;
        if (abs < 60)            quantity = pluralise(abs,                 "second");
        else if (abs < 3_600)    quantity = pluralise(abs / 60,            "minute");
        else if (abs < 86_400)   quantity = pluralise(abs / 3_600,         "hour");
        else if (abs < 2_592_000) quantity = pluralise(abs / 86_400,        "day");
        else if (abs < 31_536_000) quantity = pluralise(abs / 2_592_000,    "month");
        else                     quantity = pluralise(abs / 31_536_000,    "year");

        return past ? quantity + " ago" : "in " + quantity;
    }

    private static String pluralise(long n, String unit) {
        return n + " " + (n == 1 ? unit : unit + "s");
    }

    public static boolean isOverdue(OffsetDateTime due) {
        return due != null && due.isBefore(OffsetDateTime.now());
    }

    public static boolean isDueSoon(OffsetDateTime due) {
        return isDueSoon(due, 24);
    }

    public static boolean isDueSoon(OffsetDateTime due, int withinHours) {
        if (due == null) return false;
        OffsetDateTime now = OffsetDateTime.now();
        return due.isAfter(now) && due.isBefore(now.plusHours(withinHours));
    }
}
