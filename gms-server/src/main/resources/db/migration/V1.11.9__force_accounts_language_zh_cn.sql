UPDATE accounts
SET language = 3
WHERE language <> 3
   OR language IS NULL;

UPDATE command_info
SET enabled = 0
WHERE syntax = 'changel';
