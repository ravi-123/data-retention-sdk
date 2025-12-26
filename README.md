# Data Retention SDK (Java)

A small, framework-agnostic **retention engine** for versioned backups / snapshots.

It helps you implement retention strategies like:
- keep the last **N** versions (fast “undo” window)
- keep **time-bucket snapshots** (e.g., 1 per minute for 60 minutes, 1 per day for 30 days, 1 per month for 12 months, etc.)

Designed to be portable across storage backends:
- file-based versioning (`index.json` + versions folder)
- PostgreSQL/MySQL tables
- object storage manifests (S3)
- anything that can list `(versionNumber, createdAtUtc)`

---

## Why this exists

Retention logic becomes messy when embedded directly in filesystem code or SQL queries.

This SDK makes retention:
- **policy-driven** (config, not code)
- **portable**
- **unit-testable**
- easy to plug into any app

---

## Core idea

Given a list of versions:

- `versionNumber` — monotonic revision/sequence/id (recommended)
- `timeUtc` — UTC timestamp (Instant)

And a policy:

- `keepLastNVersions` — always keep the latest N versions (by versionNumber)
- `tiers` — keep the latest version per bucket within a window
- `bucketZone` — timezone used ONLY for bucketing (recommended: UTC)

We return:

- `keep` set
- `delete` set

Your app then deletes versions in `delete`.

---

## Retention rules (how selections work)

### 1) Keep last N versions
`keepLastNVersions = 10` keeps the latest 10 versions by `versionNumber` (descending).

> Note: If your system uses non-monotonic IDs (UUIDs), consider setting `keepLastNVersions=0`
> and rely on time-based tiers, or define your own “recency” rule in the calling app.

### 2) Tier snapshots: “keep latest per bucket within a window”
A tier is defined as:
- **window**: how far back this tier applies (e.g., last 7 days)
- **bucket**: the bucket size inside that window (e.g., 1 day)

For each bucket, the SDK keeps **one** version:
- the **latest** by timestamp in that bucket
- tie-breaker: higher `versionNumber`

---

## ISO-8601 tier strings (Duration vs Period)

You can build tiers using ISO strings with `RetentionParsers.parseTier(window, bucket)`.

### Fixed time (Java `Duration`)
Use these for minutes/hours/days:
- `PT15M` = 15 minutes
- `PT1H` = 1 hour
- `P7D` = 7 days
- `P1DT2H` = 1 day + 2 hours

### Calendar time (Java `Period`)
Use these for months/years:
- `P6M` = 6 months
- `P1Y` = 1 year
- `P12M` = 12 months

### Rules enforced by the parser
- A tier must be consistent:
    - both `window` and `bucket` must be **Duration**, or both must be **Period**
- Days (`P…D`) are treated as **Duration** (fixed time) in this SDK.
- Months/years (`P…M`, `P…Y`) are treated as **Period** (calendar time).
- Mixed types like `P1Y` + `P1D` are rejected.

---

## Quick start

### 1) Define your versions

```java
import com.rg.retention.VersionRef;
import java.time.Instant;

record V(long versionNumber, Instant timeUtc) implements VersionRef {}

### 2) Build a policy (option A: builder using strings)

```java
import com.rg.retention.RetentionPolicy;
import com.rg.retention.RetentionPolicyBuilder;

import java.time.ZoneOffset;

RetentionPolicy policy = RetentionPolicyBuilder.builder()
  .keepLastNVersions(10)
  .bucketZone(ZoneOffset.UTC)     // recommended; defaults to UTC anyway
  .addTier("PT48H", "PT1H")       // last 48 hours: 1 per hour
  .addTier("P7D", "P1D")          // last 7 days: 1 per day
  .addTier("P12M", "P1M")         // last 12 months: 1 per month
  .addTier("P5Y", "P1Y")          // last 5 years: 1 per year
  .build();

### 3) Build a policy (option B: parser + list)

```java
import com.rg.retention.RetentionPolicy;
import com.rg.retention.RetentionParsers;
import com.rg.retention.Models.Tier;

import java.time.ZoneOffset;
import java.util.List;

List<Tier> tiers = List.of(
  RetentionParsers.parseTier("PT48H", "PT1H"),
  RetentionParsers.parseTier("P7D", "P1D"),
  RetentionParsers.parseTier("P12M", "P1M"),
  RetentionParsers.parseTier("P5Y", "P1Y")
);

RetentionPolicy policy = new RetentionPolicy(10, tiers, ZoneOffset.UTC);

### 4) Decide what to keep/delete

```java
import com.rg.retention.RetentionDecision;
import com.rg.retention.RetentionEngine;

import java.time.Instant;
import java.util.List;

List<V> versions = List.of(
  new V(1, Instant.parse("2025-01-01T00:00:00Z")),
  new V(2, Instant.parse("2025-01-02T00:00:00Z")),
  new V(3, Instant.parse("2025-01-03T00:00:00Z"))
);

long currentVersion = 3;

RetentionDecision decision = RetentionEngine.decide(versions, policy, currentVersion);

decision.keep().forEach(v -> System.out.println("KEEP " + v));
decision.delete().forEach(v -> System.out.println("DELETE " +

## Using with DB later

Even if you can express parts of retention in SQL, the retention decision tends to be logic-heavy
(bucket selection, tier unions, calendar buckets, tie-breakers).

A common approach:
Query candidates from DB: (version_id, created_at_utc) (using indexes)
Run this SDK to compute keep/delete sets
Delete or mark versions not in keep set
This keeps your retention behavior:
consistent across backends (filesystem today, DB tomorrow)
easily testable via unit tests

## Build / test
./gradlew test
./gradlew jar

## License
MIT

## Gradle
dependencies {
  implementation "com.rg:data-retention-sdk:2.1.0"
}

## Maven
<dependency>
  <groupId>com.rg</groupId>
  <artifactId>data-retention-sdk</artifactId>
  <version>2.1.0</version>
</dependency>