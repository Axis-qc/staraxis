package staraxis.game.space.octree;

import staraxis.game.entity.Entity;
import staraxis.game.space.SpacePosition;

import java.util.ArrayList;
import java.util.List;

/**
 * GalaxyOctree（星系八叉树空间索引）。
 *
 * 只读空间索引，按恒星（以及任何有 3D 位置的实体）在银河中的坐标构建。
 * 每 tick 由主线程重建，worker 线程只读查询。
 *
 * 用途：
 * - 恒星拾取（点击选择最近的恒星）
 * - Frustum 视锥裁剪（判断哪些恒星在视野内）
 * - 近邻查询（找某点附近的实体）
 * - IntelSystem 传感器范围查询（替代旧 hex 网格扩展）
 *
 * 约定：
 * - 世界空间范围：±500,000 GU（即 1,000,000 x 1,000,000 x 1,000,000 立方体）
 * - 根节点覆盖整个世界空间
 * - 每 tick 全量重建，无需并发写入
 */
public class GalaxyOctree {

    /** 世界空间半边长（GU），八叉树根节点覆盖 ±WORLD_HALF。 */
    public static final double WORLD_HALF = 500_000.0;

    /** 八叉树根节点。 */
    private OctreeNode root;

    /** 当前构建版本号（每 rebuild 一次递增）。 */
    private int epoch = 0;

    /**
     * 从实体列表重建八叉树。
     * 旧树将被丢弃，新树在方法返回后立即可用。
     *
     * @param entities 所有需要索引的实体（需要含 posWorldGU）
     */
    public void rebuild(List<? extends Entity> entities) {
        root = new OctreeNode(0, 0, 0, WORLD_HALF, WORLD_HALF, WORLD_HALF, 0);
        epoch++;

        if (entities == null || entities.isEmpty()) return;

        for (Entity entity : entities) {
            if (entity == null || entity.posWorldGU == null) continue;
            root.insert(
                    new OctreeNode.OctreeEntry(entity.entityId, entity.posWorldGU.x(), entity.posWorldGU.y(), entity.posWorldGU.z()),
                    0);
        }
    }

    /**
     * 从实体列表重建八叉树（SpacePosition 版本）。
     * 适用于 entitiesById.values() 中部分实体需要索引的场景。
     *
     * @param entityIds 实体 ID 列表
     * @param positions 对应的世界坐标（必须与 entityIds 一一对应）
     */
    public void rebuildFromPositions(List<Long> entityIds, List<SpacePosition> positions) {
        root = new OctreeNode(0, 0, 0, WORLD_HALF, WORLD_HALF, WORLD_HALF, 0);
        epoch++;

        int size = Math.min(entityIds.size(), positions.size());
        for (int i = 0; i < size; i++) {
            SpacePosition pos = positions.get(i);
            if (pos == null) continue;
            root.insert(
                    new OctreeNode.OctreeEntry(entityIds.get(i), pos.x(), pos.y(), pos.z()),
                    0);
        }
    }

    /**
     * 球体查询：返回指定球心半径内所有 entityId。
     *
     * @param cx     球心 X
     * @param cy     球心 Y
     * @param cz     球心 Z
     * @param radius 查询半径（GU）
     * @return 匹配的 entityId 列表
     */
    public List<Long> querySphere(double cx, double cy, double cz, double radius) {
        if (root == null) return List.of();
        List<Long> result = new ArrayList<>();
        root.querySphere(cx, cy, cz, radius, result);
        return result;
    }

    /**
     * 球体查询（SpacePosition 版本）。
     */
    public List<Long> querySphere(SpacePosition center, double radius) {
        return querySphere(center.x(), center.y(), center.z(), radius);
    }

    /**
     * K 近邻查询：返回距离指定点最近的 k 个实体。
     *
     * @param cx 查询点 X
     * @param cy 查询点 Y
     * @param cz 查询点 Z
     * @param k  需要的结果数量
     * @return 按距离升序排列的邻居列表
     */
    public List<OctreeNode.NeighborEntry> queryKNN(double cx, double cy, double cz, int k) {
        if (root == null) return List.of();
        List<OctreeNode.NeighborEntry> result = new ArrayList<>();
        root.queryKNN(cx, cy, cz, k, result);
        return result;
    }

    /**
     * K 近邻查询（SpacePosition 版本）。
     */
    public List<OctreeNode.NeighborEntry> queryKNN(SpacePosition center, int k) {
        return queryKNN(center.x(), center.y(), center.z(), k);
    }

    /**
     * 获取当前版本号。
     */
    public int getEpoch() {
        return epoch;
    }

    /**
     * 获取树中的索引实体总数（近似）。
     */
    public int getIndexedCount() {
        return root != null ? root.getTotalEntityCount() : 0;
    }
}
