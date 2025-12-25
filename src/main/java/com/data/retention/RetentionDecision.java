package com.data.retention;

import java.util.Set;

public record RetentionDecision(
    Set<Long> keep,
    Set<Long> delete
) {}
