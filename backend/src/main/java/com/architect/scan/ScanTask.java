package com.architect.scan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * In-memory view of a queued scan (DB row is {@link com.architect.model.ScanQueueTask}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanTask {
    private Long repoId;
    private Long userId;
    private ScanType type;
    private Instant createdAt;
}
