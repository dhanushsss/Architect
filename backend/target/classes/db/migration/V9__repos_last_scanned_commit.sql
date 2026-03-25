ALTER TABLE repos ADD COLUMN IF NOT EXISTS last_scanned_commit_sha VARCHAR(64);
