package com.rg.retention;

import com.rg.retention.Models.CalendarTier;
import com.rg.retention.Models.FixedTier;
import com.rg.retention.Models.Tier;

import java.time.Duration;
import java.time.Period;

public final class RetentionParsers {
  private RetentionParsers() {}

  /**
   * Parses ISO-8601 duration/period strings into a Tier.
   *
   * Rules:
   * - If both window and bucket parse as Duration -> FixedTier
   * - Else if both parse as Period -> CalendarTier
   * - Else reject (mixed types or invalid formats)
   *
   * Note: "P7D" parses as Duration AND Period in Java; we intentionally
   * treat it as Duration (FixedTier) because day-based tiers are fine as fixed.
   */
  public static Tier parseTier(String window, String bucket) {
    if (window == null || bucket == null) {
      throw new IllegalArgumentException("window and bucket must be non-null");
    }

    String normWindow = window.trim().toUpperCase();
    String normBucket = bucket.trim().toUpperCase();

    TierKind wKind = classify(window);
    TierKind bKind = classify(bucket);

    if (wKind != bKind) {
      throw new IllegalArgumentException(
          "Mixed tier types. window=" + window + " is " + wKind + ", bucket=" + bucket + " is " + bKind +
              ". Use both Duration (e.g. P7D/PT48H) or both Period (e.g. P12M/P1Y)."
      );
    }

    try {
      return switch (wKind) {
        case DURATION -> new FixedTier(Duration.parse(normWindow), Duration.parse(normBucket));
        case PERIOD   -> new CalendarTier(Period.parse(normWindow), Period.parse(normBucket));
      };
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Invalid window/bucket format. window=" + window + ", bucket=" + bucket, e
      );
    }
  }

  private enum TierKind { DURATION, PERIOD }

  private static TierKind classify(String s) {
    // If there is a time part, it's definitely a Duration (PT..H/M/S)
    if (s.contains("T")) return TierKind.DURATION;

    // If it has years or months (months only valid for Period in this SDK), treat as Period
    // Note: In Period, "M" = months. In Duration, "M" only appears after 'T' as minutes.
    if (s.contains("Y") || s.contains("M")) return TierKind.PERIOD;

    // Otherwise it is day-based like P7D / P1D -> treat as fixed Duration (your chosen convention)
    return TierKind.DURATION;
  }

}
