package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;

/**
 * Bất biến phụ thuộc vào CHÍNH gói vừa nhận (khác {@link DisplayInvariants} — đúng với mọi gói):
 *
 * <ul>
 *   <li>despawn/đổi World rồi thì thực thể đó không còn trong presenter/HUD, không còn là mục tiêu;
 *   <li>gói không đọc được ⇒ KHÔNG gửi lệnh nào; gói đọc được ⇒ chỉ lệnh TRA CỨU (mẫu "settle →
 *       hỏi lại" CÓ CHỦ ĐÍCH của mọi coordinator, tối đa {@link #MAX_REPLIES_PER_PACKET}) và tự nhắm
 *       có chủ đích ({@link #MAY_SET_TARGET}) vào một mục tiêu đang thật sự hiện.
 * </ul>
 */
final class PacketInvariants {

    /**
     * Trần số lệnh MỘT gói đến được kéo theo. Ba là con số của mẫu "settle → hỏi lại" lớn nhất
     * ({@code GuildCoordinator#refresh}, {@code PartyCoordinator}): kết quả một lệnh → hỏi lại ba
     * danh sách. Vượt trần nghĩa là một gói đang khuếch đại thành bão lệnh.
     */
    static final int MAX_REPLIES_PER_PACKET = 3;

    /**
     * Gói nào được kéo theo {@code SetTargetCommand} — lệnh DUY NHẤT không phải tra cứu mà một gói
     * đến được phép gây ra: tự nhắm con quái đầu tiên vào tầm nhìn khi đang không nhắm gì
     * ({@link CombatHudController#onMonsterSpawned}), tự nhắm đối thủ đấu trường khi họ vào tầm nhìn
     * hoặc khi trận bắt đầu ({@link DuelTargeting}).
     */
    static final Set<Opcode> MAY_SET_TARGET = Set.of(
            Opcode.OPCODE_MONSTER_SPAWNED_EVENT,
            Opcode.OPCODE_CHARACTER_SPAWNED_EVENT,
            Opcode.OPCODE_DUEL_STARTED_EVENT);

    private PacketInvariants() {}

    /** Mọi bất biến sau khi {@code rig} vừa xử lý gói {@code opcode}/{@code payload}. */
    static List<String> check(FuzzRig rig, Opcode opcode, byte[] payload) {
        List<String> found = new ArrayList<>(rig.stateViolations());
        DynamicMessage parsed = parse(ServerOpcodes.DESCRIPTORS.get(opcode), payload);
        if (parsed != null) {
            checkRemoved(rig, opcode, parsed, found);
        }
        checkSends(rig, opcode, parsed, found);
        return found;
    }

    private static void checkRemoved(FuzzRig rig, Opcode opcode, DynamicMessage message, List<String> found) {
        switch (opcode) {
            case OPCODE_CHARACTER_DESPAWNED_EVENT -> checkCharacterGone(rig, longField(message, "character_id"), found);
            case OPCODE_MONSTER_DESPAWNED_EVENT -> checkMonsterGone(rig, longField(message, "monster_id"), found);
            case OPCODE_NPC_DESPAWNED_EVENT -> {
                if (rig.npcs.npcs().containsKey(longField(message, "npc_id"))) {
                    found.add("NPC đã despawn vẫn còn");
                }
            }
            case OPCODE_WORLD_CHANGED_EVENT -> checkWorldEmptied(rig, found);
            default -> {
                // Opcode khác không gỡ thực thể nào.
            }
        }
    }

    private static void checkCharacterGone(FuzzRig rig, long id, List<String> found) {
        if (rig.characters.characters().containsKey(id) || rig.characters.isPresent(id)) {
            found.add("nhân vật đã despawn vẫn còn trong presenter");
        }
        if (id > 0L && rig.hud.currentTargetId() == id) {
            found.add("nhân vật đã despawn vẫn là mục tiêu");
        }
    }

    private static void checkMonsterGone(FuzzRig rig, long id, List<String> found) {
        if (rig.hud.monsters().containsKey(id)) {
            found.add("quái đã despawn vẫn còn trong HUD");
        }
        if (id != 0L && rig.hud.currentTargetId() == id) {
            found.add("quái đã despawn vẫn là mục tiêu");
        }
    }

    private static void checkWorldEmptied(FuzzRig rig, List<String> found) {
        if (!rig.characters.characters().isEmpty()
                || !rig.npcs.npcs().isEmpty()
                || !rig.hud.monsters().isEmpty()) {
            found.add("đổi World mà thực thể World cũ còn lại");
        }
    }

    private static void checkSends(FuzzRig rig, Opcode opcode, DynamicMessage parsed, List<String> found) {
        List<FuzzRig.Sent> sent = rig.link.sent;
        if (sent.isEmpty()) {
            return;
        }
        if (parsed == null) {
            found.add("gói KHÔNG đọc được mà vẫn gửi lệnh " + opcodeNames(sent));
            return;
        }
        if (sent.size() > MAX_REPLIES_PER_PACKET) {
            found.add("một gói kéo theo " + sent.size() + " lệnh " + opcodeNames(sent));
        }
        for (FuzzRig.Sent command : sent) {
            Opcode sentOpcode = Opcode.forNumber(command.opcode());
            if (sentOpcode == Opcode.OPCODE_SET_TARGET_COMMAND) {
                checkSetTarget(rig, opcode, command.payload(), found);
            } else if (sentOpcode == null || !ServerOpcodes.isQuery(sentOpcode)) {
                found.add("nhận " + opcode + " mà gửi lệnh ghi " + sentOpcode);
            }
        }
    }

    private static void checkSetTarget(FuzzRig rig, Opcode cause, ByteString payload, List<String> found) {
        if (!MAY_SET_TARGET.contains(cause)) {
            found.add("nhận " + cause + " mà gửi SetTargetCommand");
            return;
        }
        long targetId;
        try {
            targetId = SetTargetCommand.parseFrom(payload).getTargetEntityId();
        } catch (InvalidProtocolBufferException e) {
            found.add("SetTargetCommand không đọc lại được: " + e.getMessage());
            return;
        }
        boolean present = rig.hud.monsters().containsKey(targetId) || rig.characters.isPresent(targetId);
        if (targetId == 0L || targetId == PoisonMessageFactory.LOCAL_ID || !present) {
            found.add("tự nhắm một mục tiêu không tồn tại/không hợp lệ");
        }
    }

    private static String opcodeNames(List<FuzzRig.Sent> sent) {
        List<String> names = new ArrayList<>();
        for (FuzzRig.Sent command : sent) {
            Opcode opcode = Opcode.forNumber(command.opcode());
            names.add(opcode == null ? Integer.toString(command.opcode()) : opcode.name());
        }
        return names.toString();
    }

    /** {@code null} khi payload không phải message hợp lệ của kiểu đó. */
    private static DynamicMessage parse(Descriptor descriptor, byte[] payload) {
        try {
            return DynamicMessage.parseFrom(descriptor, payload);
        } catch (InvalidProtocolBufferException e) {
            return null; // rác — chính là ca cần kiểm "không gửi lệnh nào".
        }
    }

    private static long longField(DynamicMessage message, String name) {
        FieldDescriptor field = message.getDescriptorForType().findFieldByName(name);
        return field == null ? 0L : (Long) message.getField(field);
    }
}
