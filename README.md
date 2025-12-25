# Data Retention SDK (Java)

A small, framework-agnostic **retention engine** for versioned backups / snapshots.

It helps you implement **backup retention strategies** like:
- keep last **N** versions (fast undo window)
- keep **time-bucket snapshots** (e.g., 1 per minute for 60 minutes, 1 per day for 30 days, 1 per month for 12 months, etc.)

This SDK is designed to be portable across storage backends:
- file-based versioning
- PostgreSQL / MySQL tables
- object storage (S3) manifests
- anything that can list `(versionNumber, createdAtUtc)`

---

## Why this exists

“Retention” often becomes hard to test when embedded in filesystem or SQL logic.
This library makes retention:
- **policy-driven** (config, not code)
- **portable**
- **unit-testable**
- easy to plug into any app

---

## Core idea

Given a list of versions:

- `versionNumber` (revision/sequence/id)
- `timeUtc` (UTC timestamp)

And a policy:

- **keepLastNVersions**
- **tiers** (windows + buckets)

We return:

- `keep` set
- `delete` set

Your app then deletes versions in `delete`.

---

## Policy model

### Always keep last N versions
`keepLastNVersions = 10` means keep the latest 10 by `versionNumber`.
NOTE: this is by `versionNumber`, NOT by `timeUtc`. versionNumber must be monotonic increasing numerical values.

### Tiers = “keep latest per bucket within window”
Example tiers:
- last 60 minutes: 1 per minute
- last 48 hours: 1 per hour
- last 30 days: 1 per day
- last 12 months: 1 per month (calendar)
- last 5 years: 1 per year (calendar)

**Important:** Bucketing is done in a configured timezone (`bucketZone`). Recommended: `UTC`.

---

## Example usage

```java
import com.data.retention.*;
import com.data.retention.RetentionPolicy.*;

import java.time.*;
import java.util.List;

record V(long versionNumber, Instant timeUtc) implements VersionRef {}

RetentionPolicy policy = RetentionPolicy.utc(
  10,
  List.of(
    new FixedTier(Duration.ofMinutes(60), Duration.ofMinutes(1)),   // 1/min for 60 mins
    new FixedTier(Duration.ofHours(48), Duration.ofHours(1)),       // 1/hour for 48 hours
    new FixedTier(Duration.ofDays(30), Duration.ofDays(1)),         // 1/day for 30 days
    new CalendarTier(Period.ofMonths(12), Period.ofMonths(1)),      // 1/month for 12 months
    new CalendarTier(Period.ofYears(5), Period.ofYears(1))          // 1/year for 5 years
  )
);

List<VersionRef> versions = ...; // from file index.json or DB query
long current = ...;              // current/latest revision number

RetentionDecision decision = RetentionEngine.decide(versions, policy, current);

// Apply deletes in your store layer
decision.delete().forEach(v -> System.out.println("Delete version " + v));
