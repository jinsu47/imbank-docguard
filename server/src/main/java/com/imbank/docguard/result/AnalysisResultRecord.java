package com.imbank.docguard.result;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 암호화한 분석 결과 (DATABASE.md 3장 analysis_results). 분석 하나에 하나, 변경 없음. */
@Entity
@Table(name = "analysis_results")
public class AnalysisResultRecord {

    @Id
    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "encrypted_payload", nullable = false)
    private byte[] encryptedPayload;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AnalysisResultRecord() {
    }

    AnalysisResultRecord(UUID analysisId, byte[] encryptedPayload, short keyVersion, Instant now) {
        this.analysisId = analysisId;
        this.encryptedPayload = encryptedPayload;
        this.keyVersion = keyVersion;
        this.createdAt = now;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public byte[] getEncryptedPayload() {
        return encryptedPayload;
    }

    public short getKeyVersion() {
        return keyVersion;
    }
}
