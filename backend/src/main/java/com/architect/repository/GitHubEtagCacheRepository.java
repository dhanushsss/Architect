package com.architect.repository;

import com.architect.model.GitHubEtagCache;
import com.architect.model.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GitHubEtagCacheRepository extends JpaRepository<GitHubEtagCache, Long> {
    Optional<GitHubEtagCache> findByRepoAndResourcePath(Repo repo, String resourcePath);

    @Modifying
    @Query("DELETE FROM GitHubEtagCache g WHERE g.repo = :repo")
    void deleteByRepo(@Param("repo") Repo repo);
}
