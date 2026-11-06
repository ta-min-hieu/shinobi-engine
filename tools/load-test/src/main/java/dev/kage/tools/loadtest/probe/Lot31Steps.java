package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.ApplyEquipmentPresetCommand;
import dev.kage.protocol.ApplyEquipmentPresetResponse;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.CongratulateFriendCommand;
import dev.kage.protocol.CongratulateFriendResponse;
import dev.kage.protocol.EquipmentPresetSlot;
import dev.kage.protocol.EquipmentPresetView;
import dev.kage.protocol.GetEquipmentPresetsRequest;
import dev.kage.protocol.GetEquipmentPresetsResponse;
import dev.kage.protocol.GetGuildLogRequest;
import dev.kage.protocol.GetGuildLogResponse;
import dev.kage.protocol.GetMarketHistoryRequest;
import dev.kage.protocol.GetMarketHistoryResponse;
import dev.kage.protocol.GetMyGuildRequest;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GuildLogEntry;
import dev.kage.protocol.LuckyBagRewardView;
import dev.kage.protocol.MarketHistoryEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.OpenLuckyBagsCommand;
import dev.kage.protocol.OpenLuckyBagsResponse;
import dev.kage.protocol.SaveEquipmentPresetCommand;
import dev.kage.protocol.SaveEquipmentPresetResponse;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SetAwayMessageCommand;
import dev.kage.protocol.SetAwayMessageResponse;
import dev.kage.protocol.SetGuildNoticeCommand;
import dev.kage.protocol.SetGuildNoticeResponse;
import dev.kage.protocol.UseItemsCommand;
import dev.kage.protocol.UseItemsResponse;

/**
 * Bước lô 31: bộ trang bị (ADR-175) {@code savePreset:<n>}, {@code applyPreset:<n>}, {@code listPresets}; dùng nhiều
 * (ADR-176) {@code useMany:<itemId>x<n>}, {@code openBags:<mẫu túi>x<n>}; lịch sử phường thị (ADR-177) {@code
 * marketHistory}; vắng mặt (ADR-178) {@code setAway:<lời nhắn>}, {@code whisperAway:<tên>} (nhắn riêng rồi chờ tin tự
 * trả lời). Nối sau {@link Lot30SocialSteps}.
 */
final class Lot31Steps {

    /** Số tin chat tối đa bỏ qua khi chờ tin tự trả lời. */
    private static final int MAX_CHAT_SKIPS = 5;

    private final Lot23Scenario.Asker asker;

    Lot31Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "savePreset" -> savePreset(Integer.parseInt(arg));
            case "applyPreset" -> applyPreset(Integer.parseInt(arg));
            case "listPresets" -> listPresets();
            case "useMany" -> useMany(arg);
            case "openBags" -> openBags(arg);
            case "marketHistory" -> marketHistory();
            default -> socialStep(verb, arg);
        };
    }

    /** Nhánh xã hội — tách khỏi {@link #step} vì trần CyclomaticComplexity. */
    private String socialStep(String verb, String arg) throws IOException {
        return switch (verb) {
            case "setAway" -> setAway(arg);
            case "whisperAway" -> whisperAway(arg);
            case "guildNotice" -> guildNotice(arg);
            case "myGuild" -> myGuild();
            case "guildLog" -> guildLog();
            case "congratulate" -> congratulate(arg);
            default -> "bước không hiểu";
        };
    }

    private String savePreset(int presetNo) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_COMMAND_VALUE,
                SaveEquipmentPresetCommand.newBuilder().setPresetNo(presetNo).build(),
                Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_RESPONSE_VALUE,
                p -> {
                    SaveEquipmentPresetResponse r = SaveEquipmentPresetResponse.parseFrom(p);
                    return r.getOutcome().name() + " " + describe(r.getPreset());
                });
    }

    private String applyPreset(int presetNo) throws IOException {
        return asker.ask(
                Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_COMMAND_VALUE,
                ApplyEquipmentPresetCommand.newBuilder().setPresetNo(presetNo).build(),
                Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_RESPONSE_VALUE,
                p -> {
                    ApplyEquipmentPresetResponse r = ApplyEquipmentPresetResponse.parseFrom(p);
                    return r.getOutcome().name() + " mặc=" + r.getEquipped() + " thiếu=" + r.getMissing() + " máu="
                            + r.getMaxHp();
                });
    }

    private String listPresets() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_EQUIPMENT_PRESETS_REQUEST_VALUE,
                GetEquipmentPresetsRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_EQUIPMENT_PRESETS_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (EquipmentPresetView preset :
                            GetEquipmentPresetsResponse.parseFrom(p).getPresetsList()) {
                        out.append(describe(preset)).append(" | ");
                    }
                    return out.toString();
                });
    }

    private String useMany(String arg) throws IOException {
        String[] parts = arg.split("x", 2);
        return asker.ask(
                Opcode.OPCODE_USE_ITEMS_COMMAND_VALUE,
                UseItemsCommand.newBuilder()
                        .setInventoryItemId(Long.parseLong(parts[0]))
                        .setCount(Integer.parseInt(parts[1]))
                        .build(),
                Opcode.OPCODE_USE_ITEMS_RESPONSE_VALUE,
                p -> {
                    UseItemsResponse r = UseItemsResponse.parseFrom(p);
                    return "dùng=" + r.getUsed() + " còn=" + r.getRemainingQuantity() + " dừng=" + r.getStopErrorKey();
                });
    }

    private String openBags(String arg) throws IOException {
        String[] parts = arg.split("x", 2);
        return asker.ask(
                Opcode.OPCODE_OPEN_LUCKY_BAGS_COMMAND_VALUE,
                OpenLuckyBagsCommand.newBuilder()
                        .setBagItemTemplateId(Long.parseLong(parts[0]))
                        .setCount(Integer.parseInt(parts[1]))
                        .build(),
                Opcode.OPCODE_OPEN_LUCKY_BAGS_RESPONSE_VALUE,
                p -> {
                    OpenLuckyBagsResponse r = OpenLuckyBagsResponse.parseFrom(p);
                    StringBuilder out =
                            new StringBuilder("mở=").append(r.getOpened()).append(" quà=");
                    for (LuckyBagRewardView reward : r.getRewardsList()) {
                        out.append(
                                        reward.getGold() > 0
                                                ? reward.getGold() + "vàng"
                                                : reward.getItemTemplateId() + "x" + reward.getQuantity())
                                .append(' ');
                    }
                    return out.append("dừng=").append(r.getStopOutcome().name()).toString();
                });
    }

    private String marketHistory() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_MARKET_HISTORY_REQUEST_VALUE,
                GetMarketHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_MARKET_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (MarketHistoryEntry e :
                            GetMarketHistoryResponse.parseFrom(p).getEntriesList()) {
                        out.append(e.getSold() ? "BÁN " : "MUA ")
                                .append(e.getItemTemplateId())
                                .append('x')
                                .append(e.getQuantity())
                                .append(' ')
                                .append(e.getGoldDelta())
                                .append(" với ")
                                .append(e.getCounterpartName())
                                .append(" | ");
                    }
                    return out.toString();
                });
    }

    private String setAway(String message) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_AWAY_MESSAGE_COMMAND_VALUE,
                SetAwayMessageCommand.newBuilder().setMessage(message).build(),
                Opcode.OPCODE_SET_AWAY_MESSAGE_RESPONSE_VALUE,
                p -> {
                    SetAwayMessageResponse r = SetAwayMessageResponse.parseFrom(p);
                    return r.getOutcome().name() + " vắng=" + r.getAway() + " lời=" + r.getMessage();
                });
    }

    /** Nhắn riêng rồi đọc các tin chat kế tiếp, bỏ qua tin hệ thống, tới khi gặp tin tự trả lời (tối đa vài tin). */
    private String whisperAway(String name) throws IOException {
        String seen = firstChat(name);
        for (int attempt = 0; attempt < MAX_CHAT_SKIPS && !seen.startsWith("tựTrảLời=true"); attempt++) {
            // Gói hỏi vô hại chỉ để đọc tiếp luồng — Asker khớp theo opcode trả lời, không theo số thứ tự.
            seen = asker.ask(
                    Opcode.OPCODE_GET_MARKET_HISTORY_REQUEST_VALUE,
                    GetMarketHistoryRequest.getDefaultInstance(),
                    Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                    Lot31Steps::describeChat);
        }
        return seen;
    }

    private String firstChat(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                SendChatMessageCommand.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                        .setTargetName(name)
                        .setBody("probe lô 31 vắng")
                        .build(),
                Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                Lot31Steps::describeChat);
    }

    private static String describeChat(byte[] payload) throws com.google.protobuf.InvalidProtocolBufferException {
        ChatMessageEvent e = ChatMessageEvent.parseFrom(payload);
        return "tựTrảLời=" + e.getAutoReply() + " từ=" + e.getSenderName() + " nội dung=" + e.getBody();
    }

    private String guildNotice(String notice) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_GUILD_NOTICE_COMMAND_VALUE,
                SetGuildNoticeCommand.newBuilder().setNotice(notice).build(),
                Opcode.OPCODE_SET_GUILD_NOTICE_RESPONSE_VALUE,
                p -> {
                    SetGuildNoticeResponse r = SetGuildNoticeResponse.parseFrom(p);
                    return r.getOutcome().name() + " thôngBáo=" + r.getNotice();
                });
    }

    private String myGuild() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_MY_GUILD_REQUEST_VALUE,
                GetMyGuildRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE,
                p -> {
                    GetMyGuildResponse r = GetMyGuildResponse.parseFrom(p);
                    return "bang=" + r.getGuildName() + " thôngBáo=" + r.getNotice();
                });
    }

    private String guildLog() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_GUILD_LOG_REQUEST_VALUE,
                GetGuildLogRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_GUILD_LOG_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (GuildLogEntry e : GetGuildLogResponse.parseFrom(p).getEntriesList()) {
                        out.append(e.getKind().name().replace("GUILD_LOG_KIND_", ""))
                                .append('(')
                                .append(e.getActorName())
                                .append("→")
                                .append(e.getSubjectName())
                                .append(' ')
                                .append(e.getAmount())
                                .append(' ')
                                .append(e.getDetail())
                                .append(") ");
                    }
                    return out.toString();
                });
    }

    private String congratulate(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_CONGRATULATE_FRIEND_COMMAND_VALUE,
                CongratulateFriendCommand.newBuilder().setFriendName(name).build(),
                Opcode.OPCODE_CONGRATULATE_FRIEND_RESPONSE_VALUE,
                p -> CongratulateFriendResponse.parseFrom(p).getOutcome().name());
    }

    private static String describe(EquipmentPresetView preset) {
        StringBuilder out = new StringBuilder("bộ").append(preset.getPresetNo()).append('[');
        for (EquipmentPresetSlot slot : preset.getSlotsList()) {
            out.append(slot.getSlot().name().replace("EQUIPMENT_SLOT_", ""))
                    .append('=')
                    .append(slot.getItemId())
                    .append('/')
                    .append(slot.getItemTemplateId())
                    .append(' ');
        }
        return out.append(']').toString();
    }
}
