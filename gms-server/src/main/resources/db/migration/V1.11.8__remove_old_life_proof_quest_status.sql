DELETE qp
FROM questprogress qp
JOIN queststatus qs ON qs.queststatusid = qp.queststatusid
WHERE qs.quest BETWEEN 30100 AND 30999;

DELETE FROM queststatus
WHERE quest BETWEEN 30100 AND 30999;
