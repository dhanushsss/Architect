package com.architect.repository;

import com.architect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGithubId(Long githubId);
    Optional<User> findByLogin(String login);
}
