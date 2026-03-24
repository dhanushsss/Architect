package com.architect.repository;

import com.architect.model.ApiCall;
import com.architect.model.ApiEndpoint;
import com.architect.model.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ApiCallRepository extends JpaRepository<ApiCall, Long> {
    List<ApiCall> findByCallerRepo(Repo repo);
    List<ApiCall> findByCallerRepoId(Long repoId);
    List<ApiCall> findByEndpoint(ApiEndpoint endpoint);
    void deleteByCallerRepo(Repo repo);
    long countByEndpoint(ApiEndpoint endpoint);
    long countByEndpointId(Long endpointId);

    @Query("SELECT c FROM ApiCall c WHERE c.callerRepo.user.id = :userId AND c.endpoint IS NULL")
    List<ApiCall> findByCallerRepoUserIdAndEndpointIsNull(@Param("userId") Long userId);

    @Query("SELECT c FROM ApiCall c WHERE c.callerRepo.user.id = :userId AND c.endpoint IS NOT NULL")
    List<ApiCall> findByCallerRepoUserIdWithEndpoint(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM ApiCall c WHERE c.callerRepo.id = :repoId AND c.endpoint IS NULL")
    long countByCallerRepoIdAndEndpointIsNull(@Param("repoId") Long repoId);

    /**
     * Distinct caller-repo → endpoint-repo pairs (matches graph CALLS / REPO_TO_REPO edges).
     */
    @Query(value = """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT c.caller_repo_id, e.repo_id
            FROM api_calls c
            INNER JOIN api_endpoints e ON c.endpoint_id = e.id
            INNER JOIN repos cr ON c.caller_repo_id = cr.id
            WHERE cr.user_id = :userId AND e.repo_id <> c.caller_repo_id
        ) pairs
        """, nativeQuery = true)
    long countDistinctCrossRepoCallPairsForUser(@Param("userId") Long userId);
}
