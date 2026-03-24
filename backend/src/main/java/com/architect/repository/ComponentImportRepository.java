package com.architect.repository;

import com.architect.model.ComponentImport;
import com.architect.model.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ComponentImportRepository extends JpaRepository<ComponentImport, Long> {
    List<ComponentImport> findBySourceRepo(Repo repo);
    List<ComponentImport> findByTargetRepo(Repo repo);
    List<ComponentImport> findBySourceRepoAndFilePath(Repo repo, String filePath);
    void deleteBySourceRepo(Repo repo);

    @Query("SELECT i FROM ComponentImport i WHERE i.sourceRepo.user.id = :userId")
    List<ComponentImport> findBySourceRepoUserId(@Param("userId") Long userId);
}
