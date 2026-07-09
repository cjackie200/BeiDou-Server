-- Wedding rings should stay in the wedding flow, not Pia's Perfect Pitch shop.
DELETE FROM shopitems
WHERE shopid = 9000069
  AND itemid IN (1112803, 1112806);
