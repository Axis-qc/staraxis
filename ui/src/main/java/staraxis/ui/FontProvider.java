package staraxis.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

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
            param.characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-()（）/\\ _" +
                    "新游戏加载游戏多人游戏未来规划舰船设计器设置退出游戏开发中提示确定StarAxis";

            BitmapFont font = generator.generateFont(param);
            Gdx.app.log("FontProvider", "TTF 字体生成成功: " + ttfInternalPath + " (characters 已指定)");
            return font;
        } catch (Throwable t) {
            Gdx.app.error("FontProvider", "TTF 字体生成失败: " + ttfInternalPath, t);
            return null;
        } finally {
            generator.dispose();
        }
    }
}
