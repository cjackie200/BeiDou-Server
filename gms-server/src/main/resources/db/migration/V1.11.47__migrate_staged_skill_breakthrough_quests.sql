CREATE TEMPORARY TABLE legacy_breakthrough_completed AS
SELECT DISTINCT characterid
FROM queststatus
WHERE quest = 30006
  AND status = 2;

CREATE TEMPORARY TABLE legacy_breakthrough_started AS
SELECT DISTINCT queststatusid, characterid
FROM queststatus
WHERE quest = 30006
  AND status = 1;

UPDATE queststatus qs
JOIN legacy_breakthrough_started legacy
  ON legacy.queststatusid = qs.queststatusid
SET qs.quest = 30009;

INSERT INTO questprogress (characterid, queststatusid, progressid, progress)
SELECT legacy.characterid,
       legacy.queststatusid,
       -30009,
       'legacy-all'
FROM legacy_breakthrough_started legacy
WHERE NOT EXISTS (
    SELECT 1
    FROM questprogress qp
    WHERE qp.queststatusid = legacy.queststatusid
      AND qp.progressid = -30009
);

INSERT INTO queststatus (characterid, quest, status, time, expires, forfeited, completed, info)
SELECT legacy.characterid,
       staged.quest,
       2,
       UNIX_TIMESTAMP(),
       0,
       0,
       0,
       0
FROM legacy_breakthrough_completed legacy
JOIN (
    SELECT 30007 AS quest
    UNION ALL SELECT 30008
    UNION ALL SELECT 30009
) staged
WHERE NOT EXISTS (
    SELECT 1
    FROM queststatus existing
    WHERE existing.characterid = legacy.characterid
      AND existing.quest = staged.quest
);

DROP TEMPORARY TABLE legacy_breakthrough_started;
DROP TEMPORARY TABLE legacy_breakthrough_completed;
