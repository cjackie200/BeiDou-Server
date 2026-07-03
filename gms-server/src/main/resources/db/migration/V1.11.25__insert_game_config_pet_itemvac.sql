INSERT INTO `game_config`(`config_type`, `config_sub_type`, `config_clazz`, `config_code`, `config_value`, `config_desc`, `update_time`)
SELECT 'server', 'Game Mechanics', 'java.lang.Boolean', 'pet_itemvac', 'false', 'pet_itemvac', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `game_config` WHERE `config_code` = 'pet_itemvac'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'zh-CN', 'game_config', 'pet_itemvac', '是否开启宠物大范围拾取物品（true=开启，false=关闭）', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources`
    WHERE `lang_type` = 'zh-CN'
      AND `lang_base` = 'game_config'
      AND `lang_code` = 'pet_itemvac'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'en-US', 'game_config', 'pet_itemvac', 'Enable pet large-range item pickup (true=enabled, false=disabled).', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources`
    WHERE `lang_type` = 'en-US'
      AND `lang_base` = 'game_config'
      AND `lang_code` = 'pet_itemvac'
);
