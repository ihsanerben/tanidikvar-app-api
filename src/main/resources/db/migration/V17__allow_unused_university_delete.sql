-- Yalnız service katmanının pasiflik ve kullanım kontrollerinden geçen
-- üniversiteler silinebilir. FK'ler geçmişte kullanılan kayıtları ayrıca korur.
DROP TRIGGER universities_no_delete ON universities;
DROP TRIGGER university_departments_no_delete ON university_departments;
