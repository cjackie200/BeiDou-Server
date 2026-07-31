-- Add Dragon Transformation Potion to Pia's Perfect Pitch shop.
-- In shopitems, pitch is the required amount of item 4310000 (Perfect Pitch).
DELETE FROM shopitems
WHERE shopid = 9000069
  AND itemid = 2210003;

INSERT INTO shopitems (shopid, itemid, price, pitch, position)
VALUES (9000069, 2210003, 0, 1, 24);
