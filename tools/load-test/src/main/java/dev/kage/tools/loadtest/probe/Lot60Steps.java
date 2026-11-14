package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 60: lịch sử chat thế giới (ADR-408) {@code worldHistory:}, bạn chung (ADR-409) {@code mutual:<tên>}, tuổi
 * nhân vật (ADR-410) {@code age:[tên]}.
 */
final class Lot60Steps {

    private final Lot23Scenario.Asker asker;

    Lot60Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "worldHistory" ->
                asker.ask(
                        Opcode.OPCODE_GET_WORLD_CHAT_HISTORY_REQUEST_VALUE,
                        dev.kage.protocol.GetWorldChatHistoryRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_WORLD_CHAT_HISTORY_RESPONSE_VALUE,
                        p -> "tin="
                                + dev.kage.protocol.GetWorldChatHistoryResponse.parseFrom(p)
                                        .getCount());
            case "mutual" -> mutual(arg);
            case "age" -> age(arg);
            default -> "bước không hiểu";
        };
    }

    private String mutual(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_MUTUAL_FRIENDS_REQUEST_VALUE,
                dev.kage.protocol.GetMutualFriendsRequest.newBuilder()
                        .setTargetName(arg)
                        .build(),
                Opcode.OPCODE_GET_MUTUAL_FRIENDS_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetMutualFriendsResponse r =
                            dev.kage.protocol.GetMutualFriendsResponse.parseFrom(p);
                    return "thấy=" + r.getFound() + " chung=" + r.getNamesList();
                });
    }

    private String age(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_CHARACTER_AGE_REQUEST_VALUE,
                dev.kage.protocol.GetCharacterAgeRequest.newBuilder()
                        .setTargetName(arg)
                        .build(),
                Opcode.OPCODE_GET_CHARACTER_AGE_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetCharacterAgeResponse r =
                            dev.kage.protocol.GetCharacterAgeResponse.parseFrom(p);
                    return r.getName() + " ngày=" + r.getDays();
                });
    }
}
