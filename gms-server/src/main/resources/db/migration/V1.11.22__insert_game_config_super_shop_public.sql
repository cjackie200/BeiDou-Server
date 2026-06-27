INSERT INTO `game_config`(`config_type`, `config_sub_type`, `config_clazz`, `config_code`, `config_value`, `config_desc`, `update_time`)
SELECT 'server', 'Game Mechanics', 'java.lang.Boolean', 'use_super_shop_public', 'false', 'use_super_shop_public', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `game_config` WHERE `config_code` = 'use_super_shop_public'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'zh-CN', 'game_config', 'use_super_shop_public', '超级商店是否对普通玩家开放（true=普通玩家可见，false=仅GM可见）', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources`
    WHERE `lang_type` = 'zh-CN'
      AND `lang_base` = 'game_config'
      AND `lang_code` = 'use_super_shop_public'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'en-US', 'game_config', 'use_super_shop_public', 'Whether Super Shop is public to normal players (true=visible to players, false=GM only).', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources`
    WHERE `lang_type` = 'en-US'
      AND `lang_base` = 'game_config'
      AND `lang_code` = 'use_super_shop_public'
);
