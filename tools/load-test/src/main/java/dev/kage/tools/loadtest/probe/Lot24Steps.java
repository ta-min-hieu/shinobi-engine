package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.Locale;

import dev.kage.protocol.BroadcastMessageCommand;
import dev.kage.protocol.BroadcastMessageResponse;
import dev.kage.protocol.BuyHonorItemCommand;
import dev.kage.protocol.BuyHonorItemResponse;
import dev.kage.protocol.CaveAction;
import dev.kage.protocol.CaveCommand;
import dev.kage.protocol.CaveDecorationView;
import dev.kage.protocol.CaveResponse;
import dev.kage.protocol.ClaimRealmMilestoneCommand;
import dev.kage.protocol.ClaimRealmMilestoneResponse;
import dev.kage.protocol.ClaimWeeklyTaskCommand;
import dev.kage.protocol.ClaimWeeklyTaskResponse;
import dev.kage.protocol.DrawFortuneCommand;
import dev.kage.protocol.DrawFortuneResponse;
import dev.kage.protocol.GetHonorShopRequest;
import dev.kage.protocol.GetHonorShopResponse;
import dev.kage.protocol.GetLuckyWheelRequest;
import dev.kage.protocol.GetLuckyWheelResponse;
import dev.kage.protocol.GetRealmMilestonesRequest;
import dev.kage.protocol.GetRealmMilestonesResponse;
import dev.kage.protocol.GetWeeklyTasksRequest;
import dev.kage.protocol.GetWeeklyTasksResponse;
import dev.kage.protocol.LootRollChoice;
import dev.kage.protocol.LootRollChoiceCommand;
import dev.kage.protocol.LootRollChoiceResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameCharacterCommand;
import dev.kage.protocol.RenameCharacterResponse;
import dev.kage.protocol.SpinLuckyWheelCommand;
import dev.kage.protocol.SpinLuckyWheelResponse;
import dev.kage.protocol.TreasureAction;
import dev.kage.protocol.TreasureHuntCommand;
import dev.kage.protocol.TreasureHuntResponse;

/**
 * Bước lô 24: động phủ (ADR-129) {@code cave:<view|enter|collect|upgrade|decorate/<mã>|invite/<tên>|visit/<tên>>}.
 * Lô 27: Tàng Bảo Đồ (ADR-147) {@code treasure:<status|read|dig>}, Dịch Danh Phù (ADR-148) {@code rename:<tên mới>},
 * quẻ bói (ADR-149) {@code fortune}, xúc xắc chia đồ (ADR-150) {@code lootRoll:<rollId>:<need|greed|pass>},
 * vòng quay (ADR-151) {@code wheel}, {@code spin}. Lô 28: loa (ADR-152) {@code shout:<nội dung>},
 * quà mốc (ADR-153) {@code milestones}, {@code claimMilestone:<cấp>}, nhiệm vụ tuần (ADR-154) {@code weekly},
 * {@code claimWeekly:<mã>}, cửa hàng vinh dự (ADR-155) {@code honorShop}, {@code buyHonor:<templateId>}.
 */
final class Lot24Steps {

    private final Lot23Scenario.Asker asker;

    Lot24Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "cave" -> cave(arg);
            case "treasure" -> treasure(arg);
            case "wheel" -> wheel();
            case "spin" -> spin();
            case "shout" -> shout(arg);
            case "milestones" -> milestones();
            case "claimMilestone" -> claimMilestone(Integer.parseInt(arg));
            case "weekly" -> weekly();
            case "claimWeekly" -> claimWeekly(arg);
            case "honorShop" -> honorShop();
            case "buyHonor" -> buyHonor(Long.parseLong(arg));
            case "lootRoll" -> lootRoll(arg);
            case "fortune" ->
                asker.ask(
                        Opcode.OPCODE_DRAW_FORTUNE_COMMAND_VALUE,
                        DrawFortuneCommand.getDefaultInstance(),
                        Opcode.OPCODE_DRAW_FORTUNE_RESPONSE_VALUE,
                        p -> {
                            DrawFortuneResponse r = DrawFortuneResponse.parseFrom(p);
                            return r.getOutcome().name() + " quẻ=" + r.getFortuneCode() + " exp%=" + r.getExpPercent()
                                    + " vàng=" + r.getGold() + " tpl" + r.getItemTemplateId() + "x" + r.getQuantity();
                        });
            case "rename" ->
                asker.ask(
                        Opcode.OPCODE_RENAME_CHARACTER_COMMAND_VALUE,
                        RenameCharacterCommand.newBuilder().setNewName(arg).build(),
                        Opcode.OPCODE_RENAME_CHARACTER_RESPONSE_VALUE,
                        p -> {
                            RenameCharacterResponse r = RenameCharacterResponse.parseFrom(p);
                            return r.getOutcome().name() + " name=" + r.getName() + " next="
                                    + r.getNextAllowedAtEpochMs();
                        });
            default -> "bước không hiểu";
        };
    }

    private String cave(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        String detail = parts.length > 1 ? parts[1] : "";
        CaveAction action = CaveAction.valueOf("CAVE_ACTION_" + parts[0].toUpperCase(Locale.ROOT));
        return asker.ask(
                Opcode.OPCODE_CAVE_COMMAND_VALUE,
                CaveCommand.newBuilder()
                        .setAction(action)
                        .setDecorationCode(detail)
                        .setTargetName(detail)
                        .build(),
                Opcode.OPCODE_CAVE_RESPONSE_VALUE,
                p -> {
                    CaveResponse r = CaveResponse.parseFrom(p);
                    StringBuilder placed = new StringBuilder();
                    r.getDecorationsList().stream()
                            .filter(CaveDecorationView::getPlaced)
                            .forEach(dec -> placed.append(dec.getCode()).append(' '));
                    return r.getOutcome().name() + " lv=" + r.getVeinLevel() + " perHour=" + r.getPerHour()
                            + " stored=" + r.getStored() + "/" + r.getCapacity() + " upgrade=" + r.getUpgradeCost()
                            + " collected=" + r.getCollected() + " placed=["
                            + placed.toString().strip() + "]";
                });
    }

    private String treasure(String arg) throws IOException {
        TreasureAction action = TreasureAction.valueOf("TREASURE_ACTION_" + arg.toUpperCase(Locale.ROOT));
        return asker.ask(
                Opcode.OPCODE_TREASURE_HUNT_COMMAND_VALUE,
                TreasureHuntCommand.newBuilder().setAction(action).build(),
                Opcode.OPCODE_TREASURE_HUNT_RESPONSE_VALUE,
                p -> {
                    TreasureHuntResponse r = TreasureHuntResponse.parseFrom(p);
                    return r.getOutcome().name() + " hunting=" + r.getHunting() + " spot=" + r.getSpotNameKey()
                            + " x=" + r.getSpotX() + " distance=" + r.getDistance() + " result=" + r.getResultKey()
                            + " vàng=" + r.getGold() + " tpl" + r.getItemTemplateId() + "x" + r.getQuantity()
                            + " quái=" + r.getGuardianSpawned();
                });
    }

    private String lootRoll(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        return asker.ask(
                Opcode.OPCODE_LOOT_ROLL_CHOICE_COMMAND_VALUE,
                LootRollChoiceCommand.newBuilder()
                        .setRollId(Long.parseLong(parts[0]))
                        .setChoice(LootRollChoice.valueOf("LOOT_ROLL_CHOICE_" + parts[1].toUpperCase(Locale.ROOT)))
                        .build(),
                Opcode.OPCODE_LOOT_ROLL_CHOICE_RESPONSE_VALUE,
                p -> LootRollChoiceResponse.parseFrom(p).getOutcome().name());
    }

    private String wheel() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_LUCKY_WHEEL_REQUEST_VALUE,
                GetLuckyWheelRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_LUCKY_WHEEL_RESPONSE_VALUE,
                p -> {
                    GetLuckyWheelResponse r = GetLuckyWheelResponse.parseFrom(p);
                    return "ô=" + r.getPrizesCount() + " free=" + r.getFreeSpinsLeft() + " paid=" + r.getPaidSpinsLeft()
                            + " giá=" + r.getSpinCost();
                });
    }

    private String spin() throws IOException {
        return asker.ask(
                Opcode.OPCODE_SPIN_LUCKY_WHEEL_COMMAND_VALUE,
                SpinLuckyWheelCommand.getDefaultInstance(),
                Opcode.OPCODE_SPIN_LUCKY_WHEEL_RESPONSE_VALUE,
                p -> {
                    SpinLuckyWheelResponse r = SpinLuckyWheelResponse.parseFrom(p);
                    return r.getOutcome().name() + " ô=" + r.getPrize().getLabelKey() + " vàng="
                            + r.getPrize().getGold() + " tpl"
                            + r.getPrize().getItemTemplateId() + "x"
                            + r.getPrize().getQuantity() + " paid=" + r.getPaid() + " free="
                            + r.getFreeSpinsLeft() + " paidLeft=" + r.getPaidSpinsLeft();
                });
    }

    private String shout(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_BROADCAST_MESSAGE_COMMAND_VALUE,
                BroadcastMessageCommand.newBuilder().setBody(arg).build(),
                Opcode.OPCODE_BROADCAST_MESSAGE_RESPONSE_VALUE,
                p -> {
                    BroadcastMessageResponse r = BroadcastMessageResponse.parseFrom(p);
                    return r.getOutcome().name() + " còn=" + r.getSecondsLeft() + "s";
                });
    }

    private String milestones() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_REALM_MILESTONES_REQUEST_VALUE,
                GetRealmMilestonesRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_REALM_MILESTONES_RESPONSE_VALUE,
                p -> describe(GetRealmMilestonesResponse.parseFrom(p)));
    }

    private String claimMilestone(int level) throws IOException {
        return asker.ask(
                Opcode.OPCODE_CLAIM_REALM_MILESTONE_COMMAND_VALUE,
                ClaimRealmMilestoneCommand.newBuilder().setLevel(level).build(),
                Opcode.OPCODE_CLAIM_REALM_MILESTONE_RESPONSE_VALUE,
                p -> {
                    ClaimRealmMilestoneResponse r = ClaimRealmMilestoneResponse.parseFrom(p);
                    return r.getOutcome().name() + " " + describe(r.getBoard());
                });
    }

    private static String describe(GetRealmMilestonesResponse board) {
        StringBuilder line = new StringBuilder("cấp=").append(board.getCharacterLevel());
        board.getMilestonesList()
                .forEach(m -> line.append(" Lv").append(m.getLevel()).append(m.getClaimed() ? "✓" : "·"));
        return line.toString();
    }

    private String weekly() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_WEEKLY_TASKS_REQUEST_VALUE,
                GetWeeklyTasksRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_WEEKLY_TASKS_RESPONSE_VALUE,
                p -> describe(GetWeeklyTasksResponse.parseFrom(p)));
    }

    private String claimWeekly(String code) throws IOException {
        return asker.ask(
                Opcode.OPCODE_CLAIM_WEEKLY_TASK_COMMAND_VALUE,
                ClaimWeeklyTaskCommand.newBuilder().setTaskCode(code).build(),
                Opcode.OPCODE_CLAIM_WEEKLY_TASK_RESPONSE_VALUE,
                p -> {
                    ClaimWeeklyTaskResponse r = ClaimWeeklyTaskResponse.parseFrom(p);
                    return r.getOutcome().name() + " " + describe(r.getTasks());
                });
    }

    private static String describe(GetWeeklyTasksResponse tasks) {
        StringBuilder line = new StringBuilder();
        tasks.getTasksList().forEach(t -> line.append(' ')
                .append(t.getCode())
                .append('=')
                .append(t.getProgress())
                .append('/')
                .append(t.getTarget())
                .append(t.getClaimed() ? "✓" : ""));
        return line.toString().strip();
    }

    private String honorShop() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_HONOR_SHOP_REQUEST_VALUE,
                GetHonorShopRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_HONOR_SHOP_RESPONSE_VALUE,
                p -> describe(GetHonorShopResponse.parseFrom(p)));
    }

    private String buyHonor(long itemTemplateId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_BUY_HONOR_ITEM_COMMAND_VALUE,
                BuyHonorItemCommand.newBuilder()
                        .setItemTemplateId(itemTemplateId)
                        .build(),
                Opcode.OPCODE_BUY_HONOR_ITEM_RESPONSE_VALUE,
                p -> {
                    BuyHonorItemResponse r = BuyHonorItemResponse.parseFrom(p);
                    return r.getOutcome().name() + " " + describe(r.getShop());
                });
    }

    private static String describe(GetHonorShopResponse shop) {
        StringBuilder line = new StringBuilder("điểm=").append(shop.getAvailable());
        shop.getItemsList().forEach(i -> line.append(" tpl")
                .append(i.getItemTemplateId())
                .append('@')
                .append(i.getPrice())
                .append('(')
                .append(i.getBoughtThisWeek())
                .append('/')
                .append(i.getWeeklyLimit())
                .append(')'));
        return line.toString();
    }
}
