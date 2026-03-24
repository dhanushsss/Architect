package com.architect.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanStatusDto {
    private Long repoId;
    private String repoName;
    private String status;
    private Integer endpointsFound;
    private Integer callsFound;
    private Integer importsFound;
    private String message;
}
