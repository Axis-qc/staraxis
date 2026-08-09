package staraxis.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import staraxis.ui.FontProvider;
import staraxis.ui.theme.UiTheme;

/**
 * ToastWidget（矢量风格短暂通知组件，G1.2 命令结果反馈用）喵。
 *
 * 屏幕右上角短暂显示的通知：
 * - 成功绿色（theme.success）/ 失败红色（theme.danger）区分结果喵
 * - 显示固定时长后自动从舞台移除（act 驱动）喵
 * - ShapeRenderer + BitmapFont 绘制，复用现有矢量 UI 风格，不依赖 libGDX Skin 喵
 *
 * 测试友好：sr / font 允许为 null，纯逻辑（act 计时 / 自动移除）不依赖渲染资源，
 * 尺寸测算在无字体时用近似值兜底喵。
 */
public class ToastWidget extends Group {

    /** 通知类型喵 */
    public enum Type {
        /** 成功通知（绿色）喵 */
        SUCCESS,
        /** 失败通知（红色）喵 */
        FAILURE
    }

    /** 左右内边距（px）喵 */
    private static final float HORIZONTAL_PAD = 16f;
    /** 上下内边距（px）喵 */
    private static final float VERTICAL_PAD = 8f;
    /** 左侧强调条宽度（px），颜色区分成功/失败喵 */
    private static final float ACCENT_BAR_WIDTH = 4f;
    /** 文字缩放：与 VectorLabel 一致的字体逻辑尺寸（20px 正文）喵 */
    private static final float FONT_SCALE = 20f / FontProvider.VECTOR_FONT_GEN_SIZE;
    /** 无字体时估算文字宽度的近似值（px/字符，按中文全角宽度近似）喵 */
    private static final float FALLBACK_CHAR_WIDTH = 20f;
    /** 无字体时估算文字高度的近似值（px）喵 */
    private static final float FALLBACK_LINE_HEIGHT = 24f;
    /** 消失前淡出时长（秒）喵 */
    private static final float FADE_DURATION = 0.3f;

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final UiTheme theme;
    private final Type type;
    private String text;

    /** 剩余显示时长（秒），归零后自动移除喵 */
    private float remaining;

    /** 测算出的文字宽度/高度（px），无字体时用近似值兜底喵 */
    private float textWidth;
    private float textHeight;

    /**
     * 构造通知喵。
     *
     * @param sr       形状渲染器（可为 null，纯逻辑场景下跳过绘制）
     * @param font     矢量字体（可为 null，纯逻辑场景下跳过绘制）
     * @param theme    UI 主题（可为 null，回退默认主题）
     * @param type     通知类型（成功/失败）
     * @param text     通知文案
     * @param duration 显示时长（秒），非正数立即结束
     */
    public ToastWidget(ShapeRenderer sr, BitmapFont font, UiTheme theme, Type type, String text, float duration) {
        this.sr = sr;
        this.font = font;
        this.theme = theme != null ? theme : UiTheme.defaults();
        this.type = type != null ? type : Type.SUCCESS;
        this.text = text != null ? text : "";
        this.remaining = Math.max(0f, duration);
        setTouchable(Touchable.disabled);
        recomputeSize();
    }

    /** @return 通知类型（成功/失败）喵 */
    public Type getType() {
        return type;
    }

    /** @return 通知文案喵 */
    public String getText() {
        return text;
    }

    /** @return 剩余显示时长（秒）喵 */
    public float getRemaining() {
        return remaining;
    }

    /** @return 是否已结束（时长耗尽）喵 */
    public boolean isFinished() {
        return remaining <= 0f;
    }

    /** @return 类型对应的强调色：成功绿色 / 失败红色喵 */
    public Color getAccentColor() {
        return type == Type.SUCCESS ? theme.success : theme.danger;
    }

    /**
     * 依据当前文本重新测算尺寸（宽 = 文字宽 + 内边距 + 强调条，高 = 文字高 + 内边距）喵。
     * 构造时调用一次；外部改文案后可再次调用刷新尺寸喵。
     */
    public void recomputeSize() {
        if (font != null) {
            float oldScaleX = font.getData().scaleX;
            float oldScaleY = font.getData().scaleY;
            font.getData().setScale(FONT_SCALE);
            GlyphLayout layout = new GlyphLayout();
            layout.setText(font, text);
            textWidth = layout.width;
            textHeight = layout.height;
            font.getData().setScale(oldScaleX, oldScaleY);
        } else {
            textWidth = text.length() * FALLBACK_CHAR_WIDTH;
            textHeight = FALLBACK_LINE_HEIGHT;
        }
        setSize(textWidth + HORIZONTAL_PAD * 2f + ACCENT_BAR_WIDTH, textHeight + VERTICAL_PAD * 2f);
    }

    /**
     * 每帧推进倒计时，时长耗尽后自动从舞台移除喵。
     *
     * @param delta 帧间隔（秒）
     */
    @Override
    public void act(float delta) {
        super.act(delta);
        remaining -= delta;
        if (remaining <= 0f) {
            remaining = 0f;
            remove();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = getColor().a * parentAlpha;
        // 临近消失前淡出喵
        if (remaining < FADE_DURATION) {
            alpha *= Math.max(0f, remaining / FADE_DURATION);
        }
        if (alpha <= 0f) {
            return;
        }

        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        Color accent = getAccentColor();

        if (sr != null) {
            batch.end();
            sr.setProjectionMatrix(batch.getProjectionMatrix());
            sr.setTransformMatrix(batch.getTransformMatrix());

            // 背景喵
            Color bg = theme.panelBg;
            sr.setColor(bg.r, bg.g, bg.b, bg.a * alpha);
            sr.begin(ShapeType.Filled);
            sr.rect(x, y, w, h);
            sr.end();

            // 左侧强调条（颜色区分成功/失败）喵
            sr.setColor(accent.r, accent.g, accent.b, alpha);
            sr.begin(ShapeType.Filled);
            sr.rect(x, y, ACCENT_BAR_WIDTH, h);
            sr.end();

            batch.begin();
        }

        if (font != null) {
            float oldScaleX = font.getData().scaleX;
            float oldScaleY = font.getData().scaleY;
            font.getData().setScale(FONT_SCALE);
            font.setColor(accent.r, accent.g, accent.b, alpha);
            // 文字盒从强调条右侧、上内边距处开始（baseline = 文字盒底部）喵
            font.draw(batch, text, x + ACCENT_BAR_WIDTH + HORIZONTAL_PAD, y + VERTICAL_PAD + textHeight);
            font.setColor(Color.WHITE);
            font.getData().setScale(oldScaleX, oldScaleY);
        }

        super.draw(batch, parentAlpha);
    }
}
