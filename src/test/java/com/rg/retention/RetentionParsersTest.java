package com.rg.retention;

import com.rg.retention.Models.CalendarTier;
import com.rg.retention.Models.FixedTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetentionParsersTest {

  @Test
  void parsesFixedTierDurationFormats() {
    var tier = RetentionParsers.parseTier("PT48H", "PT1H");
    assertTrue(tier instanceof FixedTier);

    FixedTier ft = (FixedTier) tier;
    assertEquals(java.time.Duration.ofHours(48), ft.window());
    assertEquals(java.time.Duration.ofHours(1), ft.bucket());
  }

  @Test
  void parsesDaysAsDurationFixedTier() {
    // P7D is valid for Duration.parse, so this becomes FixedTier
    var tier = RetentionParsers.parseTier("P7D", "P1D");
    assertTrue(tier instanceof FixedTier);

    FixedTier ft = (FixedTier) tier;
    assertEquals(java.time.Duration.ofDays(7), ft.window());
    assertEquals(java.time.Duration.ofDays(1), ft.bucket());
  }

  @Test
  void parsesCalendarTierMonthsYears() {
    var tier = RetentionParsers.parseTier("P12M", "P1M");
    assertTrue(tier instanceof CalendarTier);

    CalendarTier ct = (CalendarTier) tier;
    assertEquals(java.time.Period.ofMonths(12), ct.window());
    assertEquals(java.time.Period.ofMonths(1), ct.bucket());
  }

  @Test
  void rejectsMixedDurationAndPeriod() {
    // window is Period, bucket is Duration -> must fail
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> RetentionParsers.parseTier("P1Y", "P1D")
    );
    assertTrue(ex.getMessage().contains("Mixed tier types"));
  }

  @Test
  void rejectsInvalidStrings() {
    assertThrows(IllegalArgumentException.class, () -> RetentionParsers.parseTier("7 days", "1 day"));
    assertThrows(IllegalArgumentException.class, () -> RetentionParsers.parseTier("P1Y", "junk"));
  }

  @Test
  void parsesCombinedDayAndHourDuration() {
    // P1DT2H = 1 day + 2 hours
    var tier = RetentionParsers.parseTier("P1DT2H", "PT1H");
    assertTrue(tier instanceof FixedTier);

    FixedTier ft = (FixedTier) tier;
    assertEquals(java.time.Duration.ofHours(26), ft.window());
    assertEquals(java.time.Duration.ofHours(1), ft.bucket());
  }
}
