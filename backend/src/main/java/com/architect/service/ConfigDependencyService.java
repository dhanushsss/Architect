package com.architect.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class ConfigDependencyService {

    @Data
    public static class DetectedConfigRef {
        private String configFile;
        private String filePath;
        private int lineNumber;
    }

    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
        ".json", ".yaml", ".yml", ".xml", ".toml", ".properties", ".env", ".ini", ".conf"
    );

    public List<DetectedConfigRef> detect(String content, String filePath) {
        List<DetectedConfigRef> results = new ArrayList<>();
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Look for string references to config files
            Pattern p = Pattern.compile("[\"']([^\"']*\\.(?:json|yaml|yml|xml|toml|properties|env|ini|conf))[\"']");
            Matcher m = p.matcher(line);
            while (m.find()) {
                String configPath = m.group(1);
                if (!configPath.startsWith("http") && !configPath.contains("node_modules")) {
                    DetectedConfigRef ref = new DetectedConfigRef();
                    ref.setConfigFile(configPath);
                    ref.setFilePath(filePath);
                    ref.setLineNumber(i + 1);
                    results.add(ref);
                }
            }
            // Spring @PropertySource
            Matcher psm = Pattern.compile("@PropertySource\\s*\\(\\s*[\"']([^\"']+)[\"']").matcher(line);
            while (psm.find()) {
                DetectedConfigRef ref = new DetectedConfigRef();
                ref.setConfigFile(psm.group(1));
                ref.setFilePath(filePath);
                ref.setLineNumber(i + 1);
                results.add(ref);
            }
        }
        return results;
    }

    public boolean isConfigFile(String filePath) {
        return CONFIG_EXTENSIONS.stream().anyMatch(filePath::endsWith);
    }
}
