package staraxis.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public final class FontProvider {

    private FontProvider() {
    }

    // 字体分辨率倍率：生成位图时使用逻辑大小 * 此倍率，提升纹理清晰度
    public static final float RESOLUTION_MULTIPLIER = 2.0f;

    // 逻辑字体大小
    public static final int UI_FONT_SIZE = 28;
    public static final int VECTOR_FONT_SIZE = 96;
    public static final int HUD_FONT_SIZE = 20;

    // 矢量字体实际生成大小（供外部缩放计算使用）
    public static final float VECTOR_FONT_GEN_SIZE = VECTOR_FONT_SIZE * RESOLUTION_MULTIPLIER;

    // 字体文件路径
    public static final String FONT_PATH = "fonts/chinese/AlibabaPuHuiTi-3-65-Medium.ttf";
    public static final String HUD_FONT_PATH = "fonts/chinese/Alibaba-PuHuiTi-H.ttf";

    private static final java.util.List<FreeTypeFontGenerator> incrementalGenerators = new java.util.ArrayList<>();

    public static void disposeAllIncremental() {
        for (FreeTypeFontGenerator g : incrementalGenerators) {
            try { g.dispose(); } catch (Exception ignored) {}
        }
        incrementalGenerators.clear();
    }

    public static BitmapFont createDefaultFont() {
        Gdx.app.log("FontProvider", "使用 LibGDX 默认 BitmapFont 作为兜底字体");
        return new BitmapFont();
    }

    public static BitmapFont createUiFont() {
        int genSize = Math.round(UI_FONT_SIZE * RESOLUTION_MULTIPLIER);
        BitmapFont font = tryCreateFontFromTtfOrNull(FONT_PATH, genSize);
        if (font != null) {
            applyHiDpiScale(font);
            return font;
        }
        return createDefaultFont();
    }

    public static BitmapFont createVectorFont() {
        int genSize = Math.round(VECTOR_FONT_SIZE * RESOLUTION_MULTIPLIER);
        BitmapFont font = tryCreateFontFromTtfOrNull(FONT_PATH, genSize);
        if (font != null) {
            return font;
        }
        return createDefaultFont();
    }

    public static BitmapFont createHudFont() {
        int genSize = Math.round(HUD_FONT_SIZE * RESOLUTION_MULTIPLIER);
        BitmapFont font = tryCreateFontFromTtfOrNull(HUD_FONT_PATH, genSize);
        if (font != null) {
            applyHiDpiScale(font);
            return font;
        }
        return createDefaultFont();
    }

    private static void applyHiDpiScale(BitmapFont font) {
        float invScale = 1.0f / RESOLUTION_MULTIPLIER;
        font.getData().setScale(invScale);
    }

    public static BitmapFont tryCreateFontFromTtfOrNull(String ttfInternalPath, int size) {
        FileHandle fontFile;
        try {
            fontFile = Gdx.files.internal(ttfInternalPath);
        } catch (Throwable t) {
            Gdx.app.error("FontProvider", "获取 TTF 文件句柄失败: " + ttfInternalPath, t);
            return null;
        }

        if (fontFile == null || !fontFile.exists()) {
            Gdx.app.error("FontProvider", "TTF 文件不存在: " + ttfInternalPath, null);
            return null;
        }

        Gdx.app.log("FontProvider", "尝试从 TTF 生成字体: " + fontFile.path() + ", size=" + size);

        FreeTypeFontGenerator generator;
        try {
            generator = new FreeTypeFontGenerator(fontFile);
        } catch (Throwable t) {
            Gdx.app.error("FontProvider", "创建 FreeTypeFontGenerator 失败", t);
            return null;
        }

        try {
            FreeTypeFontGenerator.FreeTypeFontParameter param = new FreeTypeFontGenerator.FreeTypeFontParameter();
            param.size = size;
            param.incremental = true;
            param.characters = buildCharactersFromI18n();
            param.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
            param.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;

            BitmapFont font = generator.generateFont(param);
            incrementalGenerators.add(generator);
            Gdx.app.log("FontProvider", "TTF 字体生成成功: " + ttfInternalPath + " (增量模式)");
            return font;
        } catch (Throwable t) {
            generator.dispose();
            Gdx.app.error("FontProvider", "TTF 字体生成失败: " + ttfInternalPath, t);
            return null;
        }
    }

    private static String buildCharactersFromI18n() {
        // NOTE: 基础字符集（控制符号/常用 ASCII），避免某些 UI 文本不是 i18n 时缺字。
        String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
                "-()（）/\\ _:：" +
                ">v≡" +
                "星轴原生客户端本机世界区数实体舰队图菜单平移缩放" +
                "资产总览日期设施列表点击项聚焦左键拖动已选中当前未选择框选双击右键下令舰船信息状态待机关闭空白处下达移动指令开发军事科技内政外交暂无开始游戏";

        Set<Character> chars = new LinkedHashSet<>();
        for (int i = 0; i < base.length(); i++) {
            chars.add(base.charAt(i));
        }

        FileHandle dir = Gdx.files.internal("i18n");
        if (dir.exists() && dir.isDirectory()) {
            for (FileHandle f : dir.list()) {
                String name = f.name();
                if (!name.startsWith("strings_") || !name.endsWith(".properties")) {
                    continue;
                }
                mergePropertiesValueChars(chars, f);
            }
        }

        // NOTE: 同时扫描 mod 的 i18n，确保字体位图包含 mod 文本需要的字符，避免运行时缺字。
        FileHandle modsDir = Gdx.files.local("gamedata/mods/");
        if (modsDir.exists() && modsDir.isDirectory()) {
            FileHandle[] modDirs = modsDir.list();
            java.util.Arrays.sort(modDirs, java.util.Comparator.comparing(FileHandle::name));

            for (FileHandle modDir : modDirs) {
                if (!modDir.isDirectory()) {
                    continue;
                }

                FileHandle modI18nDir = modDir.child("i18n");
                if (!modI18nDir.exists() || !modI18nDir.isDirectory()) {
                    continue;
                }

                for (FileHandle f : modI18nDir.list()) {
                    String name = f.name();
                    if (!name.startsWith("strings_") || !name.endsWith(".properties")) {
                        continue;
                    }
                    mergePropertiesValueChars(chars, f);
                }
            }
        }

        return toString(chars);
    }

    private static void mergePropertiesValueChars(Set<Character> chars, FileHandle fileHandle) {
        Properties p = new Properties();
        try (Reader reader = fileHandle.reader("UTF-8")) {
            p.load(reader);
        } catch (IOException e) {
            Gdx.app.error("FontProvider", "读取 i18n 文件失败: " + fileHandle.path(), e);
            return;
        }

        for (Object v : p.values()) {
            if (v == null)
                continue;
            String s = v.toString();
            for (int i = 0; i < s.length(); i++) {
                chars.add(s.charAt(i));
            }
        }
    }

    private static String toString(Set<Character> chars) {
        StringBuilder sb = new StringBuilder(chars.size());
        for (Character c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
