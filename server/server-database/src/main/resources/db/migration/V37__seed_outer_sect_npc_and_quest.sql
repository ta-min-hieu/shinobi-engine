-- V37 — NPC THỨ HAI của dự án, và quest THỨ NĂM: "Báo Danh Ngoại Môn" (STORY.md §6 arc 2, nhịp MỞ).
--
-- CHỈ THÊM DỮ LIỆU. Không cột mới, không bảng mới, không ràng buộc mới — lần thứ tư liên tiếp
-- (V28 → V30 → V33 → V37) một quest mới không tốn một dòng Java NỘI DUNG nào. Java có đổi trong
-- đợt này, nhưng đổi ở tầng CƠ CHẾ (`ProcessQuestEventUseCase` học loại sự kiện thứ hai), không
-- phải ở tầng nội dung: không một dòng Java nào biết `senior_sister` hay `report_to_outer_sect`.
--
-- =============================================================================================
-- HAI CÁI "ĐẦU TIÊN" NẰM TRONG MIGRATION NÀY
-- =============================================================================================
-- 1. NPC THỨ HAI. Từ Phase 6 tới nay thế giới có ĐÚNG MỘT NPC (`town_elder`, V10/V26). Mọi chỗ suy
--    ra danh tính NPC từ một hằng số đều phải soi lại cùng đợt — `AssetCategoryResolver`
--    (tools/asset-pipeline) tự ghi sẵn lời nhắc đó trong javadoc của chính nó và đã được sửa.
--    `NpcPlacementIntegrationTest.shouldSeedTownElder_atExactlyTheOldHardcodedSpot` từng khẳng định
--    `hasSize(1)` — nay nói đúng thứ nó thật sự muốn gác (ông lão vẫn ở ĐÚNG chỗ cũ), không phải
--    "thế giới chỉ có một NPC".
-- 2. QUEST KHÔNG PHẢI "GIẾT N CON". Bốn quest trước đều đếm `monster_code`; quest này đếm sự kiện
--    `npc_talked`. Lý do cơ chế phải đi trước: xem javadoc `ProcessQuestEventUseCase`.
--
-- =============================================================================================
-- ⚠️ CÒN TREO — CÂU HỎI THIẾT KẾ CHO CHỦ DỰ ÁN (CLAUDE.md §1: AI đề xuất, người quyết)
-- =============================================================================================
-- STORY.md §6 xếp arc 1 ở **Lv1–18** và arc 2 ở **Lv19–36**. Nhưng trọn chuỗi arc 1 (bốn quest)
-- chỉ đưa người chơi tới ĐÚNG **Cấp 10** — con số đó là ràng buộc CỐ Ý của V33 (Cấp 10 = mốc mở
-- khoá chọn hệ phái). Nghĩa là quãng **Lv10–18 hiện không có nội dung nào**.
--
-- Migration này KHÔNG tự ý lấp quãng đó (một cục EXP to ở một quest đi bộ 4 mét là lời nói dối về
-- độ khó). Nó chỉ mở CÁNH CỬA TƯỜNG THUẬT sang arc 2: người chơi qua khảo hạch thì thôi làm tạp
-- dịch, và người bàn giao họ cho ngoại môn là Tô Vân Nhi (§5). Lấp quãng Lv10–18 bằng cái gì —
-- nhiệm vụ ngày (§7 đã có dòng lore sẵn)? thêm quest arc 1? kéo giãn đường cong EXP? — là quyết
-- định của chủ dự án, không phải của AI.

-- ---------------------------------------------------------------------------------------------
-- 1. Danh tính NPC — Tô Vân Nhi (STORY.md §5: sư tỷ đồng hành, vào trước người chơi 3 năm)
--
--    `code` = 'senior_sister' và nó là KHOÁ NGHIỆP VỤ ĐI TRÊN DÂY (`TalkToNpcCommand.npc_code`),
--    nên nó tiếng Anh và nó mô tả VAI, đúng tiền lệ 'town_elder' — tên hiển thị tu tiên nằm ở
--    bundle i18n (`npc.senior_sister.name`), không nằm trong DB (ADR-038 D5/D7).
--
--    ⚠️ `dialogue_script_ref` PHẢI trỏ tới một file có thật: `ScriptValidator` duyệt mọi script lúc
--    khởi động và chặn server nếu thiếu (SCRIPTING.md §8). Đó chính là lý do `shop_keeper` tới giờ
--    vẫn chưa được seed (ADR-040 D7) — không phải vì quên. `senior_sister.js` đã có trong đợt này.
-- ---------------------------------------------------------------------------------------------
INSERT INTO npc_template (code, name_key, dialogue_script_ref)
SELECT 'senior_sister', 'npc.senior_sister.name', '/scripts/dialogue/senior_sister.js'
 WHERE NOT EXISTS (SELECT 1 FROM npc_template WHERE code = 'senior_sister');

-- ---------------------------------------------------------------------------------------------
-- 2. Vị trí — x = 2, rìa ĐÔNG của sân tông môn, ngay trước bệ nhảy đầu tiên
--
--    BỐN ràng buộc độc lập nhau đều phải thoả. Ba cái đầu đọc được từ dữ liệu đã seed; cái thứ tư
--    thì KHÔNG — và đó là cả câu chuyện, xem (d).
--
--    (a) NẰM TRONG VÙNG AN TOÀN. `safe_zone.sect_grounds` = x ∈ [-15, 5] (V35). x=2 nằm trong.
--        Không phải chi tiết cho đẹp: người chơi đi gặp cô sẽ được hồi đầy máu khi bước vào vùng
--        (ADR-046), nên quest này không bao giờ là cái bẫy máu.
--
--    (b) NGOÀI TẦM VỚI CỦA THẠCH VỆ. Luật V32 (đứng lại nói chuyện = đứng yên một chỗ): khoảng cách
--        phải > leash + tầm đánh 2 = 12. Thạch vệ gần nhất @ -20 ⇒ |2 - (-20)| = 22 > 12 ✓.
--        ⚠️ Chính ràng buộc này loại phương án "đặt cô cạnh Chu Hoành ở phía tây": x=-9 chỉ cách
--        thạch vệ 11 — TRƯỢT, dù nhìn trên trục thì có vẻ xa. Đây là lần thứ hai luật V32 bác một
--        vị trí trông-thì-ổn (lần đầu: V33 §3).
--
--    (c) PHÍA ĐÔNG THÌ AN TOÀN SẴN. Hàng xóm gần nhất là mộc nhân @ 8, và mộc nhân có
--        `aggro_radius_meters = 0` (V25 §3 — nó không bao giờ gây hấn), nên 6 mét là thừa đủ.
--
--    (d) 🐛 **KHÔNG ĐƯỢC ĐỨNG TRONG MỘT BỆ NHẢY DEMO.** `WorldFacade.DEMO_PLATFORMS` (và bản sao
--        client `DemoPlatforms.PLATFORMS`) chiếm x ∈ **[3,7] · [9,13] · [15,19]**, và chúng là KHỐI
--        3D thật có depth test. Bản đầu của migration này đặt cô ở **x=4** — tức là GIỮA bệ thứ
--        nhất — và trong client thật cô bị bệ **vẽ đè lên tới cổ**: chỉ còn cái đầu nổi trên mặt
--        khối olive. x=2 nằm trước mép bệ 1 mét.
--
--        ⚠️ **Ba thứ đều nói "ổn" trong khi nó hỏng**, và đó là lý do luật này được viết ra:
--          · Test đơn/tích hợp không thấy — không test nào biết khái niệm "bệ nhảy".
--          · `runWorldPreview` không thấy — công cụ đó đặt entity theo TOẠ ĐỘ CỦA RIÊNG NÓ, không
--            đọc `npc_spawn`; chính javadoc của nó có dòng "toàn bộ khung nằm TRƯỚC bệ demo đầu
--            tiên", tức là nó đã tự tránh cái bẫy này cho riêng mình từ lâu.
--          · Ba ràng buộc (a)(b)(c) đều xanh.
--        Chỉ **client thật** mới nói ra. Cùng họ với lỗi nền đất `[-55, 60]` (V32/V33): một hằng số
--        phía CLIENT không có gì nối với `npc_spawn`/`monster_spawn` trong DB ngoài một dòng ghi chú.
--        Nay đã có hàng rào bằng máy: `SpawnGeometryIntegrationTest` (server-gateway) đọc MỌI dòng
--        `npc_spawn`/`monster_spawn` từ Postgres thật rồi đối chiếu với `WorldFacade.demoPlatforms()`.
--
--    Bố cục trục chơi sau V37:
--
--      sân khảo hạch -47 · thạch vệ -33 · thạch vệ -20 · [Chu Hoành -5] · [người chơi 0]
--        · [TÔ VÂN NHI 2] · «bệ 3–7» · mộc nhân 8 · «bệ 9–13» · mộc nhân 14 · «bệ 15–19»
--        · tán tu 21 · tán tu 34 · quỷ đá 47
--
--    Đáng chú ý: MỌI điểm spawn quái có sẵn đều đã lọt vào khe giữa các bệ (8 nằm giữa 7 và 9;
--    14 nằm giữa 13 và 15). Bất biến đó đúng từ trước, chỉ là chưa ai viết nó ra.
--
--    Cô đứng giữa sân tông môn và bãi luyện công — đúng chỗ một đệ tử ngoại môn ba năm hay đứng,
--    và cũng là chỗ người chơi đi ngang mỗi lần ra bãi mộc nhân.
-- ---------------------------------------------------------------------------------------------
INSERT INTO npc_spawn (npc_template_id, world_code, pos_x, pos_y)
SELECT nt.id, 'main', 2, 0
  FROM npc_template nt
 WHERE nt.code = 'senior_sister'
   AND NOT EXISTS (
       SELECT 1 FROM npc_spawn ns
        WHERE ns.npc_template_id = nt.id AND ns.world_code = 'main');

-- ---------------------------------------------------------------------------------------------
-- 3. Quest 5. Nội dung (mục tiêu, phần thưởng) nằm HẾT trong script (Điều 27); bảng này chỉ giữ
--    định danh + đường dẫn script + điều kiện MỞ.
--
--    `prerequisite_quest_id` trỏ về `awakening_trial` (ADR-044, mốc CLAIMED) — arc 2 không được mở
--    trước khi arc 1 ĐÓNG XONG, và "đóng xong" ở đây có nghĩa đen: người chơi đã quay lại lĩnh
--    thưởng khảo hạch, tức đã ở Cấp 10, tức đã chọn được hệ phái. Tra theo `code` chứ KHÔNG hardcode
--    id (cùng lý do đã ghi ở V28/V30/V33).
--
--    `type` = 'MAIN': đây là chính tuyến, không phải nhiệm vụ ngày.
-- ---------------------------------------------------------------------------------------------
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'report_to_outer_sect',
       'MAIN',
       '/scripts/quest/report_to_outer_sect.js',
       (SELECT id FROM quest_template WHERE code = 'awakening_trial')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'report_to_outer_sect');
