package staraxis.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.scenes.scene2d.Group;

import staraxis.ui.theme.UiTheme;

/**
 * ToastWidgetTest（短暂通知显示/消失逻辑测试，不依赖 GL / 渲染资源）喵。
 *
 * 覆盖 G1.2：
 * - 构造时依据文本测算非零尺寸喵
 * - 成功/失败类型强调色映射（绿色 / 红色）喵
 * - act 倒计时递减，时长内不消失、到期自动从舞台移除喵
 * - 移除后释放父级（空槽），可被下一条替换喵
 * - 非正时长立即结束喵
 * - 无字体时尺寸测算用近似值兜底（不抛异常）喵
 */
class ToastWidgetTest {

    private static final float DURATION = 3f;

    @Test
    void constructionComputesNonZeroSizeFromText() {
        ToastWidget toast = new ToastWidget(null, null, UiTheme.defaults(),
                ToastWidget.Type.SUCCESS, "命令已执行", DURATION);
        assertTrue(toast.getWidth() > 0, "通知宽度应为正");
        assertTrue(toast.getHeight() > 0, "通知高度应为正");
        assertEquals(DURATION, toast.getRemaining(), 1e-4f);
        assertFalse(toast.isFinished());
    }

    @Test
    void accentColorDistinguishesSuccessAndFailure() {
        UiTheme theme = UiTheme.defaults();
        ToastWidget success = new ToastWidget(null, null, theme,
                ToastWidget.Type.SUCCESS, "命令已执行", DURATION);
        ToastWidget failure = new ToastWidget(null, null, theme,
                ToastWidget.Type.FAILURE, "命令失败", DURATION);
        assertSame(theme.success, success.getAccentColor());
        assertSame(theme.danger, failure.getAccentColor());
    }

    @Test
    void actCountsDownAndRemovesAfterDuration() {
        Group parent = new Group();
        ToastWidget toast = new ToastWidget(null, null, UiTheme.defaults(),
                ToastWidget.Type.SUCCESS, "命令已执行", DURATION);
        parent.addActor(toast);
        assertSame(parent, toast.getParent());

        // 1 秒后仍在显示，剩余时长递减喵
        toast.act(1f);
        assertFalse(toast.isFinished());
        assertEquals(DURATION - 1f, toast.getRemaining(), 1e-4f);
        assertSame(parent, toast.getParent());

        // 推进到时长耗尽后自动移除喵
        toast.act(2f);
        assertTrue(toast.isFinished());
        assertNull(toast.getParent(), "到期后应从舞台移除");
        assertFalse(parent.getChildren().contains(toast, true));
    }

    @Test
    void nonPositiveDurationFinishesImmediately() {
        ToastWidget toast = new ToastWidget(null, null, UiTheme.defaults(),
                ToastWidget.Type.FAILURE, "命令失败", 0f);
        assertTrue(toast.isFinished());
        assertEquals(0f, toast.getRemaining(), 1e-4f);
        toast.act(0.1f);
        assertTrue(toast.isFinished());
    }

    @Test
    void nullFontUsesFallbackMeasurementWithoutError() {
        ToastWidget toast = new ToastWidget(null, null, UiTheme.defaults(),
                ToastWidget.Type.SUCCESS, "命令已执行", DURATION);
        // 无字体时按近似值测算，尺寸仍应为正且不为零喵
        assertTrue(toast.getWidth() > 0);
        assertTrue(toast.getHeight() > 0);
    }
}
