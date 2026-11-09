-- 워크플로 v3.2 의 등급 미확정을 서버가 보고서를 읽지 못한 경우(UNKNOWN)와 구분해 저장합니다.
ALTER TABLE analyses DROP CONSTRAINT ck_analyses_risk_grade;
ALTER TABLE analyses ADD CONSTRAINT ck_analyses_risk_grade
    CHECK (risk_grade IN ('HIGH', 'MEDIUM', 'LOW', 'NONE', 'UNKNOWN', 'UNDECIDED'));
