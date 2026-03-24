package com.architect.repository;

import com.architect.model.Repo;
import com.architect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RepoRepository extends JpaRepository<Repo, Long> {
    List<Repo> findByUser(User user);
    List<Repo> findByUserOrderByNameAsc(User user);
    Optional<Repo> findByUserAndGithubId(User user, Long githubId);
    Optional<Repo> findByFullName(String fullName);
    List<Repo> findByUserId(Long userId);
}
