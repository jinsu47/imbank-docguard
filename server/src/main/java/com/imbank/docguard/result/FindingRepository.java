package com.imbank.docguard.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface FindingRepository extends JpaRepository<Finding, Long> {

    List<Finding> findByAnalysisIdOrderByRowIndexAsc(UUID analysisId);

    @Modifying
    @Query("delete from Finding f where f.analysisId = :analysisId")
    void deleteByAnalysisId(UUID analysisId);
}
