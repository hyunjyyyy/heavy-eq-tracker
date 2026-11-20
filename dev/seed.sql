-- 시뮬레이터용 현장·장비·배정. 다시 돌려도 된다(있으면 건너뜀)
-- id 규칙은 시뮬레이터와 같다: 현장 100+n, 장비 현장id*1000+순번
SET @sites = 10, @per_site = 100;

INSERT IGNORE INTO site (id, name)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < @sites)
SELECT 100 + n, CONCAT('sim-site-', n) FROM s;

INSERT IGNORE INTO equipment (id, name, type, positioning_source)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < @sites),
               k(m) AS (SELECT 1 UNION ALL SELECT m + 1 FROM k WHERE m < @per_site)
SELECT (100 + n) * 1000 + m, CONCAT('sim-', 100 + n, '-', m), 'EXCAVATOR', 'PHONE_RTK'
FROM s CROSS JOIN k;

-- 열린 배정은 장비당 하나(uk_assignment_one_open)라 다시 돌리면 IGNORE로 건너뛴다
INSERT IGNORE INTO equipment_site_assignment (equipment_id, site_id, assigned_from)
SELECT id, id DIV 1000, '2000-01-01'
FROM equipment
WHERE name LIKE 'sim-%';
