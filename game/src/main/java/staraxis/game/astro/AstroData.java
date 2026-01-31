package staraxis.game.astro;

import java.util.Collections;
import java.util.List;

/**
 * AstroData
 *
 * 权威的宇宙星体数据容器。
 */
public class AstroData {

    /**
     * 权威的星系列表。
     */
    public final List<StarSystem> systems;

    public AstroData(List<StarSystem> systems) {
        this.systems = systems != null ? List.copyOf(systems) : List.of();
    }

    /**
     * 返回一个不可修改的星系列表视图。
     */
    public List<StarSystem> getSystemsView() {
        return Collections.unmodifiableList(systems);
    }
}
