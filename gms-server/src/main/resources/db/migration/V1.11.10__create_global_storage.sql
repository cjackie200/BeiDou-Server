CREATE TABLE IF NOT EXISTS `global_storage_items`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `page_no`            INT             NOT NULL,
    `itemid`             INT             NOT NULL,
    `inventorytype`      INT             NOT NULL,
    `quantity`           INT             NOT NULL,
    `owner`              VARCHAR(16)     NULL,
    `petid`              INT             NOT NULL DEFAULT '-1',
    `flag`               INT             NOT NULL DEFAULT '0',
    `expiration`         BIGINT          NOT NULL DEFAULT '-1',
    `gift_from`          VARCHAR(16)     NULL,
    `deposit_account_id` INT             NOT NULL,
    `deposit_char_id`    INT             NOT NULL,
    `deposit_char_name`  VARCHAR(13)     NOT NULL,
    `create_time`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_global_storage_page` (`page_no`, `id`),
    KEY `idx_global_storage_inventorytype` (`inventorytype`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `global_storage_equipment`
(
    `global_storage_item_id` BIGINT UNSIGNED NOT NULL,
    `upgradeslots`           INT             NOT NULL DEFAULT '0',
    `level`                  INT             NOT NULL DEFAULT '0',
    `str`                    INT             NOT NULL DEFAULT '0',
    `dex`                    INT             NOT NULL DEFAULT '0',
    `int`                    INT             NOT NULL DEFAULT '0',
    `luk`                    INT             NOT NULL DEFAULT '0',
    `hp`                     INT             NOT NULL DEFAULT '0',
    `mp`                     INT             NOT NULL DEFAULT '0',
    `watk`                   INT             NOT NULL DEFAULT '0',
    `matk`                   INT             NOT NULL DEFAULT '0',
    `wdef`                   INT             NOT NULL DEFAULT '0',
    `mdef`                   INT             NOT NULL DEFAULT '0',
    `acc`                    INT             NOT NULL DEFAULT '0',
    `avoid`                  INT             NOT NULL DEFAULT '0',
    `hands`                  INT             NOT NULL DEFAULT '0',
    `speed`                  INT             NOT NULL DEFAULT '0',
    `jump`                   INT             NOT NULL DEFAULT '0',
    `locked`                 INT             NOT NULL DEFAULT '0',
    `vicious`                INT             NOT NULL DEFAULT '0',
    `itemlevel`              INT             NOT NULL DEFAULT '1',
    `itemexp`                INT             NOT NULL DEFAULT '0',
    `ringid`                 INT             NOT NULL DEFAULT '-1',
    PRIMARY KEY (`global_storage_item_id`),
    CONSTRAINT `fk_global_storage_equipment_item`
        FOREIGN KEY (`global_storage_item_id`) REFERENCES `global_storage_items` (`id`)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `global_storage_logs`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `action`          VARCHAR(16)     NOT NULL,
    `page_no`         INT             NOT NULL,
    `storage_item_id` BIGINT UNSIGNED NOT NULL,
    `itemid`          INT             NOT NULL,
    `quantity`        INT             NOT NULL,
    `account_id`      INT             NOT NULL,
    `char_id`         INT             NOT NULL,
    `char_name`       VARCHAR(13)     NOT NULL,
    `create_time`     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_global_storage_logs_storage_item` (`storage_item_id`),
    KEY `idx_global_storage_logs_char` (`char_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
