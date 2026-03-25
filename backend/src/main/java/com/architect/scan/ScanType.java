package com.architect.scan;

/**
 * Queue priority: {@link #PR} is always scheduled before {@link #MANUAL}.
 */
public enum ScanType {
    PR,
    MANUAL
}
