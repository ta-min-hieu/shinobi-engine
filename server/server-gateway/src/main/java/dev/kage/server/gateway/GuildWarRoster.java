package dev.kage.server.gateway;

import java.util.Arrays;
import java.util.List;

import dev.kage.guild.domain.GuildWar;
import dev.kage.guild.domain.GuildWarPort;
import dev.kage.server.world.PlayerVersusPlayerRule;

/**
 * Ảnh chụp RAM "nhân vật → bang đang chiến" và "bang nào đang chiến với bang nào" (ADR-102 D2) — luồng nền
 * {@link GuildWarScheduler} dựng lại mỗi {@code ROSTER_PERIOD_SECONDS} giây, tick thread của world chính chỉ ĐỌC.
 *
 * <p>Mảng nguyên thuỷ đã sắp xếp + tìm nhị phân thay cho {@code Map<Long, Long>}: {@link #allows} chạy trên tick
 * thread mỗi tick cho mỗi người đang nhắm một người chơi khác, nên không được boxing hay cấp phát. Vùng an toàn KHÔNG
 * xét ở đây — {@code WorldFacade} tự kiểm (nó mới biết ai đứng trong vùng nào).
 */
final class GuildWarRoster implements PlayerVersusPlayerRule {

    /** Một ảnh chụp bất biến — mảng không bao giờ sửa sau khi dựng. */
    private static final class Snapshot {
        // id nhân vật đang chiến, tăng dần; guildIds[i] là bang của characterIds[i].
        private final long[] characterIds;
        private final long[] guildIds;
        // Từng cặp bang đang chiến: firstGuilds[i] với secondGuilds[i].
        private final long[] firstGuilds;
        private final long[] secondGuilds;

        Snapshot(long[] characterIds, long[] guildIds, long[] firstGuilds, long[] secondGuilds) {
            this.characterIds = characterIds;
            this.guildIds = guildIds;
            this.firstGuilds = firstGuilds;
            this.secondGuilds = secondGuilds;
        }
    }

    private static final long NO_GUILD = 0L;
    private static final Snapshot EMPTY = new Snapshot(new long[0], new long[0], new long[0], new long[0]);

    private volatile Snapshot snapshot = EMPTY;

    /** Thay ảnh chụp — chỉ luồng nền gọi. */
    void replace(List<GuildWarPort.Combatant> combatants, List<GuildWar> activeWars) {
        GuildWarPort.Combatant[] sorted = combatants.toArray(new GuildWarPort.Combatant[0]);
        Arrays.sort(sorted, (a, b) -> Long.compare(a.characterId(), b.characterId()));
        long[] characterIds = new long[sorted.length];
        long[] guildIds = new long[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            characterIds[i] = sorted[i].characterId();
            guildIds[i] = sorted[i].guildId();
        }
        long[] firstGuilds = new long[activeWars.size()];
        long[] secondGuilds = new long[activeWars.size()];
        for (int i = 0; i < activeWars.size(); i++) {
            firstGuilds[i] = activeWars.get(i).attacker().guildId();
            secondGuilds[i] = activeWars.get(i).defender().guildId();
        }
        snapshot = new Snapshot(characterIds, guildIds, firstGuilds, secondGuilds);
    }

    /** Nhân vật đang thuộc một bang trong giờ chiến (theo ảnh chụp gần nhất). */
    boolean isAtWar(long characterId) {
        Snapshot current = snapshot;
        return guildOf(current, characterId) != NO_GUILD;
    }

    @Override
    public boolean allows(long attackerCharacterId, long targetCharacterId) {
        Snapshot current = snapshot;
        if (current.firstGuilds.length == 0) {
            return false;
        }
        long attackerGuild = guildOf(current, attackerCharacterId);
        long targetGuild = guildOf(current, targetCharacterId);
        if (attackerGuild == NO_GUILD || targetGuild == NO_GUILD || attackerGuild == targetGuild) {
            return false;
        }
        return atWarWithEachOther(current, attackerGuild, targetGuild);
    }

    private static boolean atWarWithEachOther(Snapshot current, long attackerGuild, long targetGuild) {
        for (int i = 0; i < current.firstGuilds.length; i++) {
            long first = current.firstGuilds[i];
            long second = current.secondGuilds[i];
            if ((first == attackerGuild && second == targetGuild)
                    || (first == targetGuild && second == attackerGuild)) {
                return true;
            }
        }
        return false;
    }

    private static long guildOf(Snapshot current, long characterId) {
        int index = Arrays.binarySearch(current.characterIds, characterId);
        return index >= 0 ? current.guildIds[index] : NO_GUILD;
    }
}
