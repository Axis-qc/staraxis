package staraxis.game.space.octree;

import java.util.ArrayList;
import java.util.List;

/**
 * OctreeNode（八叉树节点）。
 *
 * 八叉树空间索引的基本单元。每个节点代表一个轴对齐包围盒，
 * 叶节点存储实体条目，内部节点分裂为 8 个子节点。
 *
 * 用于 GalaxyOctree 中的只读空间查询（每 tick 重建，无并发写）。
 */
public class OctreeNode {

    /** 每个节点最多存储的实体数（超出则分裂）。 */
    static final int MAX_ENTITIES = 8;

    /** 最大递归深度。 */
    static final int MAX_DEPTH = 16;

    /** 节点中心坐标 X。 */
    public final double cx;
    /** 节点中心坐标 Y。 */
    public final double cy;
    /** 节点中心坐标 Z。 */
    public final double cz;

    /** 半边长 X（节点范围 [cx-hx, cx+hx]）。 */
    public final double hx;
    /** 半边长 Y。 */
    public final double hy;
    /** 半边长 Z。 */
    public final double hz;

    /** 叶节点：存储在本节点的实体条目。 */
    private List<OctreeEntry> entries;

    /** 内部节点：8 个子节点（null 表示叶节点）。 */
    private OctreeNode[] children;

    /**
     * 创建一个 OctreeNode。
     *
     * @param cx   中心 X
     * @param cy   中心 Y
     * @param cz   中心 Z
     * @param hx   半边长 X
     * @param hy   半边长 Y
     * @param hz   半边长 Z
     * @param depth 当前深度（0 = 根）
     */
    OctreeNode(double cx, double cy, double cz, double hx, double hy, double hz, int depth) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
        this.entries = new ArrayList<>();
        this.children = null;
    }

    /**
     * 插入一个实体条目。
     * 如果超出容量且未达最大深度，分裂并重新分配。
     */
    void insert(OctreeEntry entry, int depth) {
        if (children != null) {
            // 内部节点：找子节点插入
            int childIndex = getChildIndex(entry.x, entry.y, entry.z);
            children[childIndex].insert(entry, depth + 1);
            return;
        }

        // 叶节点：加入列表
        entries.add(entry);

        // 检查是否需要分裂
        if (entries.size() > MAX_ENTITIES && depth < MAX_DEPTH) {
            split(depth);
        }
    }

    /**
     * 分裂节点为 8 个子节点，并将现有实体重新分配到子节点。
     */
    private void split(int depth) {
        children = new OctreeNode[8];
        double hhx = hx * 0.5;
        double hhy = hy * 0.5;
        double hhz = hz * 0.5;

        int idx = 0;
        for (int iz = 0; iz < 2; iz++) {
            for (int iy = 0; iy < 2; iy++) {
                for (int ix = 0; ix < 2; ix++) {
                    double childCx = cx + (ix == 0 ? -hhx : hhx);
                    double childCy = cy + (iy == 0 ? -hhy : hhy);
                    double childCz = cz + (iz == 0 ? -hhz : hhz);
                    children[idx++] = new OctreeNode(childCx, childCy, childCz, hhx, hhy, hhz, depth + 1);
                }
            }
        }

        // 将现有实体重新分配到子节点
        List<OctreeEntry> oldEntries = entries;
        entries = null; // 叶节点变为内部节点后不再持有实体
        for (OctreeEntry e : oldEntries) {
            int childIndex = getChildIndex(e.x, e.y, e.z);
            children[childIndex].insert(e, depth + 1);
        }
    }

    /**
     * 根据位置计算子节点索引（0-7）。
     * 索引编码：bit0=X, bit1=Y, bit2=Z（1=正方向，0=负方向）。
     */
    private int getChildIndex(double x, double y, double z) {
        int index = 0;
        if (x >= cx) index |= 1;
        if (y >= cy) index |= 2;
        if (z >= cz) index |= 4;
        return index;
    }

    /**
     * 球体查询：返回指定球体内所有 entityId。
     *
     * @param qx     球心 X
     * @param qy     球心 Y
     * @param qz     球心 Z
     * @param radius 半径
     * @param result 结果列表（追加写入）
     */
    public void querySphere(double qx, double qy, double qz, double radius, List<Long> result) {
        // 快速拒否：如果本节点包围盒与球体无交集，跳过
        double closestX = Math.max(cx - hx, Math.min(qx, cx + hx));
        double closestY = Math.max(cy - hy, Math.min(qy, cy + hy));
        double closestZ = Math.max(cz - hz, Math.min(qz, cz + hz));
        double dx = qx - closestX;
        double dy = qy - closestY;
        double dz = qz - closestZ;
        if (dx * dx + dy * dy + dz * dz > radius * radius) {
            return;
        }

        if (children != null) {
            // 内部节点：递归查询 8 个子节点
            for (OctreeNode child : children) {
                child.querySphere(qx, qy, qz, radius, result);
            }
        } else {
            // 叶节点：检查每个实体
            for (OctreeEntry entry : entries) {
                double ex = entry.x - qx;
                double ey = entry.y - qy;
                double ez = entry.z - qz;
                if (ex * ex + ey * ey + ez * ez <= radius * radius) {
                    result.add(entry.entityId);
                }
            }
        }
    }

    /**
     * K 近邻查询：返回距离指定点最近的 k 个实体（按距离升序）。
     *
     * @param qx     查询点 X
     * @param qy     查询点 Y
     * @param qz     查询点 Z
     * @param k      需要的近邻数量
     * @param result 结果列表（按距离升序）
     */
    public void queryKNN(double qx, double qy, double qz, int k, List<NeighborEntry> result) {
        if (children != null) {
            // 内部节点：按与查询点的距离排序子节点，优先遍历最近的
            OctreeNode[] sortedChildren = children.clone();
            java.util.Arrays.sort(sortedChildren, (a, b) -> {
                double da = a.minDistSq(qx, qy, qz);
                double db = b.minDistSq(qx, qy, qz);
                return Double.compare(da, db);
            });

            for (OctreeNode child : sortedChildren) {
                // 如果已有 k 个结果且当前节点的最近距离大于最远结果距离，跳过
                if (result.size() >= k) {
                    double farthestDist = result.get(result.size() - 1).distSq;
                    if (child.minDistSq(qx, qy, qz) > farthestDist) {
                        continue;
                    }
                }
                child.queryKNN(qx, qy, qz, k, result);
            }
        } else {
            // 叶节点：检查每个实体
            for (OctreeEntry entry : entries) {
                double dx = entry.x - qx;
                double dy = entry.y - qy;
                double dz = entry.z - qz;
                double distSq = dx * dx + dy * dy + dz * dz;
                // 插入排序到结果中（保持前 k 个最近的）
                insertSorted(result, new NeighborEntry(entry.entityId, distSq), k);
            }
        }
    }

    /**
     * 将邻居条目按升序插入结果列表，保持大小 ≤ k。
     */
    private void insertSorted(List<NeighborEntry> list, NeighborEntry entry, int k) {
        int i = 0;
        while (i < list.size() && list.get(i).distSq < entry.distSq) {
            i++;
        }
        list.add(i, entry);
        if (list.size() > k) {
            list.remove(list.size() - 1);
        }
    }

    /**
     * 计算本节点包围盒到指定点的最小平方距离。
     */
    double minDistSq(double qx, double qy, double qz) {
        double dx = 0;
        if (qx < cx - hx) dx = qx - (cx - hx);
        else if (qx > cx + hx) dx = qx - (cx + hx);
        double dy = 0;
        if (qy < cy - hy) dy = qy - (cy - hy);
        else if (qy > cy + hy) dy = qy - (cy + hy);
        double dz = 0;
        if (qz < cz - hz) dz = qz - (cz - hz);
        else if (qz > cz + hz) dz = qz - (cz + hz);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 获取当前节点内的实体数（叶节点）或总估计数。
     */
    int getTotalEntityCount() {
        if (children != null) {
            int count = 0;
            for (OctreeNode child : children) {
                count += child.getTotalEntityCount();
            }
            return count;
        }
        return entries != null ? entries.size() : 0;
    }

    // ── 辅助数据结构 ──

    /** 实体条目：叶节点中存储的实体引用。 */
    record OctreeEntry(long entityId, double x, double y, double z) {}

    /** KNN 结果条目：entityId + 到查询点的平方距离。 */
    public record NeighborEntry(long entityId, double distSq) {}
}
