INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'elementweapons', 2, 1, 'ElementWeaponsCommand', 2
WHERE NOT EXISTS (
    SELECT 1 FROM command_info WHERE syntax = 'elementweapons'
);
