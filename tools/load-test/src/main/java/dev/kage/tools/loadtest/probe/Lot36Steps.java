package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 36: tách chồng (ADR-215) {@code split:<inventory item id>/<số>}; xoá thư đã xong (ADR-216) {@code cleanMail};
 * ghim thư (ADR-217) {@code pinMail:<mail id>/on|off}; dòng giới thiệu (ADR-218) {@code bio:<chữ>}. Nối sau {@link
 * Lot35Steps}.
 */
final class Lot36Steps {

    private final Lot23Scenario.Asker asker;

    Lot36Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "split" -> split(arg);
            case "cleanMail" -> cleanMail();
            case "pinMail" -> pinMail(arg);
            case "bio" -> bio(arg);
            default -> "bước không hiểu";
        };
    }

    private String split(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        return asker.ask(
                Opcode.OPCODE_SPLIT_STACK_COMMAND_VALUE,
                dev.kage.protocol.SplitStackCommand.newBuilder()
                        .setInventoryItemId(Long.parseLong(parts[0]))
                        .setAmount(Integer.parseInt(parts[1]))
                        .build(),
                Opcode.OPCODE_SPLIT_STACK_RESPONSE_VALUE,
                p -> dev.kage.protocol.SplitStackResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String cleanMail() throws IOException {
        return asker.ask(
                Opcode.OPCODE_DELETE_FINISHED_MAIL_COMMAND_VALUE,
                dev.kage.protocol.DeleteFinishedMailCommand.getDefaultInstance(),
                Opcode.OPCODE_DELETE_FINISHED_MAIL_RESPONSE_VALUE,
                p -> "đãXoá="
                        + dev.kage.protocol.DeleteFinishedMailResponse.parseFrom(p)
                                .getDeleted());
    }

    private String pinMail(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        return asker.ask(
                Opcode.OPCODE_PIN_MAIL_COMMAND_VALUE,
                dev.kage.protocol.PinMailCommand.newBuilder()
                        .setMailId(Long.parseLong(parts[0]))
                        .setPinned("on".equals(parts[1]))
                        .build(),
                Opcode.OPCODE_PIN_MAIL_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.PinMailResponse r = dev.kage.protocol.PinMailResponse.parseFrom(p);
                    return "ok=" + r.getOk() + " ghim=" + r.getPinned();
                });
    }

    private String bio(String text) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_BIO_COMMAND_VALUE,
                dev.kage.protocol.SetBioCommand.newBuilder().setBio(text).build(),
                Opcode.OPCODE_SET_BIO_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SetBioResponse r = dev.kage.protocol.SetBioResponse.parseFrom(p);
                    return r.getOutcome().name() + " bio='" + r.getBio() + "'";
                });
    }
}
