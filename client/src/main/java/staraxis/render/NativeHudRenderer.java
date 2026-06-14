package staraxis.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import staraxis.game.entity.EntityType;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.ui.FontProvider;

/**
 * NativeHudRenderer
 *
 * 原生矢量 HUD：参考 web in-game UI，绘制时间、资产总览、选择列表、舰船面板和底部系统入口。
 */
public class NativeHudRenderer {

    private static final String FONT_PATH = "fonts/chinese/Alibaba-PuHuiTi-H.ttf";

    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GlyphLayout layout;

    public NativeHudRenderer() {
        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        layout = new GlyphLayout();
        BitmapFont ttfFont = FontProvider.tryCreateFontFromTtfOrNull(FONT_PATH, 20);
        font = ttfFont != null ? ttfFont : FontProvider.createDefaultFont();
        font.setUseIntegerPositions(true);
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.update();
    }

    public void render(RealTimeWorldState state) {
        HudStats stats = HudStats.from(state);
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanels();
        drawOverview(stats);
        drawSelectionPanel();
        drawShipPanel();
        drawBottomBar();
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawBorders();
        shapes.end();

        batch.begin();
        drawTexts(state, stats);
        batch.end();
    }

    public void dispose() {
        font.dispose();
        batch.dispose();
        shapes.dispose();
    }

    private void drawPanels() {
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        panel(12f, h - 52f, 300f, 40f);
        panel(w - 326f, h - 52f, 314f, 40f);
    }

    private void drawOverview(HudStats stats) {
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        float x = w - 304f;
        float y = h - 410f;
        panel(x, y, 292f, 342f);
        header(x, y + 300f, 292f, 42f);
        card(x + 14f, y + 194f, 264f, 72f);
        card(x + 14f, y + 134f, 78f, 48f);
        card(x + 108f, y + 134f, 78f, 48f);
        card(x + 202f, y + 134f, 78f, 48f);
        card(x + 14f, y + 48f, 264f, 70f);
    }

    private void drawSelectionPanel() {
        float h = viewport.getWorldHeight();
        float y = h * 0.5f - 110f;
        panel(12f, y, 240f, 220f);
        header(12f, y + 178f, 240f, 42f);
        card(24f, y + 118f, 216f, 46f);
        card(24f, y + 62f, 216f, 46f);
    }

    private void drawShipPanel() {
        float w = viewport.getWorldWidth();
        float x = w - 284f;
        float y = 96f;
        panel(x, y, 272f, 210f);
        header(x, y + 166f, 272f, 44f);
        card(x + 16f, y + 78f, 240f, 66f);
        button(x + 16f, y + 24f, 104f, 36f, true);
        button(x + 132f, y + 24f, 104f, 36f, false);
    }

    private void drawBottomBar() {
        float w = viewport.getWorldWidth();
        float barW = 492f;
        float x = (w - barW) * 0.5f;
        panel(x, 14f, barW, 58f);
        for (int i = 0; i < 5; i++) {
            button(x + 14f + i * 94f, 26f, 78f, 34f, i == 0);
        }
    }

    private void drawBorders() {
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        border(12f, h - 52f, 300f, 40f);
        border(w - 326f, h - 52f, 314f, 40f);
        border(w - 304f, h - 410f, 292f, 342f);
        border(12f, h * 0.5f - 110f, 240f, 220f);
        border(w - 284f, 96f, 272f, 210f);
        border((w - 492f) * 0.5f, 14f, 492f, 58f);
    }

    private void drawTexts(RealTimeWorldState state, HudStats stats) {
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        float ox = w - 304f;
        float oy = h - 410f;
        float sy = h * 0.5f - 110f;
        float sx = w - 284f;
        float bx = (w - 492f) * 0.5f;

        text("星轴 StarAxis", 26f, h - 27f, c(0.92f, 0.96f, 1f, 1f));
        text("原生客户端", 160f, h - 27f, c(0.48f, 0.72f, 1f, 1f));
        text(formatTime(state), w - 314f, h - 27f, c(0.9f, 0.96f, 1f, 1f));
        text("<   " + speedLabel(state) + "   >", w - 112f, h - 27f, c(0.72f, 0.86f, 1f, 1f));

        text("资产总览", ox + 16f, oy + 325f, Color.WHITE);
        small("日期", ox + 28f, oy + 248f); small(formatDay(state), ox + 178f, oy + 248f);
        small("Tick", ox + 28f, oy + 224f); small(String.valueOf(state == null ? 0 : state.simulationTick), ox + 178f, oy + 224f);
        small("星区", ox + 28f, oy + 200f); small(String.valueOf(stats.sectors), ox + 178f, oy + 200f);
        center("行星", ox + 14f, oy + 164f, 78f); center(String.valueOf(stats.planets), ox + 14f, oy + 144f, 78f);
        center("舰队", ox + 108f, oy + 164f, 78f); center(String.valueOf(stats.ships), ox + 108f, oy + 144f, 78f);
        center("设施", ox + 202f, oy + 164f, 78f); center(String.valueOf(stats.stations), ox + 202f, oy + 144f, 78f);
        small("资产列表", ox + 28f, oy + 96f); small(stats.assetsText(), ox + 28f, oy + 72f);
        small("点击资产项聚焦 | 左键拖动平移 | Q/E 缩放", ox + 16f, oy + 24f);

        text("已选中", 28f, sy + 203f, Color.WHITE);
        small("0", 224f, sy + 202f);
        small("当前未选择实体", 36f, sy + 146f);
        small("左键选择 / 框选", 36f, sy + 90f);
        small("双击聚焦，右键下令", 36f, sy + 34f);

        text("舰船信息", sx + 16f, 289f, Color.WHITE);
        small("未选择舰船", sx + 18f, 222f);
        small("状态", sx + 18f, 200f); small("待机中", sx + 176f, 200f);
        center("聚焦", sx + 16f, 143f, 104f); center("关闭", sx + 132f, 143f, 104f);
        small("右键点击空白处下达移动指令", sx + 18f, 106f);

        String[] tabs = { "开发", "军事", "科技", "内政", "外交" };
        for (int i = 0; i < tabs.length; i++) {
            center(tabs[i], bx + 14f + i * 94f, 48f, 78f);
        }
    }

    private void panel(float x, float y, float w, float h) {
        shapes.setColor(0.018f, 0.026f, 0.046f, 0.88f);
        shapes.rect(x, y, w, h);
    }

    private void header(float x, float y, float w, float h) {
        shapes.setColor(0.045f, 0.08f, 0.14f, 0.9f);
        shapes.rect(x, y, w, h);
    }

    private void card(float x, float y, float w, float h) {
        shapes.setColor(0.026f, 0.04f, 0.075f, 0.84f);
        shapes.rect(x, y, w, h);
    }

    private void button(float x, float y, float w, float h, boolean active) {
        shapes.setColor(active ? c(0.09f, 0.28f, 0.42f, 0.95f) : c(0.04f, 0.07f, 0.12f, 0.92f));
        shapes.rect(x, y, w, h);
    }

    private void border(float x, float y, float w, float h) {
        shapes.setColor(0.18f, 0.32f, 0.52f, 0.85f);
        shapes.rect(x, y, w, h);
    }

    private void text(String text, float x, float y, Color color) {
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, layout, x, y);
    }

    private void small(String text, float x, float y) {
        text(text, x, y, c(0.72f, 0.82f, 0.94f, 1f));
    }

    private void center(String text, float x, float y, float width) {
        layout.setText(font, text);
        text(text, x + (width - layout.width) * 0.5f, y, c(0.9f, 0.96f, 1f, 1f));
    }

    private String formatTime(RealTimeWorldState s) {
        if (s == null || s.year <= 0) {
            return "----.--.-- --:--:--";
        }
        return String.format("%04d-%02d-%02d %02d:%02d:%02d", s.year, s.month, s.day, s.hour, s.minute, s.second);
    }

    private String formatDay(RealTimeWorldState s) {
        if (s == null || s.year <= 0) {
            return "-";
        }
        return String.format("%04d-%02d-%02d", s.year, s.month, s.day);
    }

    private String speedLabel(RealTimeWorldState s) {
        if (s == null) {
            return "--";
        }
        double speed = s.gameSecondsPerRealSecond;
        if (Math.abs(speed - 60.0) < 0.1) return "1m/s";
        if (Math.abs(speed - 3600.0) < 0.1) return "1h/s";
        if (Math.abs(speed - 86400.0) < 0.1) return "1d/s";
        return String.format("%.0fs/s", speed);
    }

    private Color c(float r, float g, float b, float a) {
        return new Color(r, g, b, a);
    }

    private static class HudStats {
        final int sectors;
        final int planets;
        final int ships;
        final int stations;

        HudStats(int sectors, int planets, int ships, int stations) {
            this.sectors = sectors;
            this.planets = planets;
            this.ships = ships;
            this.stations = stations;
        }

        static HudStats from(RealTimeWorldState state) {
            if (state == null) {
                return new HudStats(0, 0, 0, 0);
            }
            int planets = 0;
            int ships = 0;
            int stations = 0;
            for (EntitySnapshot e : state.getEntitySnapshotsView()) {
                if (e.entityType == EntityType.PLANET) planets++;
                if (e.entityType == EntityType.SHIP) ships++;
                if (e.entityType == EntityType.STATION) stations++;
            }
            return new HudStats(state.getSectorCentersWorldGUView().size(), planets, ships, stations);
        }

        String assetsText() {
            if (planets == 0 && ships == 0 && stations == 0) {
                return "暂无资产，选择出生星系开始游戏";
            }
            return "行星 " + planets + " / 舰队 " + ships + " / 设施 " + stations;
        }
    }
}
