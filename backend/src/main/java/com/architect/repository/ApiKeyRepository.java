package com.architect.repository;

import com.architect.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByUserIdAndActiveTrue(Long userId);
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
}
