package com.rg.retention;

import com.rg.retention.Models.Tier;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for RetentionPolicy.
 *
 * Defaults:
 * - bucketZone = UTC
 * - keepLastNVersions = 0
 */
public final class RetentionPolicyBuilder {

  private int keepLastNVersions = 0;
  private ZoneId bucketZone = ZoneOffset.UTC;
  private final List<Tier> tiers = new ArrayList<>();

  private RetentionPolicyBuilder() {}

  /** Start a new builder (bucketZone defaults to UTC). */
  public static RetentionPolicyBuilder builder() {
    return new RetentionPolicyBuilder();
  }

  public RetentionPolicyBuilder keepLastNVersions(int n) {
    if (n < 0) throw new IllegalArgumentException("keepLastNVersions must be >= 0");
    this.keepLastNVersions = n;
    return this;
  }

  /** Timezone used ONLY for bucketing (recommended: UTC). */
  public RetentionPolicyBuilder bucketZone(ZoneId zone) {
    this.bucketZone = Objects.requireNonNull(zone, "bucketZone");
    return this;
  }

  /** Add a tier using an already-parsed Tier (FixedTier/CalendarTier). */
  public RetentionPolicyBuilder addTier(Tier tier) {
    this.tiers.add(Objects.requireNonNull(tier, "tier"));
    return this;
  }

  /**
   * Add a tier using ISO strings. Internally uses RetentionParsers.parseTier(window, bucket).
   * Examples:
   * - Fixed (Duration): PT48H / PT1H, P7D / P1D, P1DT2H / PT1H
   * - Calendar (Period): P12M / P1M, P5Y / P1Y
   */
  public RetentionPolicyBuilder addTier(String window, String bucket) {
    this.tiers.add(RetentionParsers.parseTier(window, bucket));
    return this;
  }

  public RetentionPolicyBuilder addTiers(List<? extends Tier> tiers) {
    if (tiers == null) return this;
    for (Tier t : tiers) addTier(t);
    return this;
  }

  public RetentionPolicy build() {
    return new RetentionPolicy(keepLastNVersions, List.copyOf(tiers), bucketZone);
  }
}
