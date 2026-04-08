-- Closed-loop feedback: track what actually happened after a PR merged.
-- Compares predictions (pr_predictions) against real outcomes (reverts, hotfixes, incidents).

CREATE TABLE pr_prediction_outcomes (
    id              BIGSERIAL PRIMARY KEY,
    prediction_id   BIGINT REFERENCES pr_predictions(id),
    pr_number       INTEGER        NOT NULL,
    repo_full_name  VARCHAR(255)   NOT NULL,
    merge_sha       VARCHAR(40),
    merged_at       TIMESTAMPTZ,

    -- Outcome signals detected post-merge
    outcome         VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
        -- PENDING   = merged, waiting for signal window to close
        -- CORRECT   = prediction aligned with outcome (no incident)
        -- INCORRECT = prediction missed real breakage
        -- REVERTED  = PR was reverted post-merge
        -- HOTFIXED  = follow-up fix detected in same files
        -- INCIDENT  = linked to a reported incident

    revert_pr_number   INTEGER,        -- PR number of the revert, if detected
    hotfix_pr_number   INTEGER,        -- PR number of the hotfix, if detected
    hotfix_detected_at TIMESTAMPTZ,    -- when hotfix/revert was detected

    -- Signal metadata
    revert_detected    BOOLEAN NOT NULL DEFAULT FALSE,
    hotfix_detected    BOOLEAN NOT NULL DEFAULT FALSE,
    time_to_revert_min INTEGER,        -- minutes between merge and revert
    time_to_hotfix_min INTEGER,        -- minutes between merge and hotfix

    -- For future: link to external incident tracking
    incident_url       TEXT,

    -- Accuracy tracking
    predicted_risk     VARCHAR(20),    -- copy from prediction for easy querying
    was_prediction_correct BOOLEAN,    -- final assessment: did prediction match outcome?

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ          -- when outcome was finalized
);

CREATE INDEX idx_pro_prediction_id ON pr_prediction_outcomes(prediction_id);
CREATE INDEX idx_pro_repo_outcome  ON pr_prediction_outcomes(repo_full_name, outcome);
CREATE INDEX idx_pro_merged_at     ON pr_prediction_outcomes(merged_at);

-- Accuracy summary view for dashboards
CREATE OR REPLACE VIEW pr_prediction_accuracy AS
SELECT
    repo_full_name,
    COUNT(*)                                                          AS total_predictions,
    COUNT(*) FILTER (WHERE outcome != 'PENDING')                      AS resolved_predictions,
    COUNT(*) FILTER (WHERE was_prediction_correct = TRUE)             AS correct_predictions,
    COUNT(*) FILTER (WHERE was_prediction_correct = FALSE)            AS incorrect_predictions,
    COUNT(*) FILTER (WHERE outcome = 'REVERTED')                      AS reverted_count,
    COUNT(*) FILTER (WHERE outcome = 'HOTFIXED')                      AS hotfixed_count,
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE was_prediction_correct = TRUE)
        / NULLIF(COUNT(*) FILTER (WHERE outcome != 'PENDING'), 0), 1
    )                                                                  AS accuracy_pct,
    AVG(time_to_revert_min) FILTER (WHERE revert_detected = TRUE)     AS avg_revert_time_min,
    AVG(time_to_hotfix_min) FILTER (WHERE hotfix_detected = TRUE)     AS avg_hotfix_time_min
FROM pr_prediction_outcomes
GROUP BY repo_full_name;
