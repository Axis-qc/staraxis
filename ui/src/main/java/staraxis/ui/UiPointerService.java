package staraxis.ui;

import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.List;

/**
 * UiPointerService（统一 UI 命中守卫服务）。
 *
 * 输入归属统一（点击单一归属）的核心：所有 UI 交互区域（窗口 bounds、
 * 摘要面板、临时弹窗、调试面板等）集中注册，3D 层只调 {@link #isMouseOverUi()}
 * 判定归属，自身零 UI 知识。
 *
 * 注册机制：
 * - 静态注册：生命周期与游戏一致的区域（UiWindowManager 窗口、摘要面板）
 * - 动态注册/注销：临时 UI（SelectHomeConfirmDialog 弹窗、UiDebug 调试面板）
 *
 * 坐标约定：{@link PointerBlocker#blocks} 接收 stage 左下原点坐标；
 * {@link #isMouseOverUi()} 内部处理 Gdx.input 左上原点 → stage 左下原点的 Y 翻转，
 * 翻转只此一处，各注册方无需关心。
 *
 * TODO 2.6.4 键盘焦点机制（未来）：引入文本输入框（搜索/重命名等）时
 * 增加键盘焦点管理，输入框聚焦期间 WASD/QE/快捷键（M/F3/F4/ESC/R）不响应。
 * 本次不做——键盘输入不涉及鼠标位置，当前无文本输入框场景。
 */
public class UiPointerService {

    /** UI 交互区域命中接口：坐标命中该区域时返回 true（stage 左下原点坐标系）喵 */
    public interface PointerBlocker {
        /**
         * 判断坐标是否落在该 UI 区域上。
         *
         * @param x stage 屏幕 X（左下原点）
         * @param y stage 屏幕 Y（左下原点）
         * @return true 表示该坐标应拦截 3D 交互
         */
        boolean blocks(float x, float y);
    }

    private final List<PointerBlocker> blockers = new ArrayList<>();

    /**
     * 注册 UI 交互区域（重复注册同一实例无效果）喵。
     */
    public void register(PointerBlocker blocker) {
        if (blocker != null && !blockers.contains(blocker)) {
            blockers.add(blocker);
        }
    }

    /**
     * 注销 UI 交互区域（临时 UI 关闭时调用）喵。
     */
    public void unregister(PointerBlocker blocker) {
        blockers.remove(blocker);
    }

    /**
     * 判断鼠标当前是否落在任一注册的 UI 交互区域上喵。
     *
     * Gdx.input 为左上原点（Y 向下），stage/UI 判定为左下原点，
     * Y 翻转在此统一处理，注册方与调用方均不关心坐标系。
     *
     * @return true 表示鼠标在 UI 上，调用方应跳过 3D 场景交互处理
     */
    public boolean isMouseOverUi() {
        float x = Gdx.input.getX();
        float y = Gdx.graphics.getHeight() - Gdx.input.getY();
        for (PointerBlocker blocker : blockers) {
            if (blocker.blocks(x, y)) {
                return true;
            }
        }
        return false;
    }
}
