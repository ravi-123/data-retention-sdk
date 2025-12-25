package com.data.retention;

import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Policy-driven retention:
 * - keepLastNVersions: always keep most recent N by versionNumber
 * - tiers: snapshot tiers (fixed durations and/or calendar periods)
 * - bucketZone: the zone used ONLY for bucketing (recommend UTC)
 */
public record RetentionPolicy(
    int keepLastNVersions,
    List<Tier> tiers,
    ZoneId bucketZone
) {
  public RetentionPolicy {
    if (keepLastNVersions < 0) throw new IllegalArgumentException("keepLastNVersions must be >= 0");
    tiers = (tiers == null) ? List.of() : List.copyOf(tiers);
    bucketZone = (bucketZone == null) ? ZoneOffset.UTC : bucketZone;
  }

  public sealed interface Tier permits FixedTier, CalendarTier {
    // window = how far back from "now" this tier applies
    // bucket = bucket size within that window (keep latest per bucket)
  }

  /** For minutes/hours/days/weeks where a fixed Duration is acceptable. */
  public record FixedTier(Duration window, Duration bucket) implements Tier {
    public FixedTier {
      Objects.requireNonNull(window, "window");
      Objects.requireNonNull(bucket, "bucket");
      if (window.isNegative() || window.isZero()) throw new IllegalArgumentException("window must be > 0");
      if (bucket.isNegative() || bucket.isZero()) throw new IllegalArgumentException("bucket must be > 0");
    }
  }

  /** For months/years where calendar periods matter (Period). */
  public record CalendarTier(Period window, Period bucket) implements Tier {
    public CalendarTier {
      Objects.requireNonNull(window, "window");
      Objects.requireNonNull(bucket, "bucket");
      if (window.isZero() || window.isNegative()) throw new IllegalArgumentException("window must be > 0");
      if (bucket.isZero() || bucket.isNegative()) throw new IllegalArgumentException("bucket must be > 0");
    }
  }

  public static RetentionPolicy utc(int keepLastNVersions, List<Tier> tiers) {
    return new RetentionPolicy(keepLastNVersions, tiers, ZoneOffset.UTC);
  }
}
