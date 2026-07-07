-- Fix Pia's Perfect Pitch shop after the temporary custom shoes item was removed.
-- Use the original Transparent Shoes item so clients do not need a new item resource.
DELETE FROM shopitems
WHERE shopid = 9000069
  AND itemid IN (1072153, 1073000);

INSERT INTO shopitems (shopid, itemid, price, pitch, position)
VALUES (9000069, 1072153, 0, 20, 19);
