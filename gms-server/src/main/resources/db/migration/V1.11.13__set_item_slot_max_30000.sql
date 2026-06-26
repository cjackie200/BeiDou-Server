UPDATE game_config
SET config_sub_type = 'Game Mechanics',
    config_clazz = 'java.lang.Short',
    config_value = '30000',
    config_desc = '消耗栏和其他栏的物品最大堆叠，当前安全配置为30000，0为默认取wz定义的堆叠数量(Max item slots in Consume and Etc)'
WHERE config_type = 'server'
  AND config_code = 'item_slot_max';

INSERT INTO game_config (config_type, config_sub_type, config_clazz, config_code, config_value, config_desc)
SELECT 'server',
       'Game Mechanics',
       'java.lang.Short',
       'item_slot_max',
       '30000',
       '消耗栏和其他栏的物品最大堆叠，当前安全配置为30000，0为默认取wz定义的堆叠数量(Max item slots in Consume and Etc)'
WHERE NOT EXISTS (
    SELECT 1
    FROM game_config
    WHERE config_type = 'server'
      AND config_code = 'item_slot_max'
);
