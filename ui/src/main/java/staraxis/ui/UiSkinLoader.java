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
        registerNinePatchDrawables(skin, "ui/uiskin/9png");
        registerPngDrawables(skin, "ui/uiskin/png", "ui/uiskin/PNG");
        return skin;
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
