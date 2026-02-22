package com.company.observability.domain;

public enum RunFrequency {

    /**
     * The calculator is scheduled to run once per business day.
     * {@code reporting_date} is typically the business date the run covers
     * (often the prior calendar day).
     */
    DAILY,

    /**
     * The calculator is scheduled to run once per calendar month.
     * {@code reporting_date} is typically the last day of the month
     * the run covers.
     */
    MONTHLY;

    /** Case-insensitive parse — tolerates "daily", "DAILY", "Daily". */
    public static RunFrequency of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("frequency is required");
        }
        return switch (value.trim().toUpperCase()) {
            case "DAILY"   -> DAILY;
            case "MONTHLY" -> MONTHLY;
            default        -> throw new IllegalArgumentException(
                    "Invalid frequency '" + value + "'. Expected DAILY or MONTHLY.");
        };
    }
}
