package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.BroadcastMessageCommand;
import dev.kage.protocol.BroadcastMessageResponse;
import dev.kage.protocol.BuyHonorItemCommand;
import dev.kage.protocol.BuyHonorItemResponse;
import dev.kage.protocol.ClaimRealmMilestoneCommand;
import dev.kage.protocol.ClaimRealmMilestoneResponse;
import dev.kage.protocol.ClaimWeeklyTaskCommand;
import dev.kage.protocol.ClaimWeeklyTaskResponse;
import dev.kage.protocol.GetHonorShopRequest;
import dev.kage.protocol.GetHonorShopResponse;
import dev.kage.protocol.GetRealmMilestonesRequest;
import dev.kage.protocol.GetRealmMilestonesResponse;
import dev.kage.protocol.GetWeeklyTasksRequest;
import dev.kage.protocol.GetWeeklyTasksResponse;
import dev.kage.protocol.Opcode;

/**
 * Bước lô 28: loa (ADR-152) {@code shout:<nội dung>}, quà mốc (ADR-153) {@code milestones}, {@code
 * claimMilestone:<cấp>}, nhiệm vụ tuần (ADR-154) {@code weekly}, {@code claimWeekly:<mã>}, cửa hàng vinh dự (ADR-155)
 * {@code honorShop}, {@code buyHonor:<templateId>}. Tách khỏi {@link Lot24Steps} vì switch ở đó chạm trần
 * CyclomaticComplexity. Bước lô 29: xem {@link Lot29Steps}.
 */
final class Lot28Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot29Steps lot29;

    Lot28Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot29 = new Lot29Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "shout" -> shout(arg);
            case "milestones" -> milestones();
            case "claimMilestone" -> claimMilestone(Integer.parseInt(arg));
            case "weekly" -> weekly();
            case "claimWeekly" -> claimWeekly(arg);
            case "honorShop" -> honorShop();
            case "buyHonor" -> buyHonor(Long.parseLong(arg));
            default -> lot29.step(verb, arg);
        };
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

    private static String describe(GetRealmMilestonesResponse board) {
        StringBuilder line = new StringBuilder("cấp=").append(board.getCharacterLevel());
        board.getMilestonesList()
                .forEach(m -> line.append(" Lv").append(m.getLevel()).append(m.getClaimed() ? "✓" : "·"));
        return line.toString();
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
