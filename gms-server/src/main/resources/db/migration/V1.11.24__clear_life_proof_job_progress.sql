-- V1.11.12: 清理生命之证职业任务变更后的旧 task_key 进度数据
-- 背景：buildStages() 中所有 105 个职业任务（MAIN_JOB）的 task_key、怪物 ID、
--       击杀数量均重新设计。旧 key 不再匹配当前 StageConfig，若残留 active=1
--       行会阻止 activateNextTaskIfNeeded 推进阶段。
-- 策略：仅清理 MAIN_JOB 进度行；公共任务和可选任务未变，保留其进度。

DELETE FROM hp_challenge_progress
WHERE task_group = 'main_job';
