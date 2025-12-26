package com.data.retention;

import com.data.retention.RetentionPolicy.CalendarTier;
import com.data.retention.RetentionPolicy.FixedTier;
import com.data.retention.RetentionPolicy.Tier;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reference implementation retention engine (portable across file/DB stores).
 *
 * Rules:
 *  1) Always keep last N versions by versionNumber (N = keepLastNVersions).
 *  2) For each tier: within the tier's window, keep the latest version per bucket.
 *     - FixedTier: bucket by Duration (e.g., 1 minute, 1 hour, 1 day). Buckets are based on epoch in bucketZone.
 *     - CalendarTier: bucket by Period (e.g., 1 month, 1 year). Buckets are based on calendar in bucketZone.
 *
 * Notes:
 *  - Input times are UTC Instants.
 *  - Bucketing uses policy.bucketZone (recommend UTC).
 *  - This engine returns sets of versionNumbers to keep/delete; your store applies deletes.
 */
public final class RetentionEngine {
  private RetentionEngine() {}

  public static RetentionDecision decide(List<? extends VersionRef> versions,
                                         RetentionPolicy policy,
                                         long currentVersion,
                                         Instant nowUtc) {
    Objects.requireNonNull(versions, "versions");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(nowUtc, "nowUtc");

    if (versions.isEmpty()) {
      return new RetentionDecision(Set.of(), Set.of());
    }

    // Build quick lookup
    Map<Long, VersionRef> byVersion = new HashMap<>();
    for (VersionRef v : versions) byVersion.put(v.versionNumber(), v);

    // Sort by versionNumber desc
    List<Long> versionsDesc = versions.stream()
        .map(VersionRef::versionNumber)
        .distinct()
        .sorted(Comparator.reverseOrder())
        .toList();

    Set<Long> keep = new HashSet<>();

    // 1) Keep last N by versionNumber
    int n = policy.keepLastNVersions();
    for (int i = 0; i < Math.min(n, versionsDesc.size()); i++) {
      keep.add(versionsDesc.get(i));
    }

    // Always keep currentVersion
    keep.add(currentVersion);

    ZoneId zone = policy.bucketZone();
    ZonedDateTime nowZ = nowUtc.atZone(zone);

    // 2) Tier-based bucketing
    for (Tier tier : policy.tiers()) {
      if (tier instanceof FixedTier ft) {
        Instant cutoff = nowUtc.minus(ft.window());
        keep.addAll(keepLatestPerFixedBucket(versions, byVersion, cutoff, ft.bucket(), zone));
      } else if (tier instanceof CalendarTier ct) {
        ZonedDateTime cutoffZ = nowZ.minus(ct.window());
        keep.addAll(keepLatestPerCalendarBucket(versions, byVersion, cutoffZ, ct.bucket(), zone));
      }
    }

    // Anything not kept is deletable
    Set<Long> all = versions.stream().map(VersionRef::versionNumber).collect(Collectors.toSet());
    Set<Long> delete = new HashSet<>(all);
    delete.removeAll(keep);

    return new RetentionDecision(Collections.unmodifiableSet(keep), Collections.unmodifiableSet(delete));
  }

  public static RetentionDecision decide(List<? extends VersionRef> versions,
                                         RetentionPolicy policy,
                                         long currentVersion) {
    return decide(versions, policy, currentVersion, Instant.now());
  }

  /**
   * Keep the latest version per fixed time bucket (minute/hour/day) within a time window.
   *
   * Example policy:
   *   window = last 48 hours
   *   bucket = 1 hour
   *
   * Means:
   *   Look at versions whose editedAt >= (now - 48h)
   *   Group them into 1-hour buckets
   *   From each bucket, keep exactly ONE version: the latest edit in that bucket
   */
  private static Set<Long> keepLatestPerFixedBucket(
      List<? extends VersionRef> versions,
      Map<Long, VersionRef> byVersion,   // versionNumber -> VersionRef (to compare "best" candidates)
      Instant cutoffUtc,                 // ignore versions older than this
      Duration bucketSize,               // e.g., 60s, 3600s, 86400s
      ZoneId bucketZone                  // not used in this epoch-based version; typically UTC
  ) {
    long bucketSeconds = bucketSize.getSeconds();
    if (bucketSeconds <= 0) throw new IllegalArgumentException("bucket must be >= 1 second");

    // bucketStartEpochSecond -> bestVersionNumberToKeepInThatBucket
    Map<Long, Long> bucketToBestVersion = new HashMap<>();

    for (VersionRef versionFile : versions) {
      // versionFile is version ref of the file having versionNumber
      // and timeUtc(editedAt) of the file (one backup)

      // 1) Skip versions outside the "window"
      if (versionFile.timeUtc().isBefore(cutoffUtc)) {
        continue;
      }

      // 2) Figure out WHICH bucket this version belongs to
      //    (bucket key = bucket start time in epoch seconds)
      long bucketKey = fixedBucketKey(versionFile.timeUtc(), bucketSeconds, bucketZone);

      // 3) If this bucket has no winner yet, set this version as the winner
      Long bestSoFarVersionNum = bucketToBestVersion.get(bucketKey);
      if (bestSoFarVersionNum == null) {
        bucketToBestVersion.put(bucketKey, versionFile.versionNumber());
        continue;
      }

      // 4) Otherwise compare this version with the current winner
      VersionRef bestSoFar = byVersion.get(bestSoFarVersionNum);

      if (isBetter(versionFile, bestSoFar)) {
        bucketToBestVersion.put(bucketKey, versionFile.versionNumber());
      }
    }

    // 5) Return all winners (one per bucket)
    return new HashSet<>(bucketToBestVersion.values());
  }

  /**
   * fixed bucket key based on epoch seconds aligned to bucket in the selected zone.
   * For UTC (recommended), this is straightforward.
   */
  private static long fixedBucketKey(Instant t, long bucketSeconds, ZoneId zone) {
    // Convert to epoch seconds, but align to bucket boundaries in the chosen zone.
    // For UTC, zone doesn't affect epoch seconds.
    // For non-UTC zones, epoch is still absolute; bucket boundaries effectively stay aligned to epoch,
    // which is acceptable for fixed buckets (minute/hour).
    long epochSecond = t.getEpochSecond();
    return (epochSecond / bucketSeconds) * bucketSeconds;
  }

  private static Set<Long> keepLatestPerCalendarBucket(List<? extends VersionRef> versions,
                                                       Map<Long, VersionRef> byVersion,
                                                       ZonedDateTime cutoffZ,
                                                       Period bucket,
                                                       ZoneId zone) {

    // We support common calendar buckets: months or years. (Extend later if needed.)
    boolean monthBucket = bucket.getYears() == 0 && bucket.getMonths() == 1 && bucket.getDays() == 0;
    boolean yearBucket  = bucket.getYears() == 1 && bucket.getMonths() == 0 && bucket.getDays() == 0;

    if (!monthBucket && !yearBucket) {
      throw new IllegalArgumentException("Calendar bucket currently supports only P1M or P1Y. Got: " + bucket);
    }

    Map<String, Long> bucketToBestVersion = new HashMap<>();

    for (VersionRef v : versions) {
      ZonedDateTime z = v.timeUtc().atZone(zone);
      if (z.isBefore(cutoffZ)) continue;

      String key = monthBucket ? monthKey(z) : yearKey(z);

      Long best = bucketToBestVersion.get(key);
      if (best == null) {
        bucketToBestVersion.put(key, v.versionNumber());
      } else {
        VersionRef curBest = byVersion.get(best);
        if (isBetter(v, curBest)) {
          bucketToBestVersion.put(key, v.versionNumber());
        }
      }
    }

    return new HashSet<>(bucketToBestVersion.values());
  }

  private static String monthKey(ZonedDateTime z) {
    int y = z.getYear();
    int m = z.getMonthValue();
    return y + "-" + (m < 10 ? "0" + m : String.valueOf(m));
  }

  private static String yearKey(ZonedDateTime z) {
    return String.valueOf(z.getYear());
  }

  /**
   * "Better" means:
   * a) later timestamp wins
   * b) if timestamps tie, higher versionNumber wins"
   */
  private static boolean isBetter(VersionRef candidate, VersionRef currentBest) {
    if (currentBest == null) return true;
    int cmp = candidate.timeUtc().compareTo(currentBest.timeUtc());
    if (cmp != 0) return cmp > 0;
    return candidate.versionNumber() > currentBest.versionNumber();
  }
}
