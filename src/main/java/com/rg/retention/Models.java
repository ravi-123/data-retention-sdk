package com.rg.retention;

import java.time.Duration;
import java.time.Period;
import java.util.Objects;

public final class Models {
  private Models() {}

  public sealed interface Tier permits FixedTier, CalendarTier {}

  /** For minutes/hours/days/weeks where fixed Duration is acceptable. */
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
}
