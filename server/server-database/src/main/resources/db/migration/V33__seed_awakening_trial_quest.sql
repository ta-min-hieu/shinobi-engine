-- V33 — quest THỨ TƯ, và là nhịp ĐÓNG của arc 1: "Khảo Hạch Thức Tỉnh Linh Căn"
-- (STORY.md §6 arc 1, mục Cao trào/Mở khoá).
--
-- CHỈ THÊM DỮ LIỆU. Không cột mới, không bảng mới, không ràng buộc mới — lần thứ ba liên tiếp
-- (V28 → V30 → V33) một quest mới không tốn một dòng Java sản phẩm nào.
--
-- STORY.md §6 arc 1 nguyên văn: *"Đánh đuổi được hắn → Chu Hoành để ý → được đặc cách dự khảo hạch
-- → thức tỉnh linh căn (Lv10)"*, và *"Mở khoá: chọn hệ (Lv10)"*. Ba nhịp trước đã có
-- (`kill_training_dummies` V11 · `night_watch_bandit` V28 · `clear_old_archive` V30); đây là nhịp
-- thứ tư và cuối của arc.
--
-- ⚠️ ĐÂY LÀ QUEST ĐẦU TIÊN CÓ MỘT MỐC CƠ CHẾ Ở PHÍA SAU NÓ. Ba quest trước thưởng EXP rồi thôi;
-- quest này phải ĐƯA NGƯỜI CHƠI TỚI CẤP 10, vì cấp 10 chính là chỗ `SelectFactionUseCase.MIN_LEVEL`
-- mở khoá việc chọn hệ phái — thứ mà lời thoại của Chu Hoành hứa hẹn. Nếu người chơi lĩnh thưởng
-- xong mà vẫn Cấp 9 thì quest đã nói dối. Con số 1800 EXP ở `awakening_trial.js` được CHỌN TỪ đúng
-- ràng buộc đó, phép tính đầy đủ nằm ở `QuestIntegrationTest
-- .shouldLandExactlyAtLevelTen_afterTheWholeArcOneChain` (server-database) — nó CHƠI cả bốn quest
-- qua use case thật trên Postgres thật, đọc `exp_reward` thẳng từ bảng này, và suy cấp bằng chính
-- `ExperienceCurve` của server. Đường chơi nó đi là đường TỐI THIỂU: chỉ EXP quest + đúng số quái
-- mà chính các quest bắt hạ, không cày thêm con nào.
--
-- `prerequisite_quest_id` trỏ về `clear_old_archive` (ADR-044, mốc CLAIMED). Tra theo `code` chứ
-- KHÔNG hardcode id — id do `GENERATED ALWAYS AS IDENTITY` cấp (cùng lý do đã ghi ở V28/V30).

-- ---------------------------------------------------------------------------------------------
-- 1. Bảng rơi RỖNG — cỗ máy khảo hạch là TÀI SẢN CỦA TÔNG MÔN, nên nó KHÔNG rơi đồ.
--
--    Loài đầu tiên của dự án cố ý không rơi gì. Mọi loài khác rơi đồ vì chúng là đồ hoang hoặc kẻ
--    cướp; cỗ máy này đứng trong sân khảo hạch của Thanh Hư Tông, nhặt đồ của nó là nhặt đồ của
--    tông môn. Nó cũng giữ cho bất biến V21 §4 ("đồ RƠI ĐƯỢC thì bán được") không phải gánh dòng
--    nào mới.
--
--    ⚠️ Vì sao vẫn phải chèn MỘT dòng `drop_table` rỗng thay vì bỏ trắng `drop_table_id`: đọc code
--    trước khi viết SQL. Đường chạy thật là `ConnectedCharacterRegistry.onMonsterKilled` →
--    `InventoryFacade.grantLoot(characterId, MÃ QUÁI)` — nó tra `drop_table.code` bằng MÃ LOÀI,
--    KHÔNG đi qua khoá ngoại `monster_template.drop_table_id` (cột đó hiện chỉ có test đọc). Và
--    `PostgresDropTableAdapter.warnOnceIfCodeMissing` phân biệt đúng hai trường hợp: bảng CÓ mà
--    rỗng ⇒ INFO "cố ý không rơi gì"; bảng KHÔNG CÓ ⇒ WARN "chắc gõ sai mã". Bỏ trắng sẽ đẩy một
--    thiết kế cố ý vào đúng cái cảnh báo dành cho lỗi gõ sai.
--
--    Khoá ngoại vẫn trỏ vào bảng rỗng đó cho khớp ba loài kia — dữ liệu nói cùng một câu ở cả hai
--    đường, dù hôm nay mới một đường được đọc.
-- ---------------------------------------------------------------------------------------------
INSERT INTO drop_table (code) VALUES ('trial_construct');

-- ---------------------------------------------------------------------------------------------
-- 2. Loài mới `trial_construct` — "Trắc Linh Thạch Nhân".
--
--    Lore (STORY.md §7, thêm dòng cùng đợt này): CÙNG DÒNG máy móc hộ mạch của Linh Triều với
--    `stone_ogre`/`archive_warden`, nhưng đây là cỗ được tông môn NHẶT VỀ VÀ SỬA LẠI để làm bạn
--    đánh trong kỳ khảo hạch. Nó đo linh khí đệ tử duy trì được, nên nó ĐÁNH NHẸ mà RẤT DAI —
--    ngược hẳn hai con kia.
--
--    ⚠️ CHỈ SỐ CHỌN TỪ SỐ ĐO, KHÔNG TỪ CẢM GIÁC. Khác V30 (tính từ công thức trên máu ĐẦY), lần
--    này có thêm một số đo thật của đường đi, lấy bằng `gameProbe` ngày 2026-09-06:
--
--      đi bộ x=0 → x=-47 tốn ĐÚNG 40 máu (mỗi thạch vệ một nhát × 20) ⇒ tới nơi còn 78/118.
--
--    Đó là con số đầu vào của bài toán, không phải 118. Bài học của V32 là "đường đi cũng là cân
--    bằng"; V33 không lặp lại lỗi đó bằng cách BỎ QUA đường đi, mà bằng cách TÍNH nó vào.
--
--      Nhân vật nền 10/10/10/10 + trọn bộ "Tạp Dịch" ⇒ ATK 37 · DEF 27 · HP 118
--      với `hit = round(atk × 100 / (100 + def))`, cả hai bên chung nhịp 1 đòn/giây:
--
--        | Tình huống                          | người chơi cần | cỗ máy cần | biên |
--        |-------------------------------------|----------------|------------|------|
--        | máu đầy 118 (đánh ngay tại chỗ)     | 4 nhát         | 10 nhát    | +6   |
--        | tới nơi với 78 (đường chơi THẬT)    | 4 nhát         | 7 nhát     | +3   |
--        | tay không, tới nơi với 56           | 5 nhát         | 5 nhát     | hoà  |
--
--    ⇒ Trận này CỐ Ý KHÔNG sát nút. Quest 3 để lại đúng một lời cảnh báo đã ghi vào ROADMAP mục
--      64: người chơi trúng 91%, hụt hai nhát là thua. Nhịp ĐÓNG của arc không nên là một lần tung
--      đồng xu, nên biên ở đây là 3 nhát — hụt hai nhát vẫn thắng. Cái khó của quest 4 nằm ở
--      QUÃNG ĐƯỜNG (40 máu, xem §3), không nằm ở trận đấu.
--    ⇒ Tay không vẫn hoà = chết, đúng ranh giới V30 đã đặt cho quest 3: bộ "Tạp Dịch" 700 vàng là
--      điều kiện cần của nửa sau arc 1.
--
--    Vì sao ATK chỉ 15 — THẤP HƠN cả tán tu (22) trong khi máu 130 là CAO NHẤT arc 1: cỗ máy này
--    đo chứ không giết. `hit_percent = 85` cũng là thấp nhất trong bốn loài (90/92/93/95) vì cùng
--    lý do — nó nương tay. Bài kiểm tra là "ngươi trụ được bao lâu", đúng luận đề của Chu Hoành
--    (STORY.md §5: linh căn quyết định đi NHANH tới đâu, không quyết định đi ĐƯỢC tới đâu).
--
--    ⚠️ MODEL DÙNG CHUNG với `archive_warden` (`mob_archive-warden`) — NỢ NHÌN THẤY ĐƯỢC, khai
--    thẳng ra y như V23 §3 và V30 §2 đã làm. Ở đây nó có lý do tường thuật thật (cùng một dòng máy
--    móc, và tông môn thì sửa lại đúng cái họ nhặt được), nhưng người chơi VẪN thấy hai con giống
--    hệt nhau cho tới khi có `.glb` riêng. Cột `model_key` tồn tại chính để trả món nợ đó bằng một
--    câu UPDATE (V24, V31 đều đã làm đúng vậy).
--
--    ✅ ĐÃ TRẢ NGAY TRONG NGÀY: `mob_trial-construct` dựng xong và **V34** lật `model_key` sang nó
--    (ASSET-LOG mục 2026-09-06 tối). Dòng cảnh báo trên giữ nguyên làm hồ sơ — nó là lần thứ BA dự
--    án đi hết vòng "thêm loài → mượn model → ghi nợ → trả nợ", và từ V34 có hàng rào CI chung
--    (`shouldNotLetAnyTwoSpeciesShareAModelKey`) để lần thứ tư không phải trông vào trí nhớ ai cả.
--
--    ⚠️ THAM SỐ AI GIỮ NGUYÊN MẶC ĐỊNH (aggro 6 / leash 10 / đuổi 3 / tuần tra 1,5 / bán kính 3 /
--    idle 3) — cùng lý do V30 §2 đã ghi: đặt số AI chưa playtest cùng lúc với việc thêm nội dung
--    là đúng cái bẫy V25 §4 tránh.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_template
    (code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res, hit_percent, exp_reward, drop_table_id)
SELECT 'trial_construct', 'hud.monster.trial_construct', 'mob_archive-warden', 8, 130, 15, 0, 10, 10, 85, 80, dt.id
  FROM drop_table dt WHERE dt.code = 'trial_construct';

-- ---------------------------------------------------------------------------------------------
-- 3. MỘT điểm spawn — sân khảo hạch ở x=-47, phía TÂY, sau lưng Tàng Thư Đường cũ.
--
--    MỘT chứ không phải hai: khảo hạch là MỘT trận. Và đó cũng là quyết định cân bằng, vì
--    server-world KHÔNG có hồi máu ngoài chiến đấu — đánh hai trận liên tiếp ở arc 1 là chết
--    (quest 3 đang mắc đúng chuyện đó: hạ con thạch vệ thứ nhất xong còn 38/118 mà vẫn cần con thứ
--    hai). Lỗ hổng hệ thống đó đã báo cáo riêng cho chủ dự án; quest 4 KHÔNG giả vờ là nó không tồn
--    tại, nó chỉ không bước vào.
--
--    LUẬT HÌNH HỌC — cả HAI luật đều kiểm, cả hai đều có test duyệt mọi cặp đọc thẳng từ DB
--    (`PostgresMonsterCatalogAdapterIntegrationTest`):
--      · V29 (đi ngang, theo tầm AGGRO): |A-B| > aggroA + aggroB = 12
--      · V32 (đứng lại đánh, theo tầm ĐUỔI): |A-B| > leash của con bên cạnh + tầm đánh 2 = 12
--    Hàng xóm gần nhất là thạch vệ @-33: |-47 - (-33)| = 14 > 12 ✓ (thoả cả hai). Con còn lại
--    @-20 thì 27 ✓. Phía tây -47 không còn gì.
--
--    Bố cục trục chơi sau V33:
--
--      sân khảo hạch -47 · thạch vệ -33 · thạch vệ -20 · [Chu Hoành -5] · [người chơi 0]
--                        · mộc nhân 8 · mộc nhân 14 · tán tu 21 · tán tu 34 · quỷ đá 47
--
--    ⚠️ QUÃNG ĐƯỜNG LÀ MỘT PHẦN CỦA BÀI THI, và điều đó được nói thẳng trong lời thoại
--    (`dialogue.town_elder.awakening_trial`) chứ không giấu sau lưng người chơi: đi tới sân khảo
--    hạch phải lách qua hai cỗ thạch vệ, tốn 40 máu — số ĐO ĐƯỢC, không phải số ước lượng. Đây là
--    khác biệt so với V30: hồi đó đường đi tốn 65 máu mà không ai biết cho tới lúc playtest.
-- ---------------------------------------------------------------------------------------------
INSERT INTO monster_spawn (monster_template_id, world_code, pos_x, pos_y, respawn_seconds)
SELECT mt.id, 'main', -47, 0, 20 FROM monster_template mt WHERE mt.code = 'trial_construct';

-- ---------------------------------------------------------------------------------------------
-- 4. Quest 4. Nội dung (số lần hạ, phần thưởng) nằm HẾT trong script (Điều 27); bảng này chỉ giữ
--    định danh + đường dẫn script + điều kiện MỞ.
--
--    KHÔNG có phần thưởng vật phẩm, và đó là chủ đích: phần thưởng của khảo hạch là chính cái mốc
--    Cấp 10 mà nó đưa người chơi tới — cửa sổ chọn hệ phái bật ra ngay tại đó
--    (`FactionController.MIN_LEVEL`, client tự mở khi `ExperienceChangedEvent` báo cấp mới).
-- ---------------------------------------------------------------------------------------------
INSERT INTO quest_template (code, type, script_ref, prerequisite_quest_id)
SELECT 'awakening_trial',
       'MAIN',
       '/scripts/quest/awakening_trial.js',
       (SELECT id FROM quest_template WHERE code = 'clear_old_archive')
 WHERE NOT EXISTS (SELECT 1 FROM quest_template WHERE code = 'awakening_trial');
