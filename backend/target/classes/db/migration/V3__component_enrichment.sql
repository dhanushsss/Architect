-- Change 5: Enrich component_imports with import classification
ALTER TABLE component_imports
    ADD COLUMN IF NOT EXISTS import_type  VARCHAR(20) DEFAULT 'EXTERNAL',
    ADD COLUMN IF NOT EXISTS resolved_file VARCHAR(500);

-- Change 5: Enrich dependency_edges with import metadata
ALTER TABLE dependency_edges
    ADD COLUMN IF NOT EXISTS import_type  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS source_file  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS target_file  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS package_path VARCHAR(500);

-- Index for fast component trace queries
CREATE INDEX IF NOT EXISTS idx_component_imports_source_file
    ON component_imports (source_repo_id, file_path);

CREATE INDEX IF NOT EXISTS idx_component_imports_import_type
    ON component_imports (import_type);
