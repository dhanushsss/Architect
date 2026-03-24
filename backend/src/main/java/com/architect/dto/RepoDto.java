package com.architect.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepoDto {
    private Long id;
    private Long githubId;
    private String name;
    private String fullName;
    private String description;
    private String primaryLanguage;
    private String htmlUrl;
    private Boolean isPrivate;
    private String scanStatus;
    private String lastScannedAt;
    private long endpointCount;
}
