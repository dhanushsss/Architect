package com.architect.config;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InstanceIdentity {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }
}
