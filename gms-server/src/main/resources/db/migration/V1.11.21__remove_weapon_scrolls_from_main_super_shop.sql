-- Keep weapon scrolls out of the main super shop.
-- Weapon 60%/30% scrolls are served by the class-specific shops instead.
DELETE FROM shopitems
WHERE shopid = 9900001
  AND itemid BETWEEN 2043000 AND 2044999;
