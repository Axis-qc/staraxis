package staraxis.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * UI Skin 统一加载入口（放在 ui 模块内）。
 *
 * 目标：
 * - client 只负责调用这里获取 Skin，并注入到 Gui。
 * - 在这里完成 png/.9.png 的注册。
 */
public final class UiSkinLoader {

    private UiSkinLoader() {
    }

    private static class NinePatchSpec {
        final int left;
        final int right;
        final int top;
        final int bottom;
        final int padLeft;
        final int padRight;
        final int padTop;
        final int padBottom;

        NinePatchSpec(int left, int right, int top, int bottom, int padLeft, int padRight, int padTop, int padBottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.padLeft = padLeft;
            this.padRight = padRight;
            this.padTop = padTop;
            this.padBottom = padBottom;
        }
    }

    /**
     * 标准 9patch 解析：
     * - 顶边 (y=0)：水平 stretch 区
     * - 左边 (x=0)：垂直 stretch 区
     * - 底边 (y=h-1)：padding 水平区（可选）
     * - 右边 (x=w-1)：padding 垂直区（可选）
     */
    private static NinePatchSpec parseNinePatchSpec(Pixmap src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        final int iw = w - 2;
        final int ih = h - 2;

        int topStart = -1, topEnd = -1;
        for (int x = 1; x < w - 1; x++) {
            int a = (src.getPixel(x, 0) >>> 24) & 0xff;
            if (a > 0) {
                if (topStart < 0)
                    topStart = x;
                topEnd = x;
            }
        }

        int leftStart = -1, leftEnd = -1;
        for (int y = 1; y < h - 1; y++) {
            int a = (src.getPixel(0, y) >>> 24) & 0xff;
            if (a > 0) {
                if (leftStart < 0)
                    leftStart = y;
                leftEnd = y;
            }
        }

        if (topStart < 0 || leftStart < 0) {
            return null;
        }

        // 转为 inner 坐标（去掉1px边框）
        int stretchXStart = topStart - 1;
        int stretchXEnd = topEnd - 1;

        // Pixmap: y=0 在顶部；NinePatch: bottom 从底部算
        int innerYStart = (h - leftEnd - 1);
        int innerYEnd = (h - leftStart - 1);

        int stretchYStart = innerYStart;
        int stretchYEnd = innerYEnd;

        int left = stretchXStart;
        int right = iw - (stretchXEnd + 1);
        int bottom = stretchYStart;
        int top = ih - (stretchYEnd + 1);

        if (left < 0 || right < 0 || top < 0 || bottom < 0)
            return null;

        // padding（可选）
        int padLeft = 0, padRight = 0, padTop = 0, padBottom = 0;

        int bottomStart = -1, bottomEnd = -1;
        for (int x = 1; x < w - 1; x++) {
            int a = (src.getPixel(x, h - 1) >>> 24) & 0xff;
            if (a > 0) {
                if (bottomStart < 0)
                    bottomStart = x;
                bottomEnd = x;
            }
        }
        if (bottomStart >= 0) {
            int pStart = bottomStart - 1;
            int pEnd = bottomEnd - 1;
            padLeft = pStart;
            padRight = iw - (pEnd + 1);
        }

        int rightStart = -1, rightEnd = -1;
        for (int y = 1; y < h - 1; y++) {
            int a = (src.getPixel(w - 1, y) >>> 24) & 0xff;
            if (a > 0) {
                if (rightStart < 0)
                    rightStart = y;
                rightEnd = y;
            }
        }
        if (rightStart >= 0) {
            int pInnerYStart = (h - rightEnd - 1);
            int pInnerYEnd = (h - rightStart - 1);
            padBottom = pInnerYStart;
            padTop = ih - (pInnerYEnd + 1);
        }

        // 如果没有 padding 标记，则按 stretch 区域作为内容 padding
        if (bottomStart < 0) {
            padLeft = left;
            padRight = right;
        }
        if (rightStart < 0) {
            padTop = top;
            padBottom = bottom;
        }

        return new NinePatchSpec(left, right, top, bottom, padLeft, padRight, padTop, padBottom);
    }

    /**
     * 加载默认 Skin。
     *
     * @param skinJsonPath 例如 "ui/uiskin/uiskin.json"
     */
    public static Skin loadDefault(String skinJsonPath) {
        Skin skin = new Skin(Gdx.files.internal(skinJsonPath));

        // 普通 png（可选）
        registerPngDrawables(skin, "ui/uiskin/png", "ui/uiskin/PNG");

        // 9patch
        registerNinePatchDrawables(skin, "ui/uiskin/9png");

        return skin;
    }

    private static void registerPngDrawables(Skin skin, String... internalDirs) {
        for (String d : internalDirs) {
            if (d == null || d.isBlank())
                continue;
            FileHandle dir = Gdx.files.internal(d);
            if (!dir.exists() || !dir.isDirectory())
                continue;
            for (FileHandle fh : dir.list()) {
                registerPngRecursive(skin, fh);
            }
        }
    }

    private static void registerPngRecursive(Skin skin, FileHandle fh) {
        if (fh.isDirectory()) {
            for (FileHandle c : fh.list()) {
                registerPngRecursive(skin, c);
            }
            return;
        }
        if (!"png".equalsIgnoreCase(fh.extension()))
            return;

        String name = fh.nameWithoutExtension();
        if (name == null || name.isBlank())
            return;

        // 9png 在另一个流程里注册；这里跳过避免覆盖
        if (name.endsWith(".9"))
            return;

        if (skin.has(name, Drawable.class))
            return;

        Texture tex = new Texture(fh);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        skin.add(name, tex, Texture.class);
        skin.add(name, new TextureRegionDrawable(new TextureRegion(tex)), Drawable.class);
    }

    private static void registerNinePatchDrawables(Skin skin, String internalDir) {
        FileHandle dir = Gdx.files.internal(internalDir);
        if (!dir.exists() || !dir.isDirectory()) {
            Gdx.app.log("UiSkinLoader", "9png dir not found: " + internalDir);
            return;
        }

        for (FileHandle fh : dir.list()) {
            if (fh.isDirectory())
                continue;
            if (!"png".equalsIgnoreCase(fh.extension()))
                continue;

            String name = fh.nameWithoutExtension();
            if (name == null || !name.endsWith(".9"))
                continue;

            String drawableName = name.substring(0, name.length() - 2);
            if (skin.has(drawableName, Drawable.class))
                continue;

            try {
                NinePatch patch = loadNinePatch(fh);
                if (patch == null)
                    continue;

                skin.add(drawableName, patch.getTexture(), Texture.class);
                skin.add(drawableName, new NinePatchDrawable(patch), Drawable.class);
                Gdx.app.log("UiSkinLoader", "Registered 9patch drawable: " + drawableName);
            } catch (Exception e) {
                Gdx.app.error("UiSkinLoader", "Failed to register 9patch: " + fh.path(), e);
            }
        }
    }

    private static NinePatch loadNinePatch(FileHandle ninePngFile) {
        Pixmap src = null;
        Pixmap inner = null;
        try {
            src = new Pixmap(ninePngFile);
            int w = src.getWidth();
            int h = src.getHeight();
            if (w < 3 || h < 3)
                return null;

            NinePatchSpec spec = parseNinePatchSpec(src);
            if (spec == null)
                return null;

            int iw = w - 2;
            int ih = h - 2;

            // 裁掉 1px 黑线边框，避免黑线被渲染
            inner = new Pixmap(iw, ih, src.getFormat());
            inner.drawPixmap(src, 0, 0, 1, 1, iw, ih);

            Texture tex = new Texture(inner);
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

            TextureRegion tr = new TextureRegion(tex);
            NinePatch patch = new NinePatch(tr, spec.left, spec.right, spec.top, spec.bottom);
            patch.setPadding(spec.padLeft, spec.padRight, spec.padTop, spec.padBottom);
            return patch;
        } finally {
            if (src != null)
                src.dispose();
            if (inner != null)
                inner.dispose();
        }
    }
}
