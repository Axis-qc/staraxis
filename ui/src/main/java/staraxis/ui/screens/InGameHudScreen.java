package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.ui.Gui;

/**
 * 游戏中 HUD 覆盖层 Screen。
 *
 * 覆盖在世界渲染之上，包含顶部概览、时间控制、底部建造栏、ESC 菜单等子组件。
 */
public class InGameHudScreen implements Disposable {

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    private Label timeLabel;
    private Label tickLabel;
    private Label entityCountLabel;

    public InGameHudScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        Skin skin = gui.get(Skin.class);
        if (skin == null) return;

        Table table = new Table();
        table.setFillParent(true);

        Table topBar = new Table();
        topBar.top().right();
        timeLabel = new Label("", skin);
        tickLabel = new Label("", skin);
        entityCountLabel = new Label("", skin);
        topBar.add(timeLabel).padRight(12);
        topBar.add(tickLabel).padRight(12);
        topBar.add(entityCountLabel);
        table.add(topBar).expandX().fillX().top().pad(8).row();

        table.row();
        table.add().expand().fill();

        Table bottomBar = new Table();
        bottomBar.bottom().center();

        String[] tabs = { "inGame.develop", "inGame.military", "inGame.tech",
                "inGame.domestic", "inGame.diplomacy" };
        for (String tabKey : tabs) {
            TextButton btn = new TextButton(gui.i18n(tabKey), skin);
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    gui.dispatchAction("SHOW_DEVELOPING_DIALOG");
                }
            });
            bottomBar.add(btn).width(120).height(36).pad(4);
        }

        TextButton escBtn = new TextButton(gui.i18n("common.back"), skin);
        escBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gui.dispatchAction("RETURN_TO_MAIN_MENU");
            }
        });
        bottomBar.add(escBtn).width(80).height(36).pad(4);

        table.add(bottomBar).expandX().fillX().bottom().pad(8).row();

        root = table;
        stage.addActor(root);

        refreshHud();
    }

    public void refreshHud() {
        StarAxisGameRuntime rt = gui.getRuntime();
        if (rt == null) return;

        RealTimeWorldState state = rt.getRealTimeWorldStateReadonly();
        if (state == null) return;

        timeLabel.setText(String.format("Y%d M%d D%d %02d:%02d",
                state.year, state.month, state.day, state.hour, state.minute));
        tickLabel.setText("Tick: " + state.simulationTick);
        entityCountLabel.setText("Entities: " + state.getEntitiesByIdView().size());
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
