ALTER TABLE characters
    MODIFY equipslots INT(11) NOT NULL DEFAULT 96,
    MODIFY useslots INT(11) NOT NULL DEFAULT 96,
    MODIFY setupslots INT(11) NOT NULL DEFAULT 96,
    MODIFY etcslots INT(11) NOT NULL DEFAULT 96;

UPDATE characters
SET equipslots = 96,
    useslots = 96,
    setupslots = 96,
    etcslots = 96;
