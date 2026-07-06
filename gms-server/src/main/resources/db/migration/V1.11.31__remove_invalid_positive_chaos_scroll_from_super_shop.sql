-- 2049115 has String.wz text but no Item.wz consume data in this client data set.
-- Leaving it in a shop can disconnect clients when they render the item list.
DELETE FROM shopitems
WHERE shopid = 9900212
  AND itemid = 2049115;
