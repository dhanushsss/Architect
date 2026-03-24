package com.architect.repository;

import com.architect.model.ApiEndpoint;
import com.architect.model.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
    List<ApiEndpoint> findByRepo(Repo repo);
    List<ApiEndpoint> findByRepoId(Long repoId);
    void deleteByRepo(Repo repo);
    long countByRepo(Repo repo);
    boolean existsByRepoIdAndPathContaining(Long repoId, String path);
}
