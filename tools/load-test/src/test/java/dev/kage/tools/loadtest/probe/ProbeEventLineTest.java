package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Định dạng dòng — thuần logic, không cần server. Frame dựng bằng chính {@link PacketCodec} thật
 * (encode rồi decode) chứ không bằng mock, nên test này cũng canh luôn việc probe đọc đúng thứ
 * server ghi ra.
 */
final class ProbeEventLineTest {

    private static PacketCodec.Frame frameOf(int opcode, byte[] payload) {
        return PacketCodec.decodeFrame(
                PacketCodec.encodeFrame(new PacketHeader(opcode, ProtocolVersion.CURRENT, 1), payload));
    }

    @Test
    void shouldDecodeMonsterSpawn_intoTabSeparatedFields() throws InvalidProtocolBufferException {
        MonsterSpawnedEvent event = MonsterSpawnedEvent.newBuilder()
                .setMonsterId(-3L)
                .setMonsterCode("wandering_bandit")
                .setModelKey("mob_wandering-bandit")
                .setX(21f)
                .setY(0f)
                .setCurrentHp(120)
                .setMaxHp(150)
                .build();

        String line =
                ProbeEventLine.format(1234L, frameOf(Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, event.toByteArray()));

        assertThat(line.split("\t"))
                .containsExactly(
                        "1234",
                        "402",
                        "MONSTER_SPAWNED_EVENT",
                        "monsterId=-3 code=wandering_bandit model=mob_wandering-bandit x=21.00 y=0.00 hp=120/150");
    }

    @Test
    void shouldDecodeAttackResult_withDamageAndRemainingHp() throws InvalidProtocolBufferException {
        AttackResultEvent event = AttackResultEvent.newBuilder()
                .setAttackerEntityId(3L)
                .setTargetEntityId(-3L)
                .setDamage(17)
                .setIsCrit(true)
                .setIsMiss(false)
                .setTargetCurrentHp(103)
                .setTargetDied(false)
                .build();

        String line =
                ProbeEventLine.format(1500L, frameOf(Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE, event.toByteArray()));

        assertThat(line)
                .isEqualTo("1500\t401\tATTACK_RESULT_EVENT\t"
                        + "attacker=3 target=-3 damage=17 crit=true miss=false targetHp=103 targetDied=false");
    }

    /** Opcode chưa giải mã vẫn phải hiện ra — im lặng bỏ qua là cách chắc chắn để bỏ sót bằng chứng. */
    @Test
    void shouldStillEmitLine_forOpcodeWithoutDecoder() throws InvalidProtocolBufferException {
        String line =
                ProbeEventLine.format(7L, frameOf(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, new byte[] {1, 2, 3}));

        assertThat(line).isEqualTo("7\t508\tLIST_INVENTORY_RESPONSE\tpayloadBytes=3");
    }

    @Test
    void shouldNameUnknownOpcode_insteadOfCrashing() {
        assertThat(ProbeEventLine.name(64000)).isEqualTo("UNKNOWN");
    }

    /**
     * Máy dev của dự án chạy locale Việt Nam — {@code %.2f} mặc định in "24,00" và dấu phẩy phá
     * luôn định dạng dòng. Test khoá lại Locale.ROOT bằng cách đổi locale mặc định trong lúc chạy.
     */
    @Test
    void shouldFormatCoordinatesWithDot_evenUnderCommaDecimalLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("vi-VN"));
            assertThat(ProbeEventLine.meters(24f)).isEqualTo("24.00");
        } finally {
            Locale.setDefault(original);
        }
    }
}
