package com.rg.retention;

import com.rg.retention.Models.FixedTier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyBuilderTest {

  @Test
  void buildsUsingStrings() {
    RetentionPolicy p = RetentionPolicyBuilder.builder()
        .keepLastNVersions(10)
        .addTier("PT48H", "PT1H")
        .addTier("P7D", "P1D")
        .addTier("P12M", "P1M")
        .addTier("P5Y", "P1Y")
        .build();

    assertEquals(10, p.keepLastNVersions());
    assertEquals(ZoneOffset.UTC, p.bucketZone());
    assertEquals(4, p.tiers().size());
  }

  @Test
  void buildsUsingTypedTier() {
    RetentionPolicy p = RetentionPolicyBuilder.builder()
        .keepLastNVersions(2)
        .addTier(new FixedTier(Duration.ofDays(7), Duration.ofDays(1)))
        .build();

    assertEquals(2, p.keepLastNVersions());
    assertEquals(1, p.tiers().size());
  }
}
