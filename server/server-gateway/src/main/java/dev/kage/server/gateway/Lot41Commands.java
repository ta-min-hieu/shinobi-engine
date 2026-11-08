package dev.kage.server.gateway;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.guild.domain.GuildEventPort;
import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.monster.domain.MonsterInfoPort;
import dev.kage.protocol.CancelGuildEventCommand;
import dev.kage.protocol.CancelGuildEventOutcome;
import dev.kage.protocol.CancelGuildEventResponse;
import dev.kage.protocol.CreateGuildEventCommand;
import dev.kage.protocol.CreateGuildEventOutcome;
import dev.kage.protocol.CreateGuildEventResponse;
import dev.kage.protocol.EnhancePreviewOutcome;
import dev.kage.protocol.FriendRankEntry;
import dev.kage.protocol.GetEnhancePreviewRequest;
import dev.kage.protocol.GetEnhancePreviewResponse;
import dev.kage.protocol.GetFriendRankingResponse;
import dev.kage.protocol.GetMonsterInfoRequest;
import dev.kage.protocol.GetMonsterInfoResponse;
import dev.kage.protocol.GuildEventView;
import dev.kage.protocol.ListGuildEventsResponse;
import dev.kage.protocol.MonsterDropView;

/** Lớp lệnh lô 41 (ADR-255…) — MỘT thành phần của {@link Lot40Commands}. Gọi trên Virtual Thread. */
record Lot41Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 42 (ADR-263…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot42Commands lot42() {
        return new Lot42Commands(gateway, registry);
    }

    /** ADR-256 — trần giờ hẹn nhận từ gói (năm 9999); facade còn giới hạn chặt hơn (7 ngày tới). */
    static final long MAX_EPOCH_SECONDS = 253_402_300_799L;

    /** ADR-255 — chỉ số + bảng rơi của một loại quái (thông tin công khai). */
    GetMonsterInfoResponse monsterInfo(GetMonsterInfoRequest request) {
        Optional<MonsterInfoPort.Profile> info = facades().monsterInfo().info(request.getMonsterCode());
        if (info.isEmpty()) {
            return GetMonsterInfoResponse.newBuilder()
                    .setFound(false)
                    .setMonsterCode(request.getMonsterCode())
                    .build();
        }
        MonsterInfoPort.Profile monster = info.get();
        GetMonsterInfoResponse.Builder response = GetMonsterInfoResponse.newBuilder()
                .setFound(true)
                .setMonsterCode(monster.code())
                .setNameKey(monster.nameKey())
                .setLevel(monster.level())
                .setMaxHp(monster.maxHp())
                .setAtk(monster.atk())
                .setDef(monster.def())
                .setExpReward(monster.expReward())
                .setBoss(monster.boss());
        for (MonsterInfoPort.Drop drop : monster.drops()) {
            response.addDrops(MonsterDropView.newBuilder()
                    .setItemTemplateId(drop.itemTemplateId())
                    .setChancePercent(drop.chancePercent())
                    .setMinQuantity(drop.minQuantity())
                    .setMaxQuantity(drop.maxQuantity()));
        }
        return response.build();
    }

    /** ADR-257 — giá / tỉ lệ / rủi ro của lần cường hoá kế tiếp, đúng {@link EnhancementTable} server dùng. */
    GetEnhancePreviewResponse enhancePreview(long characterId, GetEnhancePreviewRequest request) {
        List<ItemStack> bag =
                gateway.inventoryFacade().listInventory(characterId).inventory().items();
        Optional<ItemStack> item =
                bag.stream().filter(stack -> stack.id() == request.getItemId()).findFirst();
        GetEnhancePreviewResponse.Builder response = GetEnhancePreviewResponse.newBuilder();
        if (item.isEmpty()) {
            return response.setOutcome(EnhancePreviewOutcome.ENHANCE_PREVIEW_OUTCOME_NOT_FOUND)
                    .build();
        }
        int level = item.get().enhanceLevel();
        response.setCurrentLevel(level);
        if (!enhanceable(item.get().itemTemplateId())) {
            return response.setOutcome(EnhancePreviewOutcome.ENHANCE_PREVIEW_OUTCOME_NOT_EQUIPMENT)
                    .build();
        }
        if (level >= ItemStack.MAX_ENHANCE) {
            return response.setOutcome(EnhancePreviewOutcome.ENHANCE_PREVIEW_OUTCOME_MAX_LEVEL)
                    .build();
        }
        return response.setOutcome(EnhancePreviewOutcome.ENHANCE_PREVIEW_OUTCOME_OK)
                .setGoldCost(EnhancementTable.goldCost(level))
                .setSuccessPercent(EnhancementTable.successRatePercent(level))
                .setDropsOnFail(level >= EnhancementTable.DROP_ON_FAIL_FROM_LEVEL)
                .setProtectionCharms(protectionCharms(bag))
                .build();
    }

    /** Cùng luật lò cường hoá: là trang bị, không phải phi kiếm (MOUNT) hay linh thú (PET). */
    private boolean enhanceable(long templateId) {
        return catalog().stream()
                .filter(entry -> entry.id() == templateId)
                .findFirst()
                .map(entry -> entry.equipSlot() != null
                        && entry.equipSlot() != EquipmentSlotType.MOUNT
                        && entry.equipSlot() != EquipmentSlotType.PET)
                .orElse(false);
    }

    private int protectionCharms(List<ItemStack> bag) {
        Optional<Long> charm = catalog().stream()
                .filter(entry -> EnhancementTable.PROTECTION_CHARM_CODE.equals(entry.code()))
                .map(ItemCatalogEntry::id)
                .findFirst();
        return charm.map(id -> bag.stream()
                        .filter(stack -> stack.itemTemplateId() == id)
                        .mapToInt(ItemStack::quantity)
                        .sum())
                .orElse(0);
    }

    private List<ItemCatalogEntry> catalog() {
        return gateway.inventoryFacade().itemCatalog().entries();
    }

    /** ADR-256 — bang chủ / phó hẹn một buổi (lọc từ cấm như chat). */
    CreateGuildEventResponse createEvent(long characterId, CreateGuildEventCommand command) {
        CreateGuildEventOutcome outcome;
        long startsAt = command.getStartsAtEpochSeconds();
        if (startsAt < 0L || startsAt > MAX_EPOCH_SECONDS) {
            // Số tuỳ ý từ gói (fuzz: Long.MAX_VALUE) ⇒ Instant.ofEpochSecond ném — chặn trước, coi là sai giờ.
            outcome = CreateGuildEventOutcome.CREATE_GUILD_EVENT_OUTCOME_INVALID;
        } else if (ProfanityFilter.containsBannedWord(command.getTitle())) {
            outcome = CreateGuildEventOutcome.CREATE_GUILD_EVENT_OUTCOME_BANNED_WORD;
        } else {
            GuildEventPort.CreateOutcome created = facades()
                    .guildEvents()
                    .create(
                            characterId,
                            command.getTitle(),
                            Instant.ofEpochSecond(command.getStartsAtEpochSeconds()),
                            Instant.now());
            outcome = CreateGuildEventOutcome.valueOf("CREATE_GUILD_EVENT_OUTCOME_" + created.name());
        }
        return CreateGuildEventResponse.newBuilder().setOutcome(outcome).build();
    }

    /** ADR-256 — huỷ một buổi. */
    CancelGuildEventResponse cancelEvent(long characterId, CancelGuildEventCommand command) {
        GuildEventPort.CancelOutcome outcome = facades().guildEvents().cancel(characterId, command.getEventId());
        return CancelGuildEventResponse.newBuilder()
                .setOutcome(CancelGuildEventOutcome.valueOf("CANCEL_GUILD_EVENT_OUTCOME_" + outcome.name()))
                .build();
    }

    /** ADR-256 — các buổi sắp tới. */
    ListGuildEventsResponse listEvents(long characterId) {
        ListGuildEventsResponse.Builder response = ListGuildEventsResponse.newBuilder();
        for (GuildEventPort.Event event : facades().guildEvents().upcoming(characterId, Instant.now())) {
            response.addEvents(GuildEventView.newBuilder()
                    .setEventId(event.id())
                    .setTitle(event.title())
                    .setStartsAtEpochSeconds(event.startsAt().getEpochSecond()));
        }
        return response.build();
    }

    /** ADR-258 — mình + bạn bè từ bảng Lực Chiến đã đệm (ADR-056/057), giảm dần. */
    GetFriendRankingResponse friendRanking(long characterId, LeaderboardCache.Board combatPower) {
        List<Long> ids = new ArrayList<>(
                gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId));
        ids.add(characterId);
        List<LeaderboardCache.Row> rows = new ArrayList<>();
        for (long id : ids) {
            LeaderboardCache.Row row = combatPower.byCharacterId().get(id);
            if (row != null) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparingLong(LeaderboardCache.Row::value).reversed());
        GetFriendRankingResponse.Builder response = GetFriendRankingResponse.newBuilder();
        for (LeaderboardCache.Row row : rows) {
            response.addEntries(FriendRankEntry.newBuilder()
                    .setCharacterId(row.characterId())
                    .setName(row.name())
                    .setLevel(row.level())
                    .setCombatPower(row.value())
                    .setIsSelf(row.characterId() == characterId));
        }
        return response.build();
    }

    private Lot41Facades facades() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41();
    }
}
