package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.CharacterSummary;

/**
 * {@code --createCharacter=true} — kiểm sống chuỗi quest từ một nhân vật Cấp 1 mới tinh (2026-10-03)
 * chạy hàng chục lượt probe nối nhau trên CÙNG một nhân vật. Lượt đầu phải tạo, mọi lượt sau phải
 * dùng lại — tạo thêm một nhân vật cùng tên ở lượt hai là làm hỏng cả chuỗi đo.
 */
final class GameProbeCharacterCreationTest {

    private static final CharacterSummary EXISTING =
            CharacterSummary.newBuilder().setId(42L).setName("questrun").build();

    @Test
    void shouldNotCreate_whenFlagAbsent() throws IOException {
        ProbeConfig config = ProbeConfig.parse(new String[] {"--characterName=fresh"});

        assertThat(config.createCharacter()).isFalse();
        assertThat(GameProbe.needsCreation(List.of(), config)).isFalse();
    }

    @Test
    void shouldCreate_whenFlagSetAndNoCharacterHasThatName() throws IOException {
        ProbeConfig config = ProbeConfig.parse(new String[] {"--characterName=fresh", "--createCharacter=true"});

        assertThat(GameProbe.needsCreation(List.of(EXISTING), config)).isTrue();
    }

    @Test
    void shouldReuse_whenCharacterWithThatNameAlreadyExists() throws IOException {
        ProbeConfig config = ProbeConfig.parse(new String[] {"--characterName=questrun", "--createCharacter=true"});

        assertThat(GameProbe.needsCreation(List.of(EXISTING), config)).isFalse();
    }

    @Test
    void shouldRefuse_whenFlagSetWithoutName() {
        ProbeConfig config = ProbeConfig.parse(new String[] {"--createCharacter=true"});

        assertThatThrownBy(() -> GameProbe.needsCreation(List.of(), config))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("--characterName");
    }
}
