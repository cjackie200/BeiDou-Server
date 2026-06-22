CREATE TABLE IF NOT EXISTS `hp_challenge_state`
(
    `id`                     INT(11)      NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `character_id`           INT(11)      NOT NULL COMMENT '角色ID',
    `route_locked`           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已锁定挑战洗血路线',
    `current_stage`          INT(11)      NOT NULL DEFAULT 1 COMMENT '当前阶段',
    `highest_rewarded_stage` INT(11)      NOT NULL DEFAULT 0 COMMENT '已领取奖励的最高阶段',
    `status`                 VARCHAR(32)  NOT NULL DEFAULT 'STARTED' COMMENT '状态',
    `created_at`             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`             TIMESTAMP    NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hp_challenge_state_character` (`character_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT '挑战洗血角色状态';

CREATE TABLE IF NOT EXISTS `hp_challenge_progress`
(
    `id`             INT(11)      NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `character_id`   INT(11)      NOT NULL COMMENT '角色ID',
    `stage`          INT(11)      NOT NULL COMMENT '阶段',
    `task_group`     VARCHAR(32)  NOT NULL COMMENT '任务组：main_common/main_job/optional',
    `task_key`       VARCHAR(64)  NOT NULL COMMENT '任务键',
    `task_order`     INT(11)      NOT NULL DEFAULT 0 COMMENT '阶段内线性顺序',
    `target_type`    VARCHAR(32)  NOT NULL COMMENT '目标类型',
    `target_id`      INT(11)      NOT NULL DEFAULT 0 COMMENT '展示用目标ID，0表示复合目标',
    `current_count`  INT(11)      NOT NULL DEFAULT 0 COMMENT '当前进度',
    `required_count` INT(11)      NOT NULL COMMENT '目标进度',
    `selected`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已选择，仅8选3有效',
    `active`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '当前是否可计数',
    `completed`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否完成',
    `accepted_at`    TIMESTAMP    NULL     DEFAULT NULL COMMENT '领取时间',
    `completed_at`   TIMESTAMP    NULL     DEFAULT NULL COMMENT '完成时间',
    `created_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     TIMESTAMP    NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hp_challenge_progress_task` (`character_id`, `stage`, `task_group`, `task_key`),
    KEY `idx_hp_challenge_progress_character_stage` (`character_id`, `stage`),
    KEY `idx_hp_challenge_progress_active` (`character_id`, `stage`, `active`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT '挑战洗血任务进度';

CREATE TABLE IF NOT EXISTS `hp_challenge_reward_log`
(
    `id`           INT(11)      NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `character_id` INT(11)      NOT NULL COMMENT '角色ID',
    `stage`        INT(11)      NOT NULL COMMENT '阶段',
    `job_id`       INT(11)      NOT NULL COMMENT '领奖时职业ID',
    `before_maxhp` INT(11)      NOT NULL COMMENT '奖励前最大HP',
    `before_maxmp` INT(11)      NOT NULL COMMENT '奖励前最大MP',
    `before_hp`    INT(11)      NOT NULL COMMENT '奖励前当前HP',
    `before_mp`    INT(11)      NOT NULL COMMENT '奖励前当前MP',
    `after_maxhp`  INT(11)      NOT NULL COMMENT '奖励后最大HP',
    `after_maxmp`  INT(11)      NOT NULL COMMENT '奖励后最大MP',
    `after_hp`     INT(11)      NOT NULL COMMENT '奖励后当前HP',
    `after_mp`     INT(11)      NOT NULL COMMENT '奖励后当前MP',
    `operator`     VARCHAR(64)  NULL     DEFAULT NULL COMMENT '操作人',
    `reverted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已回滚',
    `reverted_at`  TIMESTAMP    NULL     DEFAULT NULL COMMENT '回滚时间',
    `created_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_hp_challenge_reward_character` (`character_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT '挑战洗血奖励日志';

CREATE TABLE IF NOT EXISTS `hp_challenge_event_log`
(
    `id`           INT(11)     NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `character_id` INT(11)     NOT NULL COMMENT '角色ID',
    `stage`        INT(11)     NOT NULL COMMENT '阶段',
    `task_group`   VARCHAR(32) NOT NULL COMMENT '任务组',
    `task_key`     VARCHAR(64) NOT NULL COMMENT '任务键',
    `event_key`    VARCHAR(64) NOT NULL COMMENT '唯一事件键',
    `created_at`   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hp_challenge_event` (`character_id`, `stage`, `task_group`, `task_key`, `event_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT '挑战洗血去重事件日志';

CREATE TABLE IF NOT EXISTS `hp_challenge_gm_log`
(
    `id`           INT(11)      NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `character_id` INT(11)      NOT NULL COMMENT '目标角色ID',
    `operator_id`  INT(11)      NOT NULL COMMENT 'GM角色ID',
    `operator`     VARCHAR(64)  NOT NULL COMMENT 'GM名称',
    `action`       VARCHAR(32)  NOT NULL COMMENT '操作',
    `detail`       VARCHAR(255) NOT NULL COMMENT '详情',
    `created_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_hp_challenge_gm_log_character` (`character_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT '挑战洗血GM操作日志';

SET @sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'hp_challenge_progress' AND column_name = 'task_order'
    ),
    'ALTER TABLE hp_challenge_progress ADD COLUMN task_order INT(11) NOT NULL DEFAULT 0 COMMENT ''阶段内线性顺序'' AFTER task_key',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'hp_challenge_progress' AND column_name = 'active'
    ),
    'ALTER TABLE hp_challenge_progress ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''当前是否可计数'' AFTER selected',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'hp_challenge_progress' AND column_name = 'accepted_at'
    ),
    'ALTER TABLE hp_challenge_progress ADD COLUMN accepted_at TIMESTAMP NULL DEFAULT NULL COMMENT ''领取时间'' AFTER completed',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'hp_challenge_progress' AND column_name = 'completed_at'
    ),
    'ALTER TABLE hp_challenge_progress ADD COLUMN completed_at TIMESTAMP NULL DEFAULT NULL COMMENT ''完成时间'' AFTER accepted_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'hp_challenge_progress' AND index_name = 'idx_hp_challenge_progress_active'
    ),
    'CREATE INDEX idx_hp_challenge_progress_active ON hp_challenge_progress (character_id, stage, active)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'hpchallenge', 3, 1, 'HpChallengeCommand', 3
WHERE NOT EXISTS (
    SELECT 1 FROM command_info WHERE syntax = 'hpchallenge'
);
