INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'elementready', 2, 1, 'ElementReadyCommand', 2
WHERE NOT EXISTS (
    SELECT 1 FROM command_info WHERE syntax = 'elementready'
);
