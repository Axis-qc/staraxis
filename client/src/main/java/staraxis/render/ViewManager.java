package staraxis.render;

/**
 * ViewManager（视图管理器）。
 *
 * 管理 Galaxy/System 双视图切换。
 * 两个视图完全独立，切换时重置相机参数。
 *
 * 切换方式：
 * - Galaxy -> System：点击恒星
 * - System -> Galaxy：按 ESC
 */
public class ViewManager {

    public enum ViewLevel { GALAXY, SYSTEM }

    private ViewLevel currentView = ViewLevel.GALAXY;
    private long selectedSystemId = 0;

    /**
     * 当前视图层级。
     */
    public ViewLevel getCurrentView() {
        return currentView;
    }

    /**
     * 当前选中的恒星系ID（仅 System 视图有效）。
     */
    public long getSelectedSystemId() {
        return selectedSystemId;
    }

    /**
     * 切换到恒星系视图。
     *
     * @param systemId 目标恒星系ID
     */
    public void switchToSystem(long systemId) {
        this.currentView = ViewLevel.SYSTEM;
        this.selectedSystemId = systemId;
    }

    /**
     * 切换到星系视图。
     */
    public void switchToGalaxy() {
        this.currentView = ViewLevel.GALAXY;
        this.selectedSystemId = 0;
    }

    /**
     * 是否在星系视图。
     */
    public boolean isInGalaxyView() {
        return currentView == ViewLevel.GALAXY;
    }

    /**
     * 是否在恒星系视图。
     */
    public boolean isInSystemView() {
        return currentView == ViewLevel.SYSTEM;
    }
}
