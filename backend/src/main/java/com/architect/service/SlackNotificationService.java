package com.architect.service;

import com.architect.config.AppProperties;
import com.architect.dto.ImpactDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotificationService {

    private final AppProperties appProperties;

    public void sendImpactAlert(String repoName, String endpoint, ImpactDto impact) {
        String webhookUrl = appProperties.getSlack().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Slack webhook not configured, skipping notification");
            return;
        }

        String riskEmoji = switch (impact.getRiskScore()) {
            case "HIGH" -> ":red_circle:";
            case "MEDIUM" -> ":large_yellow_circle:";
            default -> ":large_green_circle:";
        };

        String message = String.format(
            "%s *[%s RISK]* `%s` changed `%s`\n" +
            "> *Affected repos:* %d | *Affected files:* %d\n" +
            "> Repos: %s",
            riskEmoji, impact.getRiskScore(), repoName, endpoint,
            impact.getDependentsCount(), impact.getAffectedFiles().size(),
            impact.getAffectedRepos().stream()
                .map(ImpactDto.AffectedItem::getName)
                .reduce((a, b) -> a + ", " + b).orElse("none")
        );

        try {
            WebClient.builder().build()
                .post()
                .uri(webhookUrl)
                .bodyValue(Map.of("text", message))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (Exception e) {
            log.warn("Failed to send Slack notification: {}", e.getMessage());
        }
    }
}
