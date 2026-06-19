DELETE FROM hp_challenge_event_log;
DELETE FROM hp_challenge_gm_log;
DELETE FROM hp_challenge_reward_log;
DELETE FROM hp_challenge_progress;
DELETE FROM hp_challenge_state;

ALTER TABLE hp_challenge_progress
    ADD COLUMN task_order INT(11) NOT NULL DEFAULT 0 COMMENT '阶段内线性顺序' AFTER task_key,
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 0 COMMENT '当前是否可计数' AFTER selected,
    ADD COLUMN accepted_at TIMESTAMP NULL DEFAULT NULL COMMENT '领取时间' AFTER completed,
    ADD COLUMN completed_at TIMESTAMP NULL DEFAULT NULL COMMENT '完成时间' AFTER accepted_at;

CREATE INDEX idx_hp_challenge_progress_active
    ON hp_challenge_progress (character_id, stage, active);
