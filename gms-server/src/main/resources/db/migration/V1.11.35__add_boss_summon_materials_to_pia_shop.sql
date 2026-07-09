-- Add boss summon materials to Pia's Perfect Pitch shop.
-- In shopitems, pitch is the required amount of item 4310000 (Perfect Pitch).
DELETE FROM shopitems
WHERE shopid = 9000069
  AND itemid IN (4001017, 4031179, 4000138, 4032246);

INSERT INTO shopitems (shopid, itemid, price, pitch, position)
VALUES (9000069, 4001017, 0, 2, 20),
       (9000069, 4031179, 0, 2, 21),
       (9000069, 4000138, 0, 2, 22),
       (9000069, 4032246, 0, 2, 23);
