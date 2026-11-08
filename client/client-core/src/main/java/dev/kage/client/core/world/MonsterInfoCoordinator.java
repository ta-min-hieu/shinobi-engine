package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetMonsterInfoRequest;
import dev.kage.protocol.GetMonsterInfoResponse;
import dev.kage.protocol.MonsterDropView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Xem thông tin quái (ADR-255) — {@code /xemquai} (hoặc {@code /monster}) xem con quái đang nhắm; {@code /xemquai <mã>}
 * xem theo mã. In cấp, máu, công/thủ, EXP và các món có thể rơi kèm tỉ lệ.
 */
final class MonsterInfoCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/xemquai";
    private static final String COMMAND_EN = "/monster";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator catalog;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<String>> targetCode = Optional::empty;

    MonsterInfoCoordinator(WorldCommandSender sender, ChatCoordinator chat, ItemCatalogCoordinator catalog) {
        this.sender = sender;
        this.chat = chat;
        this.catalog = catalog;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối mã quái đang nhắm (HUD chiến đấu). */
    void useTarget(CombatHudController combat) {
        this.targetCode = () -> Optional.ofNullable(combat.monsters().get(combat.currentTargetId()))
                .map(CombatHudController.MonsterState::code);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<String> code = words.length > 1 ? Optional.of(words[1].strip()) : targetCode.get();
        if (code.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "monsterinfo.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_MONSTER_INFO_REQUEST_VALUE,
                GetMonsterInfoRequest.newBuilder().setMonsterCode(code.get()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_MONSTER_INFO_RESPONSE_VALUE) {
            return false;
        }
        GetMonsterInfoResponse info = GetMonsterInfoResponse.parseFrom(frame.payload());
        if (!info.getFound()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "monsterinfo.not_found", info.getMonsterCode()));
            return true;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                info.getBoss() ? "monsterinfo.header.boss" : "monsterinfo.header",
                KageI18n.textOrKey(i18n, info.getNameKey()),
                info.getLevel(),
                info.getMaxHp(),
                info.getAtk(),
                info.getDef(),
                info.getExpReward()));
        if (info.getDropsCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "monsterinfo.no_drops"));
        }
        for (MonsterDropView drop : info.getDropsList()) {
            String name = catalog.nameKey(drop.getItemTemplateId())
                    .map(key -> KageI18n.textOrKey(i18n, key))
                    .orElse("#" + drop.getItemTemplateId());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "monsterinfo.drop",
                    name,
                    drop.getChancePercent(),
                    drop.getMinQuantity(),
                    drop.getMaxQuantity()));
        }
        return true;
    }
}
