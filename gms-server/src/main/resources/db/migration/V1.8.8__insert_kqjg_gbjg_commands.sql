INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'kqjg', 0, 1, 'OpenMobVacCommand', 0
WHERE NOT EXISTS (
    SELECT 1 FROM command_info WHERE syntax = 'kqjg'
);

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'gbjg', 0, 1, 'CloseMobVacCommand', 0
WHERE NOT EXISTS (
    SELECT 1 FROM command_info WHERE syntax = 'gbjg'
);
