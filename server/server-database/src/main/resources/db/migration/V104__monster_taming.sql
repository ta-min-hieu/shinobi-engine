-- V104 — thu phục yêu thú (ADR-124, lô 23). Quái ≤ 20% máu + một lá Phong Yêu Phù ⇒ server tung tỉ lệ (gốc theo loài,
-- trừ 5% mỗi cấp quái hơn người, sàn 5%); thành công thì quái biến mất và người dùng phù nhận một linh thú (ô PET,
-- ADR-072). Chỉ loài có trong `tameable_monster` mới thu phục được — boss thì không. Mọi con số là ĐỀ XUẤT CÂN BẰNG.
INSERT INTO item_template (code, name_key, max_stack)
SELECT 'seal_demon_talisman', 'item.seal_demon_talisman.name', 99
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'seal_demon_talisman');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 300, 30 FROM item_template WHERE code = 'seal_demon_talisman'
ON CONFLICT DO NOTHING;

-- Hai linh thú mới — chỉ số gốc cấp 1 cùng tầm Hồ Ly / Huyền Quy (V71), cùng cấp yêu cầu 20 của ô PET.
INSERT INTO item_template (code, name_key, max_stack, equip_slot, required_level,
                           bonus_atk, bonus_def, bonus_res, bonus_hp)
SELECT v.code, v.name_key, 1, 'PET', 20, v.atk, v.def, v.res, v.hp
  FROM (VALUES ('stone_spirit_pet',   'item.stone_spirit_pet.name',   0, 3, 2, 15),
               ('wasteland_wolf_pet', 'item.wasteland_wolf_pet.name', 4, 0, 0, 12)) AS v (code, name_key, atk, def, res, hp)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = v.code);

CREATE TABLE tameable_monster (
    monster_template_id  BIGINT PRIMARY KEY REFERENCES monster_template (id),
    pet_item_template_id BIGINT NOT NULL REFERENCES item_template (id),
    base_chance_percent  INT    NOT NULL CHECK (base_chance_percent BETWEEN 1 AND 100)
);

INSERT INTO tameable_monster (monster_template_id, pet_item_template_id, base_chance_percent)
SELECT mt.id, it.id, v.chance
  FROM (VALUES ('stone_ogre',      'stone_spirit_pet',   40),
               ('wasteland_beast', 'wasteland_wolf_pet', 30)) AS v (monster_code, pet_code, chance)
  JOIN monster_template mt ON mt.code = v.monster_code
  JOIN item_template it ON it.code = v.pet_code
ON CONFLICT DO NOTHING;
