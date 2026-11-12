package dev.kage.client.core.world;

import java.util.Locale;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyMemberView;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bảng sát thương nhóm (ADR-251) — cộng sát thương của chính mình và thành viên nhóm từ gói kết quả đòn đánh / kỹ năng
 * client đã nhận; {@value #FIGHT_GAP_MILLIS} ms không có đòn nào ⇒ đòn kế tiếp mở trận mới. {@code /satthuong} (hoặc
 * {@code /dps}) in bảng; {@code /satthuong xoa} xoá. Mảng cố định, không cấp phát theo đòn.
 */
final class PartyDamageMeterCoordinator implements WindowFlow {

    static final long FIGHT_GAP_MILLIS = 30_000L;
    static final int MAX_SLOTS = 8;
    private static final long PERCENT = 100L;

    private final ChatCoordinator chat;
    private final LongSupplier clockMillis;
    private final I18NBundle i18n = KageI18n.load();
    private final long[] memberIds = new long[MAX_SLOTS];
    private final String[] memberNames = new String[MAX_SLOTS];
    private final long[] totals = new long[MAX_SLOTS];
    private int memberCount;
    private long localId;
    private long lastHitMillis = Long.MIN_VALUE / 2;

    PartyDamageMeterCoordinator(ChatCoordinator chat, LongSupplier clockMillis) {
        this.chat = chat;
        this.clockMillis = clockMillis;
        chat.addSlashCommands(this::onSlash);
    }

    /** Id nhân vật mình (lúc vào world). */
    void useLocalId(long characterId) {
        this.localId = characterId;
    }

    /** Ghi một đòn trúng; tính nếu người ra đòn là mình hoặc thành viên nhóm. */
    void onHit(long attackerId, long targetId, int damage) {
        if (damage <= 0 || attackerId == targetId) {
            return;
        }
        int slot = slotOf(attackerId);
        if (slot < 0) {
            return;
        }
        long now = clockMillis.getAsLong();
        if (now - lastHitMillis > FIGHT_GAP_MILLIS) {
            java.util.Arrays.fill(totals, 0L);
        }
        lastHitMillis = now;
        totals[slot] += damage;
    }

    /** ADR-285 — {@code characterId} là mình hoặc thành viên nhóm đang theo dõi. */
    boolean isTracked(long characterId) {
        return slotOf(characterId) >= 0;
    }

    /** Tổng sát thương hiện tại của {@code characterId} (0 nếu không theo dõi). */
    long totalOf(long characterId) {
        int slot = slotOf(characterId);
        return slot < 0 ? 0L : totals[slot];
    }

    private int slotOf(long characterId) {
        if (characterId == localId && localId != 0L) {
            return MAX_SLOTS - 1;
        }
        for (int i = 0; i < memberCount; i++) {
            if (memberIds[i] == characterId) {
                return i;
            }
        }
        return -1;
    }

    /** Thành viên nhóm mới (từ gói danh sách thành viên); tổng của người còn ở lại giữ nguyên. */
    void onMembers(ListPartyMembersResponse response) {
        long[] oldIds = memberIds.clone();
        long[] oldTotals = totals.clone();
        int oldCount = memberCount;
        memberCount = 0;
        for (PartyMemberView member : response.getMembersList()) {
            if (member.getCharacterId() == localId || memberCount >= MAX_SLOTS - 1) {
                continue;
            }
            memberIds[memberCount] = member.getCharacterId();
            memberNames[memberCount] = member.getName();
            totals[memberCount] = carried(oldIds, oldTotals, oldCount, member.getCharacterId());
            memberCount++;
        }
    }

    private static long carried(long[] ids, long[] values, int count, long characterId) {
        for (int i = 0; i < count; i++) {
            if (ids[i] == characterId) {
                return values[i];
            }
        }
        return 0L;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/satthuong".equals(command) && !"/dps".equals(command)) {
            return false;
        }
        if (words.length > 1
                && ("xoa".equalsIgnoreCase(words[1].strip()) || "reset".equalsIgnoreCase(words[1].strip()))) {
            java.util.Arrays.fill(totals, 0L);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "dps.cleared"));
            return true;
        }
        show();
        return true;
    }

    private void show() {
        long sum = 0L;
        for (long total : totals) {
            sum += total;
        }
        if (sum == 0L) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "dps.empty"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "dps.header"));
        boolean[] shown = new boolean[MAX_SLOTS];
        for (int row = 0; row < MAX_SLOTS; row++) {
            int best = bestRemaining(shown);
            if (best < 0) {
                return;
            }
            shown[best] = true;
            String name = best == MAX_SLOTS - 1 ? KageI18n.textOrKey(i18n, "dps.you") : memberNames[best];
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "dps.line", name, totals[best], totals[best] * PERCENT / sum));
        }
    }

    /** Ô có tổng lớn nhất chưa in; -1 nếu hết. */
    private int bestRemaining(boolean[] shown) {
        int best = -1;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!shown[i] && totals[i] > 0 && (best < 0 || totals[i] > totals[best])) {
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            AttackResultEvent event = AttackResultEvent.parseFrom(frame.payload());
            if (!event.getIsMiss()) {
                onHit(event.getAttackerEntityId(), event.getTargetEntityId(), event.getDamage());
            }
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            SkillResultEvent event = SkillResultEvent.parseFrom(frame.payload());
            if (!event.getIsMiss()) {
                onHit(event.getAttackerEntityId(), event.getTargetEntityId(), event.getDamage());
            }
        } else if (opcode == Opcode.OPCODE_LIST_PARTY_MEMBERS_RESPONSE_VALUE) {
            onMembers(ListPartyMembersResponse.parseFrom(frame.payload()));
        }
        return false;
    }
}
