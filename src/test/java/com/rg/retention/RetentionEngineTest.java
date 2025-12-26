package com.rg.retention;

import com.rg.retention.Models.CalendarTier;
import com.rg.retention.Models.FixedTier;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RetentionEngineTest {

  private record V(long versionNumber, Instant timeUtc) implements VersionRef {}

  @Test
  void keepsLastNVersions() {
    Instant base = Instant.parse("2025-12-25T00:00:00Z");
    List<VersionRef> vs = List.of(
        new V(1, base.plusSeconds(1)),
        new V(2, base.plusSeconds(2)),
        new V(3, base.plusSeconds(3)),
        new V(4, base.plusSeconds(4))
    );

    var policy = RetentionPolicy.utc(2, List.of());
    var decision = RetentionEngine.decide(vs, policy, 4, base.plusSeconds(10));

    assertTrue(decision.keep().containsAll(Set.of(4L, 3L)));
    assertFalse(decision.keep().contains(1L));
    assertFalse(decision.keep().contains(2L)); // not guaranteed unless currentVersion/tiers keep it
  }

  @Test
  void fixedTierKeepsLatestPerMinute() {
    Instant now = Instant.parse("2025-12-25T01:00:00Z");
    Instant t0 = Instant.parse("2025-12-25T00:59:10Z");

    // 3 versions in same minute bucket, keep latest by time (or highest version if tie)
    List<VersionRef> vs = List.of(
        new V(10, t0),
        new V(11, t0.plusSeconds(20)),
        new V(12, t0.plusSeconds(30))
    );

    var policy = RetentionPolicy.utc(0, List.of(
        new FixedTier(Duration.ofMinutes(60), Duration.ofMinutes(1))
    ));

    var decision = RetentionEngine.decide(vs, policy, 12, now);

    assertTrue(decision.keep().contains(12L));
    assertEquals(1, decision.keep().size());
  }

  @Test
  void calendarTierKeepsLatestPerMonth() {
    Instant now = Instant.parse("2025-12-25T00:00:00Z");
    List<VersionRef> vs = List.of(
        new V(1, Instant.parse("2025-01-10T00:00:00Z")),
        new V(2, Instant.parse("2025-01-20T00:00:00Z")), // same month, later
        new V(3, Instant.parse("2025-02-05T00:00:00Z"))
    );

    var policy = RetentionPolicy.utc(0, List.of(
        new CalendarTier(Period.ofMonths(12), Period.ofMonths(1))
    ));

    var decision = RetentionEngine.decide(vs, policy, 3, now);
    assertTrue(decision.keep().containsAll(Set.of(2L, 3L)));
    assertFalse(decision.keep().contains(1L));
  }
}
