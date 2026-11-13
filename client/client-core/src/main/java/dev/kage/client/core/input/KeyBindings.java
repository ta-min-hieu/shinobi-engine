package dev.kage.client.core.input;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.badlogic.gdx.Input.Keys;

/**
 * Đổi phím tắt (ADR-190) — mỗi phím mặc định (mở cửa sổ, di chuyển, đánh...) ánh xạ sang phím người chơi chọn. Lưu trên
 * MÁY người chơi (không phải dữ liệu gameplay, như {@code DisplaySettings}). Chỗ nào so phím thì hỏi
 * {@link #resolve(int)} với phím MẶC ĐỊNH của mình — đọc một ô mảng, không cấp phát (gọi được mỗi khung).
 */
public final class KeyBindings {

    /** Mọi phím đổi được: tên ngắn (dùng trong lệnh chat) + phím mặc định. */
    public enum Binding {
        INVENTORY("tuido", Keys.I),
        QUEST_LOG("nhiemvu", Keys.J),
        MAIL("thu", Keys.M),
        FRIENDS("banbe", Keys.F),
        GUILD("bang", Keys.G),
        PARTY("nhom", Keys.P),
        SHOP("cuahang", Keys.B),
        TRADE("giaodich", Keys.N),
        DUEL("luanvo", Keys.K),
        FACTION("linhcan", Keys.H),
        CHAT("chat", Keys.T),
        RELATIONS("quanhe", Keys.Q),
        DAILY("hangngay", Keys.E),
        BESTIARY("dodien", Keys.Y),
        REFINE("tinhluyen", Keys.X),
        CHARACTER("nhanvat", Keys.C),
        LEADERBOARD("bxh", Keys.L),
        ACHIEVEMENTS("thanhtuu", Keys.U),
        MARKET("phuongthi", Keys.O),
        GEMS("linhngoc", Keys.V),
        SKILL_RANKS("congphap", Keys.R),
        MOVE_LEFT("trai", Keys.A),
        MOVE_RIGHT("phai", Keys.D),
        JUMP("nhay", Keys.W),
        USE_SKILL("kynang", Keys.ENTER),
        USE_ARTIFACT("phapbao", Keys.NUM_1),
        ATTACK("danh", Keys.Z),
        /** ADR-333 — ẩn / hiện khung chat. */
        HIDE_CHAT("anchat", Keys.S);

        private final String id;
        private final int defaultKey;

        Binding(String id, int defaultKey) {
            this.id = id;
            this.defaultKey = defaultKey;
        }

        /** Tên ngắn trong lệnh chat; cũng là đuôi khoá i18n {@code keybind.<id>}. */
        public String id() {
            return id;
        }

        /** Phím mặc định. */
        public int defaultKey() {
            return defaultKey;
        }

        /** Tra theo tên ngắn (không phân biệt hoa thường). */
        public static Optional<Binding> byId(String id) {
            String wanted = id.toLowerCase(Locale.ROOT);
            for (Binding binding : values()) {
                if (binding.id.equals(wanted)) {
                    return Optional.of(binding);
                }
            }
            return Optional.empty();
        }
    }

    /** Kết quả đổi phím. */
    public enum Rebind {
        OK,
        /** Phím đó đang là phím của một hành động khác. */
        KEY_IN_USE,
        /** Phím dành riêng (Esc, mũi tên, Space...) — không cho gán. */
        RESERVED,
        /** Không phải phím libGDX biết. */
        UNKNOWN_KEY
    }

    /** Nơi lưu — tách port để test không cần libGDX. */
    public interface Store {
        /** Phím đã lưu cho một hành động, rỗng nếu chưa đổi. */
        OptionalInt read(String key);

        /** Ghi (KHÔNG ném khi nơi lưu hỏng — giá trị vẫn có hiệu lực trong phiên). */
        void write(String key, int value);

        /** Xoá (về mặc định). */
        void remove(String key);
    }

    /** Phím luôn giữ nguyên nghĩa (phím phụ của di chuyển/đánh, Esc đóng cửa sổ...) — không gán được. */
    private static final Set<Integer> RESERVED_KEYS = Set.of(
            Keys.ESCAPE,
            Keys.LEFT,
            Keys.RIGHT,
            Keys.UP,
            Keys.DOWN,
            Keys.SPACE,
            Keys.NUMPAD_ENTER,
            Keys.NUMPAD_1,
            Keys.CONTROL_LEFT,
            Keys.CONTROL_RIGHT,
            Keys.SHIFT_LEFT,
            Keys.SHIFT_RIGHT,
            Keys.TAB,
            Keys.BACKSPACE);

    private static final int KEY_SPACE = 256;
    private static final String PREFIX = "keybind.";

    private static volatile KeyBindings shared = new KeyBindings(new MemoryStore());

    private final Store store;
    private final Map<Binding, Integer> current = new EnumMap<>(Binding.class);
    /** {@code remap[phím mặc định] = phím đang dùng} — đọc mỗi khung, không cấp phát. */
    private final int[] remap = new int[KEY_SPACE];

    private KeyBindings(Store store) {
        this.store = store;
        for (int key = 0; key < KEY_SPACE; key++) {
            remap[key] = key;
        }
        for (Binding binding : Binding.values()) {
            int key = store.read(PREFIX + binding.id).orElse(binding.defaultKey);
            apply(binding, isValid(key) ? key : binding.defaultKey);
        }
    }

    /** Đọc từ nơi lưu (giá trị hỏng bị bỏ, về mặc định). */
    public static KeyBindings load(Store store) {
        return new KeyBindings(store);
    }

    /** Bộ phím dùng chung cả client (mặc định: phím gốc, chưa lưu gì). */
    public static KeyBindings shared() {
        return shared;
    }

    /** Lắp bộ phím đã đọc từ máy (lúc vào game). */
    public static void install(KeyBindings bindings) {
        shared = bindings;
    }

    /** Phím đang dùng cho chỗ có phím MẶC ĐỊNH {@code defaultKey}; phím không đổi được thì trả nguyên. */
    public int resolve(int defaultKey) {
        return defaultKey >= 0 && defaultKey < KEY_SPACE ? remap[defaultKey] : defaultKey;
    }

    /** Phím đang dùng của một hành động. */
    public int keyOf(Binding binding) {
        return current.get(binding);
    }

    /** Gán {@code key} cho {@code binding} (và lưu). */
    public Rebind rebind(Binding binding, int key) {
        if (!isValid(key)) {
            return Rebind.UNKNOWN_KEY;
        }
        if (RESERVED_KEYS.contains(key)) {
            return Rebind.RESERVED;
        }
        for (Map.Entry<Binding, Integer> other : current.entrySet()) {
            if (other.getKey() != binding && other.getValue() == key) {
                return Rebind.KEY_IN_USE;
            }
        }
        apply(binding, key);
        if (key == binding.defaultKey) {
            store.remove(PREFIX + binding.id);
        } else {
            store.write(PREFIX + binding.id, key);
        }
        return Rebind.OK;
    }

    /** Mọi phím về mặc định (và xoá khỏi nơi lưu). */
    public void resetAll() {
        for (Binding binding : Binding.values()) {
            apply(binding, binding.defaultKey);
            store.remove(PREFIX + binding.id);
        }
    }

    private void apply(Binding binding, int key) {
        current.put(binding, key);
        remap[binding.defaultKey] = key;
    }

    private static boolean isValid(int key) {
        return key > 0 && key < KEY_SPACE;
    }

    /** Nơi lưu trong bộ nhớ — mặc định trước khi lắp bộ đọc từ máy, và cho test. */
    public static final class MemoryStore implements Store {
        private final Map<String, Integer> values = new java.util.HashMap<>();

        @Override
        public OptionalInt read(String key) {
            Integer value = values.get(key);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }

        @Override
        public void write(String key, int value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }
}
