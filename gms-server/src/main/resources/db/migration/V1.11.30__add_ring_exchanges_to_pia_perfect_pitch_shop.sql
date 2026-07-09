-- Add Perfect Pitch ring exchanges to Pia's shop.
-- In shopitems, pitch is the required amount of item 4310000 (Perfect Pitch).
INSERT INTO shopitems (shopid, itemid, price, pitch, position)
SELECT new_items.shopid, new_items.itemid, new_items.price, new_items.pitch, new_items.position
FROM (
    SELECT 9000069 AS shopid, 1112300 AS itemid, 0 AS price, 10 AS pitch, 10 AS position
    UNION ALL SELECT 9000069, 1112301, 0, 20, 11
    UNION ALL SELECT 9000069, 1112302, 0, 30, 12
    UNION ALL SELECT 9000069, 1112303, 0, 10, 13
    UNION ALL SELECT 9000069, 1112304, 0, 20, 14
    UNION ALL SELECT 9000069, 1112305, 0, 30, 15
    UNION ALL SELECT 9000069, 1112413, 0, 40, 16
    UNION ALL SELECT 9000069, 1112414, 0, 50, 17
    UNION ALL SELECT 9000069, 1112405, 0, 60, 18
) new_items
WHERE NOT EXISTS (
    SELECT 1
    FROM shopitems existing
    WHERE existing.shopid = new_items.shopid
      AND existing.itemid = new_items.itemid
);
