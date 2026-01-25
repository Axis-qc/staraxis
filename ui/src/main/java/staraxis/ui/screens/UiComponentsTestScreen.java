package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;

import java.util.ArrayList;
import java.util.List;

public class UiComponentsTestScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;

    private Actor root;

    public UiComponentsTestScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        Skin skin = gui.get(Skin.class);
        if (skin == null) {
            Gdx.app.error("UiComponentsTestScreen", "Skin not found.");
            return;
        }

        Table container = new Table();
        container.setFillParent(true);

        Table header = new Table(skin);
        header.defaults().pad(6);

        Label title = new Label("UI Components Test", skin);
        title.setAlignment(Align.left);

        TextButton backBtn = new TextButton("Back", skin);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                gui.showMainMenu();
            }
        });

        header.add(title).left().expandX().fillX();
        header.add(backBtn).right();

        Table content = new Table(skin);
        content.top().left();
        content.defaults().pad(8).left();

        List<Actor> demos = buildDemos(skin);
        for (Actor demo : demos) {
            Table row = new Table(skin);
            row.defaults().pad(6);
            row.add(new Label(demo.getName() != null ? demo.getName() : demo.getClass().getSimpleName(), skin)).left()
                    .width(220);
            row.add(demo).left().growX();
            content.add(row).growX().fillX().row();
        }

        ScrollPane scroll = new ScrollPane(content, skin);
        scroll.setFadeScrollBars(false);
        scroll.setOverscroll(false, false);

        container.add(header).growX().fillX().row();
        container.add(scroll).grow().fill();

        // ESC 快捷返回主菜单（不占用 DevConsole 的 ESC：控制台可见时 DevConsole 优先处理）
        container.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    gui.showMainMenu();
                    return true;
                }
                return false;
            }
        });

        root = container;
        stage.addActor(root);
        stage.setKeyboardFocus(root);
    }

    private List<Actor> buildDemos(Skin skin) {
        List<Actor> list = new ArrayList<>();

        Label label = new Label("Label: Hello", skin);
        label.setName("label");
        list.add(label);

        TextButton textButton = new TextButton("TextButton", skin);
        textButton.setName("textbutton");
        list.add(textButton);

        CheckBox checkBox = new CheckBox("CheckBox", skin);
        checkBox.setName("checkbox");
        list.add(checkBox);

        Slider slider = new Slider(0f, 100f, 1f, false, skin);
        slider.setValue(42f);
        slider.setName("slider");
        list.add(slider);

        ProgressBar progressBar = new ProgressBar(0f, 100f, 1f, false, skin);
        progressBar.setValue(65f);
        progressBar.setName("progressbar");
        list.add(progressBar);

        SelectBox<String> selectBox = new SelectBox<>(skin);
        selectBox.setItems("A", "B", "C");
        selectBox.setSelected("B");
        selectBox.setName("selectbox");
        list.add(selectBox);

        TextField textField = new TextField("TextField", skin);
        textField.setMessageText("Type here...");
        textField.setName("textfield");
        list.add(textField);

        Image image = new Image();
        image.setName("image (empty)");
        image.setSize(64, 32);
        list.add(image);

        ImageButton imageButton = new ImageButton(skin);
        imageButton.setName("imagebutton");
        list.add(imageButton);

        Stack stack = new Stack();
        stack.setName("stack (Label+Button)");
        stack.add(new Label("Stack label", skin));
        stack.add(new TextButton("Stack btn", skin));
        list.add(stack);

        Window window = new Window("Window", skin);
        window.setName("window (packed)");
        window.add(new Label("Window content", skin)).pad(8);
        window.pack();
        list.add(window);

        TextButton dialogBtn = new TextButton("Open Dialog", skin);
        dialogBtn.setName("dialog (button) ");
        dialogBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Dialog d = new Dialog("Dialog", skin);
                d.text("Dialog body");
                d.button("OK");
                d.show(stage);
            }
        });
        list.add(dialogBtn);

        // group/container 示例
        Group group = new Group();
        group.setName("group (Label)");
        Label gl = new Label("Group child", skin);
        gl.setPosition(0, 0);
        group.addActor(gl);
        group.setSize(200, gl.getPrefHeight());
        list.add(group);

        // button(style)
        Button button = new Button(skin);
        button.setName("button");
        button.add(new Label("Button", skin)).pad(6);
        list.add(button);

        return list;
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
