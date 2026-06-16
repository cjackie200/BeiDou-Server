INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'mobvac', 0, 1, 'MobVacCommand', 0
WHERE NOT EXISTS (
    SELECT 1 FROM command_info WHERE syntax = 'mobvac'
);

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'noguai', 0, 1, 'MobVacCommand', 0
WHERE NOT EXISTS (
    SELECT 1 FROM command_info WHERE syntax = 'noguai'
);
