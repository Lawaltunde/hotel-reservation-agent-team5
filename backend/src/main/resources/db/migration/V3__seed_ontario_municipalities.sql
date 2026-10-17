-- Development seed: a few benchmark municipalities so the city field works before ingestion runs.
-- The ingestion job (profile "ingestion") loads the full list and boundaries from Statistics Canada,
-- filling csd_code and boundary. MAT rates are intentionally left NULL: set them from each
-- municipality's published rate with a source and effective date; never guess.
INSERT INTO municipalities (id, name, name_key, alias_keys, centre, time_zone) VALUES
  (gen_random_uuid(), 'Toronto',          'toronto',          '{}',                         ST_MakePoint(-79.3832, 43.6532)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Ottawa',           'ottawa',           '{}',                         ST_MakePoint(-75.6972, 45.4215)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Mississauga',      'mississauga',      '{}',                         ST_MakePoint(-79.6441, 43.5890)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Hamilton',         'hamilton',         '{}',                         ST_MakePoint(-79.8711, 43.2557)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'London',           'london',           '{}',                         ST_MakePoint(-81.2453, 42.9849)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Kitchener',        'kitchener',        '{kitchener-waterloo}',       ST_MakePoint(-80.4925, 43.4516)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Windsor',          'windsor',          '{}',                         ST_MakePoint(-83.0364, 42.3149)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Kingston',         'kingston',         '{}',                         ST_MakePoint(-76.4860, 44.2312)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Niagara Falls',    'niagara falls',    '{}',                         ST_MakePoint(-79.0849, 43.0896)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Greater Sudbury',  'greater sudbury',  '{sudbury}',                  ST_MakePoint(-80.9930, 46.4917)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Sault Ste. Marie', 'sault ste. marie', '{the soo,sault ste marie}',  ST_MakePoint(-84.3333, 46.5219)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Thunder Bay',      'thunder bay',      '{}',                         ST_MakePoint(-89.2477, 48.3809)::geography, 'America/Toronto'),
  (gen_random_uuid(), 'Kenora',           'kenora',           '{}',                         ST_MakePoint(-94.4894, 49.7670)::geography, 'America/Winnipeg');
