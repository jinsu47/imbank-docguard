package com.imbank.docguard.result;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AnalysisResultRecordRepository extends JpaRepository<AnalysisResultRecord, UUID> {
}
