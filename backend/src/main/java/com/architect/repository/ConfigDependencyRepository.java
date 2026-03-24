package com.architect.repository;

import com.architect.model.ConfigDependency;
import com.architect.model.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfigDependencyRepository extends JpaRepository<ConfigDependency, Long> {
    List<ConfigDependency> findByRepo(Repo repo);
    void deleteByRepo(Repo repo);
}
