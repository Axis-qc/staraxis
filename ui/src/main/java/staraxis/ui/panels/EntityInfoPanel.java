package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.VectorLabel;
import staraxis.ui.widgets.VectorWindow;

/**
 * EntityInfoPanel（实体详情窗口）喵。
 *
 * 基于 {@link VectorWindow} 的完整信息窗口，展示 {@link EntityInfoViewModel#detailFields}
 * 全量字段。由 UiWindowManager 统一管理（中央定位、级联偏移、ESC 栈、关闭按钮）。
 */
public class EntityInfoPanel extends VectorWindow {

    /** 内容区内边距（px）喵 */
    private static final float CONTENT_PAD = 12f;
    /** 字段行高（px）喵 */
    private static final float LINE_HEIGHT = 20f;
    /** 类型标签与字段区的间距（px）喵 */
    private static final float TAG_GAP = 6f;
    /** 窗口最小宽度（px）喵 */
    private static final float MIN_WIDTH = 340f;
    /** 内容区最大高度（px），超出截断喵 */
    private static final float MAX_CONTENT_HEIGHT = 480f;

    private final UiTheme theme;
    /** 内容字段用字体（VectorWindow 的 font 为私有，构造时留存一份）喵 */
    private final BitmapFont font;

    public EntityInfoPanel(ShapeRenderer sr, BitmapFont font, EntityInfoViewModel vm, UiTheme theme) {
        super(sr, font, vm.title);
        this.font = font;
        this.theme = theme;
        rebuild(vm);
    }

    /** 按视图模型重建窗口内容喵 */
    public void rebuild(EntityInfoViewModel vm) {
        setTitle(vm.title);
        getContentGroup().clearChildren();

        // 内容从内容区底部向上排（stage 坐标 y 向上），最终高度按内容量计算喵
        float y = CONTENT_PAD;
        float maxW = MIN_WIDTH;

        for (EntityInfoViewModel.FieldEntry f : vm.detailFields) {
            Color lineColor = f.color() != null ? f.color() : theme.text;
            VectorLabel line = new VectorLabel(font,
                    f.key() + ": " + f.value(), lineColor);
            line.setPosition(CONTENT_PAD, y);
            getContentGroup().addActor(line);
            maxW = Math.max(maxW, CONTENT_PAD * 2f + line.getWidth());
            y += LINE_HEIGHT;
        }

        // 类型标签显示在字段区上方（habitability 等着色）喵
        if (vm.typeLabel != null && !vm.typeLabel.isEmpty()) {
            y += TAG_GAP;
            VectorLabel tag = new VectorLabel(font, "[" + vm.typeLabel + "]",
                    vm.typeColor != null ? vm.typeColor : theme.text);
            tag.setPosition(CONTENT_PAD, y);
            getContentGroup().addActor(tag);
            maxW = Math.max(maxW, CONTENT_PAD * 2f + tag.getWidth());
            y += LINE_HEIGHT;
        }

        y += CONTENT_PAD;
        // TODO 滚动支持：字段超出 MAX_CONTENT_HEIGHT 时截断，后续换 VectorScrollPane 容器
        setContentSize(maxW, Math.min(y, MAX_CONTENT_HEIGHT));
    }
}
