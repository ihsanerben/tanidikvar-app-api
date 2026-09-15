ALTER TABLE universities ADD COLUMN city varchar(120);

CREATE INDEX idx_universities_city_search ON universities (search_fold(coalesce(city, '')));
