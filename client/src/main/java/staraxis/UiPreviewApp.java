package staraxis;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.VisUI.SkinScale;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisWindow;

public class UiPreviewApp extends ApplicationAdapter {
    private Stage stage;

    @Override
    public void create() {
        VisUI.load(SkinScale.X2);

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        VisWindow window = new VisWindow("UI Preview (VisUI X2)");
        window.setResizable(true);
        window.setMovable(true);

        VisTable content = new VisTable(true);
        content.add(new VisLabel("这只是 UI 预览入口：不跑世界、不接服务端，仅渲染 UI。"));
        content.row();
        content.add(new VisTextButton("按钮示例"));

        window.add(content).grow();
        window.pack();
        window.centerWindow();

        stage.addActor(window);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.10f, 0.10f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null)
            stage.dispose();
        VisUI.dispose();
    }
}
