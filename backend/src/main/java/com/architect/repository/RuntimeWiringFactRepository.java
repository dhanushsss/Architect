package com.architect.repository;

import com.architect.model.Repo;
import com.architect.model.RuntimeWiringFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RuntimeWiringFactRepository extends JpaRepository<RuntimeWiringFact, Long> {

    void deleteByRepo(Repo repo);

    @Query("SELECT f FROM RuntimeWiringFact f JOIN FETCH f.repo r WHERE r.user.id = :userId")
    List<RuntimeWiringFact> findAllByUserId(@Param("userId") Long userId);
}
