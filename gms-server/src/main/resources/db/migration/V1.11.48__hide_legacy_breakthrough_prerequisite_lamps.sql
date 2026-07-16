INSERT INTO queststatus (characterid, quest, status, time, expires, forfeited, completed, info)
SELECT legacy.characterid,
       prerequisite.quest,
       2,
       UNIX_TIMESTAMP(),
       0,
       0,
       0,
       0
FROM (
    SELECT DISTINCT qs.characterid
    FROM queststatus qs
    JOIN questprogress qp ON qp.queststatusid = qs.queststatusid
    WHERE qs.quest = 30009
      AND qs.status = 1
      AND qp.progressid = -30009
      AND qp.progress = 'legacy-all'
) legacy
JOIN (
    SELECT 30006 AS quest
    UNION ALL SELECT 30007
    UNION ALL SELECT 30008
) prerequisite
WHERE NOT EXISTS (
    SELECT 1
    FROM queststatus existing
    WHERE existing.characterid = legacy.characterid
      AND existing.quest = prerequisite.quest
);
