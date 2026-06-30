package staraxis.ui.console;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

/**
 * 开发者控制台的 UI 视图。
 *
 * 设计要点：
 * - 顶部半屏布局，通过 Table 实现。
 * - 输出区 (logArea) 是一个 Table，放在 ScrollPane 内，确保可滚动。
 * - 输入框 (inputField) 在底部，回车后触发命令执行。
 */
public class DevConsoleView extends Table {

    private final Table logArea;
    private final ScrollPane scrollPane;
    private final TextField inputField;

    public DevConsoleView() {
        super();

        align(Align.topLeft);

        logArea = new Table();
        logArea.align(Align.topLeft);

        scrollPane = new ScrollPane(logArea);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        inputField = new TextField("", new Skin());

        add(scrollPane).expand().fill().row();
        add(inputField).expandX().fillX().height(32).pad(4);
    }

    public void addLog(String message, String style) {
        Label label;
        try {
            Label.LabelStyle labelStyle = getSkin().get(style, Label.LabelStyle.class);
            label = new Label(message, labelStyle);
        } catch (Exception ignored) {
            label = new Label(message, getSkin());
        }
        label.setWrap(true);
        logArea.add(label).expandX().fillX().left().pad(2, 8, 2, 8).row();
        scrollPane.layout();
        scrollPane.scrollTo(0, 0, 0, 0);
    }

    public TextField getInputField() {
        return inputField;
    }

    @Override
    public void clear() {
        logArea.clearChildren();
    }
}
