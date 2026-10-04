package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.math.Matrix4;

import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Bất biến TRẠNG THÁI HIỂN THỊ — đúng sau mọi gói, bất kể gói gì: không NaN/vô cực ở vị trí và
 * camera, máu/Khí trong {@code [0, max]} khi đã biết max, số entity ECS khớp số người chơi khác đang
 * hiện, và chính mình không bao giờ là "người khác".
 */
final class DisplayInvariants {

    private DisplayInvariants() {}

    /** Rỗng nghĩa là sạch; mỗi phần tử là một vi phạm đọc được. */
    static List<String> check(FuzzRig rig) {
        List<String> found = new ArrayList<>();
        checkLocal(rig, found);
        checkPlayerHud(rig.hud, found);
        checkCharacterTarget(rig.hud, found);
        checkCharacters(rig, found);
        checkMonsters(rig.hud, found);
        checkNpcs(rig, found);
        return found;
    }

    private static void checkLocal(FuzzRig rig, List<String> found) {
        PositionComponent local = rig.localPosition;
        if (!finite(local.x, local.y, local.z) || !finite(local.prevX, local.prevY, local.prevZ)) {
            found.add("vị trí của mình không hữu hạn");
        }
        if (!finite(rig.camera.combined)) {
            found.add("camera không hữu hạn");
        }
    }

    private static void checkPlayerHud(CombatHudController hud, List<String> found) {
        if (hud.playerMaxHp() > 0 && outside(hud.playerHp(), hud.playerMaxHp())) {
            found.add("máu của mình ngoài [0, max]");
        }
        if (outside(hud.playerChi(), hud.playerMaxChi())) {
            found.add("Khí của mình ngoài [0, max]");
        }
        float cooldown = hud.skillCooldownRemaining();
        if (!Float.isFinite(cooldown) || cooldown < 0f) {
            found.add("hồi chiêu không hợp lệ");
        }
    }

    private static void checkCharacterTarget(CombatHudController hud, List<String> found) {
        if (!hud.hasCharacterTarget()) {
            return;
        }
        int max = hud.characterTargetMaxHp();
        int hp = hud.characterTargetHp();
        if (max > 0 && hp != CombatHudController.UNKNOWN_HP && outside(hp, max)) {
            found.add("máu mục tiêu nhân vật ngoài [0, max]");
        }
    }

    private static void checkCharacters(FuzzRig rig, List<String> found) {
        Map<Long, ? extends PickablePoint> characters = rig.characters.characters();
        for (Map.Entry<Long, ? extends PickablePoint> entry : characters.entrySet()) {
            if (!finite(entry.getValue().x(), entry.getValue().y(), 0f)) {
                found.add("vị trí nhân vật khác không hữu hạn");
            }
            if (entry.getKey() == PoisonMessageFactory.LOCAL_ID) {
                found.add("chính mình bị dựng thành 'nhân vật khác'");
            }
        }
        if (rig.world.entityCount() != 1 + characters.size()) {
            found.add("rò entity ECS: " + rig.world.entityCount() + " entity cho " + characters.size() + " người khác");
        }
    }

    private static void checkMonsters(CombatHudController hud, List<String> found) {
        for (CombatHudController.MonsterState monster : hud.monsters().values()) {
            if (!finite(monster.x(), monster.y(), 0f)) {
                found.add("vị trí quái không hữu hạn");
            }
            if (monster.maxHp() > 0 && outside(monster.hp(), monster.maxHp())) {
                found.add("máu quái ngoài [0, max]");
            }
        }
    }

    private static void checkNpcs(FuzzRig rig, List<String> found) {
        for (NpcPresenter.NpcState npc : rig.npcs.npcs().values()) {
            if (!finite(npc.x(), npc.y(), 0f)) {
                found.add("vị trí NPC không hữu hạn");
            }
        }
    }

    private static boolean outside(int value, int max) {
        return value < 0 || value > max;
    }

    private static boolean finite(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }

    private static boolean finite(Matrix4 matrix) {
        for (float value : matrix.val) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
