package com.architect.dto;

import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeDto {
    private String id;
    private String label;
    private String type; // REPO, API_ENDPOINT, COMPONENT, CONFIG
    private String language;
    private Map<String, Object> data;
}
