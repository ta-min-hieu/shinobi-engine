package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.EnemyView;
import dev.kage.protocol.ListEnemiesRequest;
import dev.kage.protocol.ListEnemiesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RemoveEnemyCommand;
import dev.kage.protocol.RemoveEnemyResponse;
import dev.kage.protocol.RenamePetCommand;
import dev.kage.protocol.RenamePetResponse;
import dev.kage.protocol.TransferPartyLeaderCommand;
import dev.kage.protocol.TransferPartyLeaderResponse;

/**
 * Bước lô 33: nhường trưởng nhóm (ADR-191) {@code partyLeader:<tên>}; đặt tên linh thú (ADR-193) {@code petName:<tên>}
 * ({@code petName:} trống = xoá); cừu nhân (ADR-194) {@code enemies}, {@code unenemy:<tên>}; theo dõi phường thị
 * (ADR-196) {@code watch:<id mẫu>=<giá mỗi cái>} (giá 0 = bỏ); kho chung tài khoản (ADR-197) {@code vault},
 * {@code vaultDeposit:<id>:<sl>}, {@code vaultWithdraw:<id>:<sl>}. Nối sau {@link Lot32Steps}.
 */
final class Lot33Steps {

    private final Lot23Scenario.Asker asker;

    Lot33Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "partyLeader" -> partyLeader(arg);
            case "petName" -> petName(arg);
            case "enemies" -> enemies();
            case "unenemy" -> unenemy(arg);
            case "watch" -> watch(arg);
            default -> vaultStep(verb, arg);
        };
    }

    private String partyLeader(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_TRANSFER_PARTY_LEADER_COMMAND_VALUE,
                TransferPartyLeaderCommand.newBuilder().setTargetName(name).build(),
                Opcode.OPCODE_TRANSFER_PARTY_LEADER_RESPONSE_VALUE,
                p -> {
                    TransferPartyLeaderResponse r = TransferPartyLeaderResponse.parseFrom(p);
                    return r.getOutcome().name() + " người nhận=" + r.getTargetName();
                });
    }

    private String petName(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_RENAME_PET_COMMAND_VALUE,
                RenamePetCommand.newBuilder().setName(name).build(),
                Opcode.OPCODE_RENAME_PET_RESPONSE_VALUE,
                p -> {
                    RenamePetResponse r = RenamePetResponse.parseFrom(p);
                    return r.getOutcome().name() + " tên=" + r.getName();
                });
    }

    private String enemies() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_ENEMIES_REQUEST_VALUE,
                ListEnemiesRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_ENEMIES_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (EnemyView enemy : ListEnemiesResponse.parseFrom(p).getEnemiesList()) {
                        out.append(enemy.getName())
                                .append(" x")
                                .append(enemy.getKillCount())
                                .append(" online=")
                                .append(enemy.getOnline())
                                .append("; ");
                    }
                    return out.toString();
                });
    }

    private String unenemy(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_REMOVE_ENEMY_COMMAND_VALUE,
                RemoveEnemyCommand.newBuilder().setName(name).build(),
                Opcode.OPCODE_REMOVE_ENEMY_RESPONSE_VALUE,
                p -> "removed=" + RemoveEnemyResponse.parseFrom(p).getRemoved());
    }

    private String watch(String arg) throws IOException {
        String[] parts = arg.split("=", 2);
        return asker.ask(
                Opcode.OPCODE_SET_MARKET_WATCH_COMMAND_VALUE,
                dev.kage.protocol.SetMarketWatchCommand.newBuilder()
                        .setItemTemplateId(Long.parseLong(parts[0]))
                        .setMaxUnitPrice(Long.parseLong(parts[1]))
                        .build(),
                Opcode.OPCODE_SET_MARKET_WATCH_RESPONSE_VALUE,
                p -> dev.kage.protocol.SetMarketWatchResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    /** Kho chung tài khoản (ADR-197) — tách vì trần CyclomaticComplexity của {@link #step}. */
    private String vaultStep(String verb, String arg) throws IOException {
        return switch (verb) {
            case "vault" -> vault();
            case "vaultDeposit" -> vaultMove(arg, true);
            case "vaultWithdraw" -> vaultMove(arg, false);
            default -> "bước không hiểu";
        };
    }

    private String vault() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_STORAGE_REQUEST_VALUE,
                dev.kage.protocol.ListStorageRequest.newBuilder()
                        .setStorageKind(dev.kage.protocol.StorageKind.STORAGE_KIND_ACCOUNT)
                        .build(),
                Opcode.OPCODE_LIST_STORAGE_RESPONSE_VALUE,
                p -> describeVault(
                        dev.kage.protocol.ListStorageResponse.parseFrom(p).getItemsList()));
    }

    /** {@code <id>:<số lượng>} — id món trong túi (gửi) hoặc id dòng kho (rút). */
    private String vaultMove(String arg, boolean deposit) throws IOException {
        String[] parts = arg.split(":", 2);
        long id = Long.parseLong(parts[0]);
        int quantity = Integer.parseInt(parts[1]);
        if (deposit) {
            return asker.ask(
                    Opcode.OPCODE_DEPOSIT_ITEM_COMMAND_VALUE,
                    dev.kage.protocol.DepositItemCommand.newBuilder()
                            .setInventoryItemId(id)
                            .setQuantity(quantity)
                            .setStorageKind(dev.kage.protocol.StorageKind.STORAGE_KIND_ACCOUNT)
                            .build(),
                    Opcode.OPCODE_DEPOSIT_ITEM_RESPONSE_VALUE,
                    p -> {
                        dev.kage.protocol.DepositItemResponse r = dev.kage.protocol.DepositItemResponse.parseFrom(p);
                        return r.getOutcome().name() + " kho=" + describeVault(r.getItemsList());
                    });
        }
        return asker.ask(
                Opcode.OPCODE_WITHDRAW_ITEM_COMMAND_VALUE,
                dev.kage.protocol.WithdrawItemCommand.newBuilder()
                        .setStoredItemId(id)
                        .setQuantity(quantity)
                        .setStorageKind(dev.kage.protocol.StorageKind.STORAGE_KIND_ACCOUNT)
                        .build(),
                Opcode.OPCODE_WITHDRAW_ITEM_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.WithdrawItemResponse r = dev.kage.protocol.WithdrawItemResponse.parseFrom(p);
                    return r.getOutcome().name() + " kho=" + describeVault(r.getItemsList());
                });
    }

    private static String describeVault(java.util.List<dev.kage.protocol.StoredItemView> items) {
        StringBuilder out = new StringBuilder();
        for (dev.kage.protocol.StoredItemView item : items) {
            out.append('#')
                    .append(item.getStoredItemId())
                    .append(" tpl")
                    .append(item.getItemTemplateId())
                    .append('x')
                    .append(item.getQuantity())
                    .append(' ');
        }
        return out.toString();
    }
}
