package staraxis.game.space.galaxy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GalaxyData（星系生成结果）。
 *
 * 持有整个星系的所有恒星位置数据。
 * 纯数据容器，不包含任何生成逻辑。
 */
public class GalaxyData {

    /** 世界种子。 */
    public final long worldSeed;

    /** 星系类型。 */
    public final GalaxyType galaxyType;

    /** 所有恒星的位置数据。 */
    public final List<StarPosition> stars;

    /** starId -> StarPosition 的快速查找索引。 */
    private final Map<Long, StarPosition> starIndex;

    public GalaxyData(long worldSeed, GalaxyType galaxyType, List<StarPosition> stars) {
        this.worldSeed = worldSeed;
        this.galaxyType = galaxyType;
        this.stars = Collections.unmodifiableList(stars);

        Map<Long, StarPosition> index = new HashMap<>(stars.size());
        for (StarPosition star : stars) {
            index.put(star.starId(), star);
        }
        this.starIndex = Collections.unmodifiableMap(index);
    }

    /**
     * 根据 starId 查找恒星。
     *
     * @param starId 恒星ID
     * @return 恒星位置数据，不存在返回 null
     */
    public StarPosition getStar(long starId) {
        return starIndex.get(starId);
    }

    /**
     * 恒星总数。
     */
    public int starCount() {
        return stars.size();
    }
}
