package com.architect.repository;

import com.architect.model.RuntimeWiringWarning;
import com.architect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RuntimeWiringWarningRepository extends JpaRepository<RuntimeWiringWarning, Long> {

    List<RuntimeWiringWarning> findByUserOrderByCreatedAtDesc(User user);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM RuntimeWiringWarning w WHERE w.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
