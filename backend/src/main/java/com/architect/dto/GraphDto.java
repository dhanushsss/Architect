package com.architect.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphDto {
    private List<NodeDto> nodes;
    private List<EdgeDto> edges;
    private GraphStats stats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GraphStats {
        private int totalRepos;
        private int totalEndpoints;
        private int totalCalls;
        private int totalImports;
        private int totalEdges;
        /** Outbound calls to https:// external hosts (Stripe, etc.) */
        private int totalExternalCalls;
        /** Edges from config: gateway routes, UI proxy, etc. */
        private int totalWiredEdges;
    }
}
