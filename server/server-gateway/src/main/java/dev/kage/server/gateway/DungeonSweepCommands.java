package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;

import dev.kage.character.api.CharacterFacade;
import dev.kage.checkin.api.ActivityFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.SweepDungeonCommand;
import dev.kage.protocol.SweepDungeonOutcome;
import dev.kage.protocol.SweepDungeonResponse;
import dev.kage.protocol.SweepLootView;
import dev.kage.world.api.DungeonQuotaFacade;
import dev.kage.world.api.DungeonSweepFacade;
import dev.kage.world.domain.DungeonSweepPort;

/**
 * Quét nhanh Bí Cảnh (ADR-162). Server kiểm lần đầu hạ boss, lượt ngày, vàng (một transaction); thành công thì đồng bộ
 * cấp như thưởng quest, tung bảng rơi của boss MỘT lần và gửi đồ bằng thư hệ thống (túi đầy không mất).
 */
final class DungeonSweepCommands {

    /** Phụ thuộc — gom lại để constructor không vượt trần tham số. */
    record Deps(
            DungeonSweepFacade sweeps,
            InventoryFacade inventory,
            CharacterFacade characters,
            ConnectedCharacterRegistry registry,
            PlayerNotices notices) {}

    private final Deps deps;

    DungeonSweepCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    SweepDungeonResponse sweep(long characterId, SweepDungeonCommand command) {
        DungeonSweepPort.Result result = deps.sweeps().sweep(characterId, command.getDungeonCode());
        SweepDungeonResponse.Builder response = SweepDungeonResponse.newBuilder()
                .setOutcome(wire(result.outcome()))
                .setEntriesLeft(Math.max(0, DungeonQuotaFacade.MAX_DAILY_ENTRIES - result.entriesUsed()));
        if (result.outcome() != DungeonSweepPort.Outcome.OK) {
            return response.build();
        }
        response.setExpGained(result.expGained());
        deps.registry().sendExperience(characterId, deps.characters().syncExperienceAfterQuestReward(characterId));
        deps.registry().activity.record(characterId, ActivityFacade.Activity.DUNGEON_RUN);
        // Tung bảng rơi nhưng KHÔNG cho vào túi (divert mọi chồng) — đồ đi bằng thư.
        List<ItemStack> loot = deps.inventory()
                .grantLoot(characterId, result.dropTableCode(), stack -> true)
                .diverted();
        List<MailFacade.AttachmentRequest> attachments = new ArrayList<>();
        for (ItemStack stack : loot) {
            attachments.add(new MailFacade.AttachmentRequest(stack.itemTemplateId(), stack.quantity()));
            response.addLoot(SweepLootView.newBuilder()
                    .setItemTemplateId(stack.itemTemplateId())
                    .setQuantity(stack.quantity()));
        }
        if (!attachments.isEmpty()) {
            deps.notices()
                    .mail(
                            characterId,
                            new PlayerNotices.SystemMail(
                                    "Quét Bí Cảnh / Dungeon sweep",
                                    "Chiến lợi phẩm quét Bí Cảnh. / Loot from your dungeon sweep.",
                                    0L,
                                    attachments));
        }
        return response.build();
    }

    private static SweepDungeonOutcome wire(DungeonSweepPort.Outcome outcome) {
        return switch (outcome) {
            case OK -> SweepDungeonOutcome.SWEEP_DUNGEON_OUTCOME_OK;
            case UNKNOWN_DUNGEON -> SweepDungeonOutcome.SWEEP_DUNGEON_OUTCOME_UNKNOWN_DUNGEON;
            case NOT_CLEARED -> SweepDungeonOutcome.SWEEP_DUNGEON_OUTCOME_NOT_CLEARED;
            case DAILY_LIMIT_REACHED -> SweepDungeonOutcome.SWEEP_DUNGEON_OUTCOME_DAILY_LIMIT_REACHED;
            case NOT_ENOUGH_GOLD -> SweepDungeonOutcome.SWEEP_DUNGEON_OUTCOME_NOT_ENOUGH_GOLD;
        };
    }
}
