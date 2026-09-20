-- DB-10 (#270): Teilindex für die Cleaner-Query in StaleImportJobCleaner.cleanUpStaleJobs
-- (WHERE status = 'RUNNING' AND created_at < ?).
--
-- Die beiden Indizes aus V05 (idx_import_jobs_user_id, idx_import_jobs_user_hash_status) führen
-- beide mit user_id, das in dieser Query nicht vorkommt — Postgres fiel deshalb auf einen
-- Sequential Scan über die ganze Tabelle zurück (per EXPLAIN bestätigt, siehe Issue #270).
--
-- Teilindex statt Vollindex, weil die Query nur je RUNNING-Zeilen sucht: DONE- und FAILED-Zeilen
-- wachsen mit jedem Upload und blieben im Index sonst toter Ballast.
CREATE INDEX idx_import_jobs_running_created_at
    ON import_jobs (created_at)
    WHERE status = 'RUNNING';
