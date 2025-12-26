package com.rg.retention;

import com.rg.retention.Models.Tier;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

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

  public static RetentionPolicy utc(int keepLastNVersions, List<Tier> tiers) {
    return new RetentionPolicy(keepLastNVersions, tiers, ZoneOffset.UTC);
  }
}
