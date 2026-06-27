UPDATE transactions
SET category = nullif(btrim(category), '')
WHERE category IS NOT NULL
  AND category IS DISTINCT FROM nullif(btrim(category), '');
