package com.staraxis.game.client.ui.components;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Stage;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.delay;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.run;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/**
 * 简易提示组件 (Toast Component)
 *
 * 用于在屏幕中央显示临时的文字提示（如“功能开发中”）
 */
public class Toast {

    /**
     * 显示一个提示信息
     *
     * @param stage UI 舞台
     * @param text 提示文本
     * @param skin UI 皮肤
     */
    public static void show(Stage stage, String text, Skin skin) {
        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        MarqueeLabel label = new MarqueeLabel(text, skin);
        label.getColor().a = 0f;

        // 为提示信息设置一个合理的最大宽度，以触发滚动逻辑
        float maxWidth = stage.getWidth() * 0.8f;
        table.add(label).width(maxWidth).center();

        // 动画效果：淡入 -> 停留 -> 淡出 -> 移除
        label.addAction(sequence(
                fadeIn(0.2f, Interpolation.fade),
                delay(1.5f),
                fadeOut(0.3f, Interpolation.fade),
                run(() -> table.remove())
        ));
    }
}
