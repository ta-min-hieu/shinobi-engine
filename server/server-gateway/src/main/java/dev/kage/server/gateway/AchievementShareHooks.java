package dev.kage.server.gateway;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.achievement.api.AchievementShareFacade;
import dev.kage.achievement.domain.Achievement;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.guild.api.GuildFacade;
import dev.kage.protocol.GuildAchievementSharedEvent;
import dev.kage.protocol.Opcode;

/**
 * Khoe thành tựu vào bang (ADR-230): mở thành tựu ⇒ mọi thành viên bang đang online (kể cả mình) nhận một dòng
 * {@code GuildAchievementSharedEvent}; client tự dịch tên thành tựu. Người tắt công tắc / không ở bang ⇒ không gửi gì.
 * Việc phụ chạy trên Virtual Thread của đường đếm thành tựu — lỗi dừng ở đây.
 */
final class AchievementShareHooks {

    private static final Logger LOG = LoggerFactory.getLogger(AchievementShareHooks.class);

    /** Nguồn dữ liệu. */
    record Deps(
            AchievementShareFacade share,
            GuildFacade guilds,
            CharacterFacade characters,
            ConnectedCharacterRegistry registry) {}

    private final Deps deps;

    AchievementShareHooks(Deps deps) {
        this.deps = deps;
    }

    // PMD AvoidCatchingGenericException: việc phụ — không được làm hỏng đường đếm thành tựu.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onUnlocked(long characterId, List<Achievement> opened) {
        try {
            if (opened.isEmpty() || !deps.share().enabled(characterId)) {
                return;
            }
            List<GuildFacade.MemberView> members = deps.guilds().listMembers(characterId);
            if (members.isEmpty()) {
                return;
            }
            String name =
                    deps.characters().findById(characterId).map(Character::name).orElse("");
            for (Achievement achievement : opened) {
                byte[] event = GuildAchievementSharedEvent.newBuilder()
                        .setCharacterName(name)
                        .setAchievementNameKey(achievement.nameKey())
                        .build()
                        .toByteArray();
                for (GuildFacade.MemberView member : members) {
                    deps.registry()
                            .sendToCharacterIfOnline(
                                    member.characterId(), Opcode.OPCODE_GUILD_ACHIEVEMENT_SHARED_EVENT_VALUE, 0, event);
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Khoe thành tựu vào bang lỗi: characterId={}", characterId, e);
        }
    }
}
