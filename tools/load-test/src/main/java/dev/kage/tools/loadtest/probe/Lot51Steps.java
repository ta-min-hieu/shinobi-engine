package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 51: lời nhắn trạng thái (ADR-335) {@code status:?|-|<chữ>}; rao vặt (ADR-336) {@code bulletin:-|<chữ>},
 * {@code bulletinList}; lịch sử tử vong (ADR-337) {@code deaths}; biểu quyết đuổi (ADR-338) {@code voteKick:<tên>},
 * {@code castVote:yes|no}.
 */
final class Lot51Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot52Steps lot52;

    Lot51Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot52 = new Lot52Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "status" -> status(arg);
            case "bulletin" ->
                asker.ask(
                        Opcode.OPCODE_POST_BULLETIN_COMMAND_VALUE,
                        dev.kage.protocol.PostBulletinCommand.newBuilder()
                                .setBody("-".equals(arg) ? "" : arg)
                                .build(),
                        Opcode.OPCODE_POST_BULLETIN_RESPONSE_VALUE,
                        p -> dev.kage.protocol.PostBulletinResponse.parseFrom(p)
                                .getOutcome()
                                .name());
            case "bulletinList" -> bulletinList();
            case "deaths" ->
                asker.ask(
                        Opcode.OPCODE_GET_DEATH_HISTORY_REQUEST_VALUE,
                        dev.kage.protocol.GetDeathHistoryRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_DEATH_HISTORY_RESPONSE_VALUE,
                        p -> "tửVong="
                                + dev.kage.protocol.GetDeathHistoryResponse.parseFrom(p)
                                        .getEntriesCount());
            case "voteKick" ->
                asker.ask(
                        Opcode.OPCODE_START_VOTE_KICK_COMMAND_VALUE,
                        dev.kage.protocol.StartVoteKickCommand.newBuilder()
                                .setTargetName(arg)
                                .build(),
                        Opcode.OPCODE_START_VOTE_KICK_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.StartVoteKickResponse r =
                                    dev.kage.protocol.StartVoteKickResponse.parseFrom(p);
                            return r.getOutcome().name() + " " + r.getYes() + "/" + r.getNeeded();
                        });
            case "castVote" ->
                asker.ask(
                        Opcode.OPCODE_CAST_VOTE_KICK_COMMAND_VALUE,
                        dev.kage.protocol.CastVoteKickCommand.newBuilder()
                                .setYes("yes".equals(arg))
                                .build(),
                        Opcode.OPCODE_CAST_VOTE_KICK_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.CastVoteKickResponse r =
                                    dev.kage.protocol.CastVoteKickResponse.parseFrom(p);
                            return r.getOutcome().name() + " " + r.getYes() + "/" + r.getNeeded();
                        });
            default -> lot52.step(verb, arg);
        };
    }

    private String status(String arg) throws IOException {
        dev.kage.protocol.SetStatusMessageCommand.Builder request =
                dev.kage.protocol.SetStatusMessageCommand.newBuilder();
        if ("?".equals(arg)) {
            request.setQueryOnly(true);
        } else {
            request.setMessage("-".equals(arg) ? "" : arg);
        }
        return asker.ask(
                Opcode.OPCODE_SET_STATUS_MESSAGE_COMMAND_VALUE,
                request.build(),
                Opcode.OPCODE_SET_STATUS_MESSAGE_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SetStatusMessageResponse r =
                            dev.kage.protocol.SetStatusMessageResponse.parseFrom(p);
                    return r.getOutcome().name() + " '" + r.getMessage() + "'";
                });
    }

    private String bulletinList() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_BULLETIN_REQUEST_VALUE,
                dev.kage.protocol.ListBulletinRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_BULLETIN_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("raoVặt");
                    for (dev.kage.protocol.BulletinPost post :
                            dev.kage.protocol.ListBulletinResponse.parseFrom(p).getPostsList()) {
                        out.append(" [")
                                .append(post.getName())
                                .append(": ")
                                .append(post.getBody())
                                .append(']');
                    }
                    return out.toString();
                });
    }
}
