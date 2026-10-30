package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;

import dev.kage.protocol.AwaitingRevivalEvent;
import dev.kage.protocol.BuyFromMerchantCommand;
import dev.kage.protocol.BuyFromMerchantResponse;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.GetTravelingMerchantRequest;
import dev.kage.protocol.GetTravelingMerchantResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ReviveCommand;
import dev.kage.protocol.ReviveResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Bước thế giới lô 23: {@code awaitRevival:<giây>} (đứng yên chờ gục — in gói 321, kèm máu lúc hồi sinh nếu server tự
 * đưa về), {@code revive:<town|pill>} (in kết quả + gói hồi sinh 305), {@code merchant}, {@code
 * buyMerchant:<templateId>}.
 */
final class Lot23WorldSteps {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final Lot23Scenario.Asker asker;

    Lot23WorldSteps(GameConnection connection, Lot23Scenario.Asker asker) {
        this.connection = connection;
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "awaitRevival" -> awaitRevival(Integer.parseInt(arg));
            case "revive" -> revive("pill".equals(arg));
            case "merchant" ->
                asker.ask(
                        Opcode.OPCODE_GET_TRAVELING_MERCHANT_REQUEST_VALUE,
                        GetTravelingMerchantRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_TRAVELING_MERCHANT_RESPONSE_VALUE,
                        p -> describe(GetTravelingMerchantResponse.parseFrom(p)));
            case "buyMerchant" ->
                asker.ask(
                        Opcode.OPCODE_BUY_FROM_MERCHANT_COMMAND_VALUE,
                        BuyFromMerchantCommand.newBuilder()
                                .setItemTemplateId(Long.parseLong(arg))
                                .build(),
                        Opcode.OPCODE_BUY_FROM_MERCHANT_RESPONSE_VALUE,
                        p -> BuyFromMerchantResponse.parseFrom(p).getResult().name());
            default -> "bước không hiểu";
        };
    }

    private String awaitRevival(int seconds) throws IOException {
        long deadline = System.currentTimeMillis() + seconds * MILLIS_PER_SECOND;
        String seen = "không gục trong " + seconds + "s";
        while (System.currentTimeMillis() < deadline) {
            PacketCodec.Frame frame;
            try {
                frame = connection.readFrame();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_AWAITING_REVIVAL_EVENT_VALUE) {
                AwaitingRevivalEvent e = AwaitingRevivalEvent.parseFrom(frame.payload());
                return "GỤC chờ=" + e.getSeconds() + "s tạiChỗ=" + e.getCanReviveInPlace();
            }
            if (opcode == Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE) {
                seen = "hồi sinh NGAY (không chờ) " + describe(CharacterRespawnedEvent.parseFrom(frame.payload()));
            }
        }
        return seen;
    }

    private String revive(boolean pill) throws IOException {
        String outcome = asker.ask(
                Opcode.OPCODE_REVIVE_COMMAND_VALUE,
                ReviveCommand.newBuilder().setInPlace(pill).build(),
                Opcode.OPCODE_REVIVE_RESPONSE_VALUE,
                p -> ReviveResponse.parseFrom(p).getOutcome().name());
        if (!outcome.endsWith("_OK")) {
            return outcome;
        }
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame;
            try {
                frame = connection.readFrame();
            } catch (SocketTimeoutException quiet) {
                return outcome + " (chưa thấy gói 305)";
            }
            if (frame.header().opcode() == Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE) {
                return outcome + " " + describe(CharacterRespawnedEvent.parseFrom(frame.payload()));
            }
        }
        return outcome;
    }

    private static String describe(GetTravelingMerchantResponse r) {
        if (!r.getPresent()) {
            return "vắng mặt";
        }
        StringBuilder line = new StringBuilder("ở " + r.getWaypointCode() + " x=" + Math.round(r.getX()) + " hàng:");
        r.getStockList().forEach(s -> line.append(" tpl")
                .append(s.getItemTemplateId())
                .append('@')
                .append(s.getPrice())
                .append(" còn ")
                .append(s.getRemaining()));
        return line.toString();
    }

    private static String describe(CharacterRespawnedEvent e) {
        return "x=" + Math.round(e.getX()) + " máu=" + e.getCurrentHp() + "/" + e.getMaxHp();
    }
}
