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

    public static BitmapFont createDefaultFont() {
        Gdx.app.log("FontProvider", "使用 LibGDX 默认 BitmapFont 作为兜底字体");
        return new BitmapFont();
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
            param.incremental = false;
            // NOTE: 非增量模式下，需要一次性生成所有可能出现的字符。
            // 这里从 assets/i18n/strings_*.properties 汇总字符集，避免手工维护白名单。
            param.characters = buildCharactersFromI18n();

            BitmapFont font = generator.generateFont(param);
            Gdx.app.log("FontProvider", "TTF 字体生成成功: " + ttfInternalPath + " (characters 从 i18n 汇总)");
            return font;
        } catch (Throwable t) {
            Gdx.app.error("FontProvider", "TTF 字体生成失败: " + ttfInternalPath, t);
            return null;
        } finally {
            generator.dispose();
        }
    }

    private static String buildCharactersFromI18n() {
        // NOTE: 基础字符集（控制符号/常用 ASCII），避免某些 UI 文本不是 i18n 时缺字。
        String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
                "-()（）/\\ _" +
                ">v≡";

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
        // 由于 :lwjgl3:run 的 workingDir 为 assets/，这里用 ../gamedata/mods 定位项目根下的 mod 目录。
        FileHandle modsDir = Gdx.files.local("../gamedata/mods/");
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
