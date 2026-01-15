package staraxis.core.module;

/**
 * 游戏模块生命周期接口。所有系统（经济/战斗/AI 等）应实现此接口，
 * 由主循环按固定顺序调用。
 */
public interface IGameModule {
    void onPrepare();

    void onUpdate(double dtGameHours);

    void onCommit();

    void onPostUpdate();
}
