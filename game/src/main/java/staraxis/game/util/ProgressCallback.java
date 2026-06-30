package staraxis.game.util;

/**
 * ProgressCallback（进度回调）—— 用于长时间运行的生成/加载任务报告进度喵。
 */
@FunctionalInterface
public interface ProgressCallback {

    /**
     * 进度更新。
     *
     * @param progress 0.0 ~ 1.0
     * @param phase    当前阶段描述（可 null）
     */
    void onProgress(float progress, String phase);
}
