CREATE TEMPORARY TABLE global_storage_stack_merge AS
    SELECT MIN(id) AS keeper_id, SUM(quantity) AS total_quantity
    FROM global_storage_items
    WHERE inventorytype <> 1
    GROUP BY itemid, inventorytype;

UPDATE global_storage_items target
JOIN global_storage_stack_merge merged ON merged.keeper_id = target.id
SET target.quantity = merged.total_quantity;

DELETE duplicate_item
FROM global_storage_items duplicate_item
JOIN global_storage_items keeper
  ON keeper.itemid = duplicate_item.itemid
 AND keeper.inventorytype = duplicate_item.inventorytype
JOIN global_storage_stack_merge merged ON merged.keeper_id = keeper.id
WHERE duplicate_item.inventorytype <> 1
  AND duplicate_item.id <> merged.keeper_id;

DROP TEMPORARY TABLE global_storage_stack_merge;

CREATE INDEX idx_global_storage_stack
    ON global_storage_items (itemid, inventorytype);
