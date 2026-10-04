package dev.kage.client.core.world;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.Set;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Môi trường libGDX GIẢ đủ để dựng {@link WorldScreen} THẬT không cần cửa sổ hay GPU: GL trả số
 * trung tính cho mọi lệnh (riêng biên dịch shader thì "thành công" để {@code SpriteBatch} chịu dựng),
 * màn hình 1280x720, input không phím nào bấm, file đọc qua {@link Lwjgl3Files} (rơi về classpath —
 * đúng đường font và bundle i18n đang đi).
 *
 * <p>Không thay thế được GPU thật: mọi lệnh vẽ là no-op. Đủ cho câu hỏi "gói đến có làm client chết
 * hay đổ rác vào trạng thái hiển thị không", không đủ cho câu hỏi "vẽ ra trông thế nào".
 */
final class HeadlessGdx {

    static final int SCREEN_WIDTH = 1280;
    static final int SCREEN_HEIGHT = 720;
    private static final float FRAME_SECONDS = 1f / 60f;
    private static final int TEXTURE_UNITS = 16;
    private static final int NON_ZERO_HANDLE = 1;
    private static final Set<String> HANDLE_CALLS = Set.of(
            "glCreateShader",
            "glCreateProgram",
            "glGenBuffer",
            "glGenTexture",
            "glGenFramebuffer",
            "glGenRenderbuffer");
    private static final Set<String> INFO_LOG_CALLS = Set.of("glGetShaderInfoLog", "glGetProgramInfoLog");

    /** {@code InputProcessor} mà màn hình vừa cài qua {@code Gdx.input.setInputProcessor} — để test bấm phím/click. */
    static InputProcessor capturedInputProcessor;

    private static GL20 previousGl;
    private static GL20 previousGl20;
    private static Graphics previousGraphics;
    private static Files previousFiles;
    private static Application previousApp;
    private static Input previousInput;

    private HeadlessGdx() {}

    static void install() {
        GdxNativesLoader.load();
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGraphics = Gdx.graphics;
        previousFiles = Gdx.files;
        previousApp = Gdx.app;
        previousInput = Gdx.input;
        GL20 gl = proxy(GL20.class, HeadlessGdx::invokeGl);
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.graphics = proxy(Graphics.class, HeadlessGdx::invokeGraphics);
        Gdx.files = new Lwjgl3Files();
        Gdx.app = proxy(Application.class, HeadlessGdx::invokeNeutral);
        Gdx.input = proxy(Input.class, HeadlessGdx::invokeInput);
    }

    static void forgetInputProcessor() {
        capturedInputProcessor = null;
    }

    static void uninstall() {
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.graphics = previousGraphics;
        Gdx.files = previousFiles;
        Gdx.app = previousApp;
        Gdx.input = previousInput;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object invokeGl(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if (HANDLE_CALLS.contains(name)) {
            return NON_ZERO_HANDLE; // 0 là "tạo thất bại" — SpriteBatch/ShaderProgram sẽ ném.
        }
        if (INFO_LOG_CALLS.contains(name)) {
            return "";
        }
        if ("glGetIntegerv".equals(name)) {
            ((IntBuffer) args[1]).put(0, TEXTURE_UNITS);
        } else if ("glGetShaderiv".equals(name) || "glGetProgramiv".equals(name)) {
            // Biên dịch/link "thành công"; mọi truy vấn khác (số attribute/uniform...) là 0.
            int pname = (Integer) args[1];
            boolean ok = pname == GL20.GL_COMPILE_STATUS || pname == GL20.GL_LINK_STATUS;
            ((IntBuffer) args[2]).put(0, ok ? 1 : 0);
        }
        return neutral(method.getReturnType());
    }

    private static Object invokeGraphics(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "getWidth", "getBackBufferWidth" -> SCREEN_WIDTH;
            case "getHeight", "getBackBufferHeight" -> SCREEN_HEIGHT;
            case "getDeltaTime" -> FRAME_SECONDS;
            case "getDensity", "getBackBufferScale" -> 1f;
            default -> neutral(method.getReturnType());
        };
    }

    private static Object invokeInput(Object proxy, Method method, Object[] args) {
        if ("setInputProcessor".equals(method.getName())) {
            capturedInputProcessor = (InputProcessor) args[0];
        }
        return neutral(method.getReturnType());
    }

    private static Object invokeNeutral(Object proxy, Method method, Object[] args) {
        return neutral(method.getReturnType());
    }

    private static Object neutral(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }
}
