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
 */
public final class UiSkinLoader {

    private UiSkinLoader() {
    }

    private static class NinePatchSpec {
        final int left, right, top, bottom;
        final int padLeft, padRight, padTop, padBottom;

        NinePatchSpec(int[] splits, int[] pads) {
            this.left = splits[0];
            this.right = splits[1];
            this.top = splits[2];
            this.bottom = splits[3];
            if (pads != null) {
                this.padLeft = pads[0];
                this.padRight = pads[1];
                this.padTop = pads[2];
                this.padBottom = pads[3];
            } else {
                this.padLeft = left;
                this.padRight = right;
                this.padTop = top;
                this.padBottom = bottom;
            }
        }
    }

    private static boolean isMarker(int pixel) {
        return (pixel & 0xff) != 0; // alpha != 0
    }

    private static NinePatchSpec parseNinePatchSpec(Pixmap pixmap, String name) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        int[] splits = new int[4];
        int[] pads = new int[4];
        boolean hasPads = false;

        // Top stretch
        int start = -1;
        for (int x = 1; x < width - 1; x++) {
            if (isMarker(pixmap.getPixel(x, 0))) {
                if (start == -1)
                    start = x - 1;
            } else if (start != -1) {
                splits[0] = start;
                splits[1] = (width - 2) - (x - 1);
                break;
            }
        }
        if (start == -1) {
            Gdx.app.error("UiSkinLoader", "9-patch parsing error: No top stretch markers found for " + name);
            return null;
        }

        // Left stretch
        start = -1;
        for (int y = 1; y < height - 1; y++) {
            if (isMarker(pixmap.getPixel(0, y))) {
                if (start == -1)
                    start = y - 1;
            } else if (start != -1) {
                splits[2] = start;
                splits[3] = (height - 2) - (y - 1);
                break;
            }
        }
        if (start == -1) {
            Gdx.app.error("UiSkinLoader", "9-patch parsing error: No left stretch markers found for " + name);
            return null;
        }

        // Bottom padding
        start = -1;
        for (int x = 1; x < width - 1; x++) {
            if (isMarker(pixmap.getPixel(x, height - 1))) {
                hasPads = true;
                if (start == -1)
                    start = x - 1;
            } else if (start != -1) {
                pads[0] = start;
                pads[1] = (width - 2) - (x - 1);
                break;
            }
        }

        // Right padding
        start = -1;
        for (int y = 1; y < height - 1; y++) {
            if (isMarker(pixmap.getPixel(width - 1, y))) {
                hasPads = true;
                if (start == -1)
                    start = y - 1;
            } else if (start != -1) {
                pads[2] = start;
                pads[3] = (height - 2) - (y - 1);
                break;
            }
        }

        return new NinePatchSpec(splits, hasPads ? pads : null);
    }

    public static Skin loadDefault(String skinJsonPath) {
        Skin skin = new Skin(Gdx.files.internal(skinJsonPath));
        registerProceduralDrawables(skin);
        registerNinePatchDrawables(skin, "ui/uiskin/9png");
        registerPngDrawables(skin, "ui/uiskin/png", "ui/uiskin/PNG");
        return skin;
    }

    private static void registerProceduralDrawables(Skin skin) {
        registerProcedural(skin, "white", createSolid(1, 1, 0xFFFFFFFF));
        registerProcedural(skin, "rect", createSolid(4, 4, 0xFFFFFFFF));
        registerProcedural(skin, "square", createSolid(4, 4, 0xFFFFFFFF));
        registerProcedural(skin, "dot", createCircle(8, 8, 0xFFFFFFFF));
        registerProcedural(skin, "line-h", createSolid(4, 1, 0xFFFFFFFF));
        registerProcedural(skin, "line-v", createSolid(1, 4, 0xFFFFFFFF));
        registerProcedural(skin, "knob-h", createSolid(8, 4, 0xFFFFFFFF));
        registerProcedural(skin, "knob-v", createSolid(4, 8, 0xFFFFFFFF));
        registerProcedural(skin, "check-on", createCheckOn(8, 8, 0xFFFFFFFF));
        registerProcedural(skin, "check", createCheckOutline(8, 8, 0xFFFFFFFF));
        registerProcedural(skin, "select", createSelectBox(8, 8, 0xFFFFFFFF));
        registerProcedural(skin, "tree-minus", createTreeMinus(8, 8, 0xFFFFFFFF));
        registerProcedural(skin, "tree-plus", createTreePlus(8, 8, 0xFFFFFFFF));
        registerProcedural(skin, "window-resize", createWindowResize(8, 8, 0xFFFFFFFF));
        registerProcedural(skin, "window-border", createWindowBorder(8, 8, 0xFFFFFFFF));

        registerProceduralNinePatch(skin, "Rounded_background", createRoundedRect(16, 16, 4, 0xFFFFFFFF), 4, 4, 4, 4);
    }

    private static void registerProcedural(Skin skin, String name, Pixmap pixmap) {
        if (skin.has(name, Drawable.class)) return;
        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        skin.add(name, tex, Texture.class);
        skin.add(name, new TextureRegionDrawable(new TextureRegion(tex)), Drawable.class);
        pixmap.dispose();
    }

    private static void registerProceduralNinePatch(Skin skin, String name, Pixmap pixmap, int l, int r, int t, int b) {
        if (skin.has(name, Drawable.class)) {
            pixmap.dispose();
            return;
        }
        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        NinePatch patch = new NinePatch(new TextureRegion(tex), l, r, t, b);
        skin.add(name, tex, Texture.class);
        skin.add(name, new NinePatchDrawable(patch), Drawable.class);
        pixmap.dispose();
    }

    private static Pixmap createSolid(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.fill();
        return p;
    }

    private static Pixmap createCircle(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.fillCircle(w / 2, h / 2, Math.min(w, h) / 2);
        return p;
    }

    private static Pixmap createCheckOn(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.fillRectangle(0, 0, w, h);
        p.setColor(0x00000000);
        p.fillRectangle(1, 1, w - 2, h - 2);
        p.setColor(rgba);
        p.drawLine(2, h / 2, w / 2 - 1, h - 3);
        p.drawLine(w / 2 - 1, h - 3, w - 3, 2);
        p.drawLine(2, h / 2 - 1, w / 2 - 1, h - 4);
        p.drawLine(w / 2, h - 3, w - 3, 1);
        return p;
    }

    private static Pixmap createCheckOutline(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.drawRectangle(0, 0, w, h);
        return p;
    }

    private static Pixmap createSelectBox(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.fillRectangle(0, 0, w, h);
        p.setColor(0x00000000);
        p.fillRectangle(1, 1, w - 2, h - 2);
        p.setColor(rgba);
        p.drawLine(w - 4, 2, w - 2, 4);
        p.drawLine(w - 5, 2, w - 2, 5);
        return p;
    }

    private static Pixmap createTreeMinus(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.drawRectangle(0, 0, w, h);
        p.fillRectangle(2, h / 2 - 1, w - 4, 2);
        return p;
    }

    private static Pixmap createTreePlus(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.drawRectangle(0, 0, w, h);
        p.fillRectangle(2, h / 2 - 1, w - 4, 2);
        p.fillRectangle(w / 2 - 1, 2, 2, h - 4);
        return p;
    }

    private static Pixmap createWindowResize(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        for (int i = 0; i < 3; i++) {
            int x = w - 2 - i * 3;
            int y = i * 3;
            p.drawLine(x, y, w - 1, y + (w - 1 - x));
        }
        return p;
    }

    private static Pixmap createWindowBorder(int w, int h, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.drawRectangle(0, 0, w, h);
        return p;
    }

    private static Pixmap createRoundedRect(int w, int h, int radius, int rgba) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.fillRectangle(radius, 0, w - radius * 2, h);
        p.fillRectangle(0, radius, w, h - radius * 2);
        p.fillCircle(radius, radius, radius);
        p.fillCircle(w - radius - 1, radius, radius);
        p.fillCircle(radius, h - radius - 1, radius);
        p.fillCircle(w - radius - 1, h - radius - 1, radius);
        return p;
    }

    private static void registerPngDrawables(Skin skin, String... internalDirs) {
        for (String d : internalDirs) {
            FileHandle dir = Gdx.files.internal(d);
            if (!dir.exists() || !dir.isDirectory())
                continue;
            for (FileHandle fh : dir.list())
                registerPngRecursive(skin, fh);
        }
    }

    private static void registerPngRecursive(Skin skin, FileHandle fh) {
        if (fh.isDirectory()) {
            for (FileHandle c : fh.list())
                registerPngRecursive(skin, c);
            return;
        }
        if (!"png".equalsIgnoreCase(fh.extension()) || fh.name().endsWith(".9.png"))
            return;
        String name = fh.nameWithoutExtension();
        if (skin.has(name, Drawable.class))
            return;

        Texture tex = new Texture(fh);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        skin.add(name, tex, Texture.class);
        skin.add(name, new TextureRegionDrawable(new TextureRegion(tex)), Drawable.class);
    }

    private static void registerNinePatchDrawables(Skin skin, String internalDir) {
        FileHandle dir = Gdx.files.internal(internalDir);
        if (!dir.exists() || !dir.isDirectory())
            return;

        for (FileHandle fh : dir.list()) {
            if (fh.isDirectory() || !fh.name().endsWith(".9.png"))
                continue;
            String drawableName = fh.name().substring(0, fh.name().length() - 6);
            if (skin.has(drawableName, Drawable.class))
                continue;

            Pixmap src = null;
            Pixmap inner = null;
            try {
                src = new Pixmap(fh);
                NinePatchSpec spec = parseNinePatchSpec(src, fh.name());
                if (spec == null)
                    continue;

                int iw = src.getWidth() - 2;
                int ih = src.getHeight() - 2;
                inner = new Pixmap(iw, ih, src.getFormat());
                inner.drawPixmap(src, 0, 0, 1, 1, iw, ih);

                Texture tex = new Texture(inner);
                tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

                NinePatch patch = new NinePatch(new TextureRegion(tex), spec.left, spec.right, spec.top, spec.bottom);
                patch.setPadding(spec.padLeft, spec.padRight, spec.padTop, spec.padBottom);

                skin.add(drawableName, tex, Texture.class);
                skin.add(drawableName, new NinePatchDrawable(patch), Drawable.class);
                Gdx.app.log("UiSkinLoader", "Registered 9patch: " + drawableName);
            } catch (Exception e) {
                Gdx.app.error("UiSkinLoader", "Failed to register 9patch: " + fh.path(), e);
            } finally {
                if (src != null)
                    src.dispose();
                if (inner != null)
                    inner.dispose();
            }
        }
    }
}
