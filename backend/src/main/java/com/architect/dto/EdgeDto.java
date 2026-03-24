package com.architect.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EdgeDto {
    private String id;
    private String source;
    private String target;
    private String label;
    private String type; // CALLS, IMPORTS, READS, DEPENDS_ON
    private Map<String, Object> data; // importType, sourceFile, etc.
}
