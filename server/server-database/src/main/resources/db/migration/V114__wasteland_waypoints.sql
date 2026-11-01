-- ADR-141 (2026-11-01, lô 26) — trạm truyền tống cho arc 3/arc 4: mạng ADR-100 dừng ở Trạm Thí Luyện (208) trong khi
-- trục chính đã dài tới 417. Ba trạm mới đặt ĐÚNG giữa ba vùng an toàn đã có (V109 cổng nội môn 237,5–242,5; V113
-- waypost_wasteland_descent 301,5–306,5 và waypost_deep_dead_vein 365,5–370,5) — dời tới là đứng trong vùng an toàn,
-- kể cả trạm 368 nằm giữa dải PvP wasteland_deep. Mở khi đi ngang (luật ADR-100), không mở sẵn.
INSERT INTO waypoint (code, name_key, x, y, sort_order, always_open)
VALUES ('inner_sect_gate', 'waypoint.inner_sect_gate.name', 240, 0, 7, FALSE),
       ('wasteland_descent', 'waypoint.wasteland_descent.name', 304, 0, 8, FALSE),
       ('deep_dead_vein', 'waypoint.deep_dead_vein.name', 368, 0, 9, FALSE)
ON CONFLICT DO NOTHING;
