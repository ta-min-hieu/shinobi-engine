-- ADR-083 — thư chở món cường hoá GIỮ nguyên cấp. Dòng cũ = 0 (trước đây thư chỉ nhận món chưa cường hoá).
ALTER TABLE mail_item ADD COLUMN enhance_level INT NOT NULL DEFAULT 0 CHECK (enhance_level BETWEEN 0 AND 15);
