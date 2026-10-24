package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.api.SeclusionFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.SeclusionPort;
import dev.kage.checkin.api.OnlineRewardFacade;
import dev.kage.checkin.domain.OnlineRewardPolicy;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.DismantlePort;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ClaimOnlineRewardOutcome;
import dev.kage.protocol.ClaimOnlineRewardResponse;
import dev.kage.protocol.ClaimSeclusionOutcome;
import dev.kage.protocol.ClaimSeclusionResponse;
import dev.kage.protocol.DismantleItemOutcome;
import dev.kage.protocol.DismantleItemResponse;
import dev.kage.protocol.GetOnlineRewardsResponse;
import dev.kage.protocol.GetSeclusionStatusResponse;
import dev.kage.protocol.OnlineRewardMilestoneView;

/**
 * Bế quan (ADR-095), thưởng online (ADR-096), phân giải (ADR-097) — và hai móc vào/rời world cho hai tính năng đầu.
 * Chạy trên Virtual Thread (chạm DB).
 */
final class DailyCommands {

    private static final Logger LOG = LoggerFactory.getLogger(DailyCommands.class);
    private static final int MINUTES_PER_HOUR = 60;

    /** Các nguồn dữ liệu — gom để constructor không vượt 4 tham số. */
    record Deps(
            DailyFacades daily,
            CharacterFacade characters,
            InventoryFacade inventory,
            ConnectedCharacterRegistry registry,
            LongPredicate outsideMainWorld,
            AdventureFacades adventure) {}

    private final Deps deps;
    private final PlayerNotices notices;
    private final Clock clock;
    private final HerbCommands herbs;

    DailyCommands(Deps deps, MailFacade mail, Clock clock) {
        this.deps = deps;
        this.notices = new PlayerNotices(mail, deps.registry());
        this.clock = clock;
        this.herbs = new HerbCommands(deps.registry(), deps.inventory(), deps.outsideMainWorld(), clock);
    }

    /** Bản cho MỘT kết nối, nối từ composition của gateway. */
    static DailyCommands of(GatewayFacades facades, WorldGateway worldGateway) {
        return new DailyCommands(
                new Deps(
                        facades.progression().daily(),
                        facades.characterFacade(),
                        facades.inventoryFacade(),
                        worldGateway.registry(),
                        id -> worldGateway.dungeonInstanceRegistry().isInDungeon(id)
                                || worldGateway.arenaInstanceRegistry().isInDuel(id),
                        facades.progression().adventure()),
                facades.contentFacades().socialFacades().mailFacade(),
                Clock.systemUTC());
    }

    /** Hái linh thảo (ADR-098). */
    HerbCommands herbs() {
        return herbs;
    }

    /** Vào world: tích quỹ bế quan (báo nếu có) và bắt đầu đếm giờ online. Việc phụ — lỗi dừng ở đây. */
    // PMD AvoidCatchingGenericException: việc phụ của lúc vào world, không được làm hỏng việc vào world.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onEnteredWorld(long characterId) {
        deps.registry().onlineSessions.enter(characterId, clock.instant());
        try {
            int banked = deps.daily().seclusion().onEnteredWorld(characterId);
            if (banked > 0) {
                notices.systemLine(
                        characterId,
                        "Bế quan được %d giờ %d phút — mở Hằng ngày (E) để nhận. / Secluded %dh %dm — open Daily (E)."
                                .formatted(
                                        banked / MINUTES_PER_HOUR,
                                        banked % MINUTES_PER_HOUR,
                                        banked / MINUTES_PER_HOUR,
                                        banked % MINUTES_PER_HOUR));
            }
        } catch (RuntimeException e) {
            LOG.warn("Tích quỹ bế quan lỗi: characterId={}", characterId, e);
        }
    }

    /** Rời world: ghi mốc thoát (bế quan) và lưu giây online của phiên. Việc phụ — lỗi dừng ở đây. */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onLeftWorld(long characterId) {
        Optional<Instant> entered = deps.registry().onlineSessions.leave(characterId);
        try {
            deps.daily().seclusion().onLeftWorld(characterId);
            deps.adventure().waypoints().forget(characterId);
            entered.ifPresent(at -> deps.daily().onlineRewards().onLeftWorld(characterId, at));
        } catch (RuntimeException e) {
            LOG.warn("Ghi mốc rời world (bế quan/online) lỗi: characterId={}", characterId, e);
        }
    }

    GetSeclusionStatusResponse seclusionStatus(long characterId) {
        int level =
                deps.characters().findById(characterId).map(Character::level).orElse(1);
        SeclusionFacade.Status status = deps.daily().seclusion().status(characterId, level);
        return GetSeclusionStatusResponse.newBuilder()
                .setBankedMinutes(status.bankedMinutes())
                .setExpSingle(status.expSingle())
                .setDoubleCost(status.doubleCost())
                .build();
    }

    /** Nhận quỹ; thành công thì đồng bộ cấp như thưởng quest (lên cấp tới World/HUD/sư đồ). */
    ClaimSeclusionResponse claimSeclusion(long characterId, boolean doubled) {
        SeclusionPort.Claim claim = deps.daily().seclusion().claim(characterId, doubled);
        if (claim.outcome() == SeclusionPort.Outcome.OK) {
            deps.registry().sendExperience(characterId, deps.characters().syncExperienceAfterQuestReward(characterId));
        }
        return ClaimSeclusionResponse.newBuilder()
                .setOutcome(ClaimSeclusionOutcome.valueOf(
                        "CLAIM_SECLUSION_OUTCOME_" + claim.outcome().name()))
                .setExpGained(claim.expGained())
                .build();
    }

    GetOnlineRewardsResponse onlineRewards(long characterId) {
        OnlineRewardFacade.Status status = deps.daily().onlineRewards().status(characterId, enteredAt(characterId));
        GetOnlineRewardsResponse.Builder response =
                GetOnlineRewardsResponse.newBuilder().setSecondsToday(status.secondsToday());
        for (OnlineRewardFacade.MilestoneView view : status.milestones()) {
            OnlineRewardPolicy.Milestone m = view.milestone();
            response.addMilestones(OnlineRewardMilestoneView.newBuilder()
                    .setMinutes(m.minutes())
                    .setReached(view.reached())
                    .setClaimed(view.claimed())
                    .setGold(m.gold())
                    .setItemCode(m.itemCode())
                    .setItemQuantity(m.itemQuantity()));
        }
        return response.build();
    }

    /** Nhận một mốc — quà đi bằng thư (túi đầy không mất quà). */
    ClaimOnlineRewardResponse claimOnlineReward(long characterId, int minutes) {
        OnlineRewardFacade.ClaimResult result =
                deps.daily().onlineRewards().claim(characterId, minutes, enteredAt(characterId));
        result.milestone()
                .ifPresent(m -> notices.mail(
                        characterId,
                        new PlayerNotices.SystemMail(
                                "Thưởng online / Online reward",
                                "Online %d phút hôm nay. / %d minutes online today."
                                        .formatted(m.minutes(), m.minutes()),
                                m.gold(),
                                items(m))));
        return ClaimOnlineRewardResponse.newBuilder()
                .setOutcome(ClaimOnlineRewardOutcome.valueOf(
                        "CLAIM_ONLINE_REWARD_OUTCOME_" + result.outcome().name()))
                .build();
    }

    DismantleItemResponse dismantle(long characterId, long itemId) {
        DismantlePort.Result result = deps.daily().dismantle().dismantle(characterId, itemId);
        return DismantleItemResponse.newBuilder()
                .setOutcome(DismantleItemOutcome.valueOf(
                        "DISMANTLE_ITEM_OUTCOME_" + result.outcome().name()))
                .setGold(result.gold())
                .setHerbQuantity(result.herbs())
                .build();
    }

    private Instant enteredAt(long characterId) {
        Instant now = clock.instant();
        return deps.registry().onlineSessions.enteredAt(characterId, now);
    }

    private List<MailFacade.AttachmentRequest> items(OnlineRewardPolicy.Milestone milestone) {
        if (milestone.itemCode().isEmpty()) {
            return List.of();
        }
        return deps.inventory().itemCatalog().entries().stream()
                .filter(entry -> entry.code().equals(milestone.itemCode()))
                .findFirst()
                .map(ItemCatalogEntry::id)
                .map(id -> List.of(new MailFacade.AttachmentRequest(id, milestone.itemQuantity(), 0)))
                .orElse(List.of());
    }
}
