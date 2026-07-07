-- Add Transparent Shoes to Pia's Perfect Pitch shop.
-- In shopitems, pitch is the required amount of item 4310000 (Perfect Pitch).
DELETE FROM shopitems
WHERE shopid = 9000069
  AND itemid IN (1072153, 1073000);

INSERT INTO shopitems (shopid, itemid, price, pitch, position)
VALUES (9000069, 1072153, 0, 20, 19);
