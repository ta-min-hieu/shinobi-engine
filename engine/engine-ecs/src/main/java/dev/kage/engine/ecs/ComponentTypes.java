package dev.kage.engine.ecs;

/**
 * Đăng ký TRƯỚC các kiểu Component vào sổ {@code ComponentType} tĩnh của Ashley (dòng 73(d), chủ dự án
 * duyệt 2026-10-17).
 *
 * <p><b>Vì sao.</b> Sổ đó là một {@code ObjectMap} tĩnh không đồng bộ, và Ashley GHI vào nó lần đầu gặp
 * một kiểu — kể cả từ {@code Entity.add}, tức từ tick thread của bất kỳ World nào. Server chạy nhiều
 * World trên nhiều luồng (world chính, hầm ngục, đấu trường): một kiểu được gặp lần đầu ở World này trong
 * lúc World kia đang tra sổ là một lần đọc-ghi đua nhau trên một bảng băm. Chưa lộ chỉ vì mọi kiểu đều
 * được World ĐẦU TIÊN đăng ký trên một luồng. Đăng ký hết lúc khởi động thì về sau sổ chỉ còn ĐỌC — an
 * toàn đa luồng.
 *
 * <p>Gọi nhiều lần vô hại (Ashley trả lại kiểu đã có).
 */
public final class ComponentTypes {

    private ComponentTypes() {}

    // "varargs": chỉ đọc mảng varargs — không ghi gì vào nó, không có heap pollution.
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static void register(Class<? extends Component>... types) {
        AshleyStatics.registerTypes(types);
    }
}
