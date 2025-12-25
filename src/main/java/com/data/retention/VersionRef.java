package com.data.retention;

import java.time.Instant;

/**
 * Minimal abstraction for a version in any system:
 * - a monotonic/unique version number (revision, id, seq)
 * - a UTC timestamp
 */
public interface VersionRef {
  long versionNumber();
  Instant timeUtc();
}
