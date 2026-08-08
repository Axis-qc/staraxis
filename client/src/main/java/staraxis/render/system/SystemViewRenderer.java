package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.entity.EntityType;
import staraxis.game.space.OrbitSolver;
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.PlanetDetails;
import staraxis.game.state.snapshot.EntitySnapshot.StarDetails;
import staraxis.render.WorldCamera;
import staraxis.render.debug.ChunkGridDebugRenderer;
import staraxis.render.effect.WormholePlaneRenderer;
import staraxis.render.lod.LodCalculator;
import staraxis.render.lod.LodLevel;
import staraxis.render.mesh.OrbitRingMesh;
import staraxis.render.mesh.PlanetMesh;
import staraxis.render.mesh.StarMesh;
import staraxis.render.util.TemperatureColor;

/**
 * SystemViewRenderer（恒星系视图渲染器）。
 *
 * 渲染单个恒星系：所有恒星 + 行星 + 轨道环。
 * 纯快照驱动：所有天体数据由 EntitySnapshot 列表提供，不依赖 game 层类型。
 * 恒星位置由 StarDetails.systemPosX/Y/Z 决定（单星在原点）。
 * 行星使用 OrbitSolver 实时计算位置，再加引力中心偏移。
 *
 * 三级 LOD：
 * - FULL：高精度球体 + 辉光 + 轨道环
 * - LOW：低精度球体，无辉光
 * - POINT：光点
 * - HIDDEN：不渲染
 */
public class SystemViewRenderer {

    private final ModelBatch modelBatch;
    private final Environment environment;
    private final DirectionalLight starLight;
    private final PlanetMesh planetMesh;
    private final StarMesh starMesh;
    private final OrbitRingMesh orbitRing;

    /** 舰船 3D 模型渲染器（star_eater 测试模型，替代 2D 贴图精灵）。 */
    private final StarEaterShipRenderer shipRenderer;

    /** 对象池：恒星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> starInstances = new java.util.ArrayList<>();
    /** 对象池：高精度行星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> planetHighInstances = new java.util.ArrayList<>();
    /** 对象池：低精度行星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> planetLowInstances = new java.util.ArrayList<>();

    /** 实体ID -> 天体在系统局部空间的位置查找表，每帧构建。包含恒星+行星+小行星+卫星。 */
    private final java.util.HashMap<Long, Vector3> bodyCenterIndex = new java.util.HashMap<>();

    /** 恒星位置列表（每帧构建），用于计算"恒星→目标"光照方向。 */
    private final java.util.ArrayList<Vector3> starPositions = new java.util.ArrayList<>();

    private double simulationTime = 0.0;

    /** 区块网格调试渲染器（null = 关闭）。 */
    private ChunkGridDebugRenderer chunkDebug;

    /** 临时向量，避免每帧分配。 */
    private final Vector3 tmpVec = new Vector3();
    private final Vector3 tmpIntersect = new Vector3();

    /** 2D 屏幕圆标叠加层（天体/舰船位置标记 + 拾取）。 */
    private final SystemViewOverlay overlay = new SystemViewOverlay();

    /** 虫洞平面渲染器（开局舰船刷出时显示，3 秒渐隐）。 */
    private final WormholePlaneRenderer wormhole = new WormholePlaneRenderer();

    public SystemViewRenderer() {
        // 自定义 shader provider：舰船材质走 ShipShader（玩家颜色自发光），其余走默认 shader
        modelBatch = new ModelBatch(new staraxis.render.shader.ShipShaderProvider());
        environment = new Environment();
        starLight = new DirectionalLight();
        starLight.set(0.8f, 0.8f, 0.9f, 0, 1, 0);
        environment.add(starLight);

        planetMesh = new PlanetMesh();
        starMesh = new StarMesh();
        orbitRing = new OrbitRingMesh();
        shipRenderer = new StarEaterShipRenderer();
    }

    /** 当前帧待渲染的舰船快照列表（由 ClientGame 每帧设置）。 */
    private java.util.List<EntitySnapshot> currentFrameShips = java.util.List.of();

    /** 当前选中的舰船ID（高亮用）。 */
    private long highlightShipId = -1L;

    /** 确保恒星实例池足够大 */
    private void ensureStarInstances(int needed) {
        while (starInstances.size() < needed) {
            starInstances.add(new ModelInstance(starMesh.getModel(), 0, 0, 0));
        }
    }

    /** 确保行星两级实例池都足够大 */
    private void ensurePlanetInstances(int needed) {
        while (planetHighInstances.size() < needed) {
            planetHighInstances.add(new ModelInstance(planetMesh.getHighDetail(), 0, 0, 0));
        }
        while (planetLowInstances.size() < needed) {
            planetLowInstances.add(new ModelInstance(planetMesh.getLowDetail(), 0, 0, 0));
        }
    }

    /**
     * 设置当前帧要渲染的舰船快照列表（由 ClientGame 在每帧渲染前调用）。
     */
    public void setShips(java.util.List<EntitySnapshot> ships) {
        currentFrameShips = ships != null ? ships : java.util.List.of();
        shipRenderer.setShips(currentFrameShips);
    }

    /** 当前选中的实体ID（高亮用：星体边框 + 舰船贴图变色共用）喵 */
    private long selectedEntityId = -1L;

    /**
     * 设置选中高亮舰船ID。-1 = 无选中。
     * 兼容旧入口：委托到 {@link #setHighlightEntity}（舰船贴图高亮共用同一选中态）喵。
     */
    public void setHighlightShip(long shipId) {
        setHighlightEntity(shipId);
    }

    /**
     * 设置选中实体 ID（恒星/行星/卫星/小行星/舰船统一入口）。-1 = 无选中喵。
     * 星体画 2D 屏幕边框，舰船由 3D 模型渲染器处理。
     */
    public void setHighlightEntity(long entityId) {
        this.selectedEntityId = entityId;
        this.highlightShipId = entityId;
        shipRenderer.setHighlightShip(entityId);
    }

    /** 构建所有天体的系统局部空间位置索引（从快照构建）。 */
    private void buildBodyIndex(java.util.List<EntitySnapshot> snapshots) {
        bodyCenterIndex.clear();
        starPositions.clear();

        // 1. 恒星位于 systemPos（从 StarDetails 读取）
        for (EntitySnapshot snap : snapshots) {
            if (snap == null || snap.entityType != EntityType.STAR)
                continue;
            if (!(snap.details instanceof StarDetails sd))
                continue;
            Vector3 pos = new Vector3((float) sd.systemPosX, (float) sd.systemPosY, (float) sd.systemPosZ);
            bodyCenterIndex.put(snap.entityId, pos);
            starPositions.add(pos);
        }

        // 2. 行星/小行星：轨道解算 + 轨道中心偏移
        for (EntitySnapshot snap : snapshots) {
            if (snap == null)
                continue;
            if (snap.entityType != EntityType.PLANET && snap.entityType != EntityType.ASTEROID)
                continue;
            if (!(snap.details instanceof PlanetDetails pd))
                continue;
            Vector3 center = bodyCenterIndex.get(pd.orbitCenterEntityId);
            if (center == null)
                center = tmpVecZero();
            OrbitalElements orbit = toOrbitalElements(snap);
            SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
            bodyCenterIndex.put(snap.entityId, new Vector3(
                    (float) pos.x() + center.x,
                    (float) pos.y() + center.y,
                    (float) pos.z() + center.z));
        }

        // 3. 卫星：轨道解算 + 母行星位置偏移
        for (EntitySnapshot snap : snapshots) {
            if (snap == null || snap.entityType != EntityType.MOON)
                continue;
            if (!(snap.details instanceof PlanetDetails pd))
                continue;
            Vector3 center = bodyCenterIndex.get(pd.orbitCenterEntityId);
            if (center == null)
                center = tmpVecZero();
            OrbitalElements orbit = toOrbitalElements(snap);
            SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
            bodyCenterIndex.put(snap.entityId, new Vector3(
                    (float) pos.x() + center.x,
                    (float) pos.y() + center.y,
                    (float) pos.z() + center.z));
        }
    }

    /** 返回 (0,0,0) 向量（临时用，每次调用返回同一对象，不跨帧持有）。 */
    private Vector3 tmpVecZero() {
        tmpVec.set(0, 0, 0);
        return tmpVec;
    }

    /** 从位置索引查找天体的系统局部空间位置（详见下方实现）。 */

    public void render(java.util.List<EntitySnapshot> systemSnapshots, WorldCamera camera) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        // 从快照列表中分离恒星、行星体、舰船
        java.util.List<EntitySnapshot> stars = new java.util.ArrayList<>();
        java.util.List<EntitySnapshot> bodies = new java.util.ArrayList<>();
        java.util.List<EntitySnapshot> ships = new java.util.ArrayList<>();
        for (EntitySnapshot snap : systemSnapshots) {
            if (snap == null)
                continue;
            if (snap.entityType == EntityType.STAR)
                stars.add(snap);
            else if (snap.details instanceof PlanetDetails)
                bodies.add(snap);
        }
        // 当前帧舰船从外部设置（来自快照）
        ships = currentFrameShips;

        // 同步舰船数据到 3D 模型渲染器
        shipRenderer.setShips(ships);
        shipRenderer.setHighlightShip(highlightShipId);

        // 构建所有天体的层级位置索引（恒星→行星→小行星→卫星）
        buildBodyIndex(systemSnapshots);

        // 第一遍：渲染所有恒星
        ensureStarInstances(stars.size());
        renderAllStars(stars, camera);

        // 第一遍：渲染所有行星 + 小行星 + 卫星，填充深度缓冲
        ensurePlanetInstances(bodies.size());
        int bodyIdx = 0;
        for (EntitySnapshot snap : bodies) {
            renderPlanetBody(bodyIdx++, snap, camera);
        }

        // 舰船 3D 模型（在行星之后渲染，利用深度缓冲实现正确遮挡；光照方向来自最近恒星）
        shipRenderer.render(modelBatch, environment, camera, this::updateLightDirection);

        // 第二遍：渲染所有轨道环，利用完整的深度缓冲实现正确遮挡
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float orbitAlpha = LodCalculator.calculateOrbitAlpha(camera.getOrbitDistance());
        if (orbitAlpha > 0f) {
            for (EntitySnapshot snap : bodies) {
                renderOrbitRing(snap, camera, orbitAlpha);
            }
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // 区块网格调试渲染（始终在最上层）
        if (chunkDebug != null) {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            chunkDebug.render(camera.camera.combined, camera.camera.position);
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // 最上层：2D 屏幕圆标叠加层（深度测试已关闭）
        overlay.renderPlanetDots(systemSnapshots, camera, bodyCenterIndex);
        // 选中星体边框（屏幕空间战术框，圆标之上）喵
        overlay.renderSelectedFrame(selectedEntityId, camera, bodyCenterIndex);

        // 虫洞平面（最上层 billboard，additive 混合）
        wormhole.render(camera);
    }

    /** 渲染所有恒星，全部批处理在一次 begin/end 内完成。 */
    private void renderAllStars(java.util.List<EntitySnapshot> starSnapshots, WorldCamera camera) {
        if (starSnapshots.isEmpty())
            return;
        modelBatch.begin(camera.camera);
        for (int i = 0; i < starSnapshots.size(); i++) {
            EntitySnapshot snap = starSnapshots.get(i);
            if (!(snap.details instanceof StarDetails sd))
                continue;
            float[] rgb = TemperatureColor.temperatureToRgb(sd.temperatureK);
            float scale = (float) sd.radiusGU;

            ModelInstance instance = starInstances.get(i);
            instance.transform.idt();
            instance.transform.translate(
                    (float) sd.systemPosX,
                    (float) sd.systemPosY,
                    (float) sd.systemPosZ);
            instance.transform.scl(scale);
            instance.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));

            modelBatch.render(instance);
        }
        modelBatch.end();
    }

    /** 渲染单个天体（行星/小行星/卫星），位置从 bodyCenterIndex 取。 */
    private void renderPlanetBody(int index, EntitySnapshot snap, WorldCamera camera) {
        if (!(snap.details instanceof PlanetDetails pd))
            return;
        // 从位置索引取系统局部坐标
        if (!getBodyPosition(snap.entityId, tmpVec))
            return;
        float px = tmpVec.x, py = tmpVec.y, pz = tmpVec.z;

        Vector3 cameraPos = camera.camera.position;
        double distance = Math.sqrt(
                (cameraPos.x - px) * (cameraPos.x - px) +
                        (cameraPos.y - py) * (cameraPos.y - py) +
                        (cameraPos.z - pz) * (cameraPos.z - pz));
        LodLevel lod = LodCalculator.calculate(distance);

        if (lod == LodLevel.HIDDEN) {
            return;
        }

        float scale = (float) pd.radiusGU;
        float[] rgb = SystemViewOverlay.planetColor(pd.planetTypeId);

        ModelInstance instance = (lod == LodLevel.LOW)
                ? planetLowInstances.get(index)
                : planetHighInstances.get(index);
        instance.transform.idt();
        instance.transform.translate(px, py, pz);
        instance.transform.scl(scale);
        instance.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));
        instance.materials.get(0)
                .set(ColorAttribute.createEmissive(rgb[0] * 0.08f, rgb[1] * 0.08f, rgb[2] * 0.08f, 1f));

        // 方向光：从最近的恒星指向天体
        updateLightDirection(px, py, pz);

        modelBatch.begin(camera.camera);
        modelBatch.render(instance, environment);
        modelBatch.end();
    }

    /**
     * 更新方向光：光源方向为"最近的恒星 → 目标位置"。
     * 无恒星时保持当前方向不变。
     */
    private void updateLightDirection(float px, float py, float pz) {
        Vector3 nearestStar = null;
        float nearestDist = Float.MAX_VALUE;
        for (Vector3 star : starPositions) {
            float dx = px - star.x, dy = py - star.y, dz = pz - star.z;
            float dist = dx * dx + dy * dy + dz * dz;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestStar = star;
            }
        }
        if (nearestStar == null) {
            return;
        }
        starLight.setDirection(px - nearestStar.x, py - nearestStar.y, pz - nearestStar.z);
    }

    /** 轨道环带偏移 */
    private void renderOrbitRing(EntitySnapshot snap, WorldCamera camera, float orbitAlpha) {
        if (!(snap.details instanceof PlanetDetails pd))
            return;
        OrbitalElements orbit = toOrbitalElements(snap);
        Vector3 center = bodyCenterIndex.get(pd.orbitCenterEntityId);
        float cx = 0, cy = 0, cz = 0;
        if (center != null) {
            cx = center.x;
            cy = center.y;
            cz = center.z;
        }

        orbitRing.render(orbit,
                cx, cy, cz,
                camera.camera.combined, new Color(0.3f, 0.4f, 0.6f, orbitAlpha));
    }

    public void advanceTime(double dtSeconds) {
        simulationTime += dtSeconds;
        // 更新虫洞渐隐
        if (wormhole.isVisible()) {
            wormhole.update((float) dtSeconds);
        }
    }

    /**
     * 在指定世界坐标激活虫洞平面（开局时 fleet 刷出位置）喵。
     */
    public void showWormhole(double x, double y, double z) {
        wormhole.show(x, y, z);
    }

    public void resetTime() {
        simulationTime = 0.0;
    }

    /**
     * 重置渲染器内部状态，用于新世界开始时清理旧世界的运行时状态喵。
     *
     * 使用场景：退出游戏返回主菜单后，重新开始新游戏时调用喵。
     */
    public void reset() {
        resetTime();
        currentFrameShips = java.util.List.of();
        selectedEntityId = -1L;
        highlightShipId = -1L;
    }

    public void setSimulationTime(double time) {
        simulationTime = time;
    }

    /**
     * 开启或关闭区块网格调试渲染。
     * 开启后按 F4 可见 System View 中以原点为中心的区块边界线。
     */
    public void setDebugChunksEnabled(boolean enabled) {
        if (enabled && chunkDebug == null) {
            chunkDebug = new ChunkGridDebugRenderer();
        } else if (!enabled) {
            if (chunkDebug != null) {
                chunkDebug.dispose();
                chunkDebug = null;
            }
        }
    }

    public boolean isDebugChunksEnabled() {
        return chunkDebug != null;
    }

    public void dispose() {
        modelBatch.dispose();
        planetMesh.dispose();
        starMesh.dispose();
        orbitRing.dispose();
        shipRenderer.dispose();
        overlay.dispose();
        wormhole.dispose();
        if (chunkDebug != null) {
            chunkDebug.dispose();
            chunkDebug = null;
        }
    }

    /** 从 PlanetDetails 构建轨道根数。 */
    private static OrbitalElements toOrbitalElements(EntitySnapshot snap) {
        if (!(snap.details instanceof PlanetDetails pd)) {
            return new OrbitalElements(0, 0, 0, 0, 0, 0, 0, 1);
        }
        double periodSeconds = pd.orbitalPeriodDays * 86400.0;
        return new OrbitalElements(
                pd.semiMajorAxisGU,
                pd.eccentricity,
                Math.toRadians(pd.inclinationDeg),
                Math.toRadians(pd.longitudeOfAscendingNodeDeg),
                Math.toRadians(pd.periapsisArgDeg),
                Math.toRadians(pd.meanAnomalyDegAtEpoch),
                0.0,
                periodSeconds);
    }

    /**
     * 拾取 System View 中屏幕坐标处的天体（恒星或行星）。
     *
     * @param camera  当前相机
     * @param screenX 屏幕 X 坐标
     * @param screenY 屏幕 Y 坐标
     * @return 最近天体的 entityId，未命中返回 -1
     */
    public long pick(WorldCamera camera, int screenX, int screenY) {
        Ray ray = camera.camera.getPickRay(screenX, screenY);
        long closestId = -1;
        float closestDist = Float.MAX_VALUE;

        // 检测所有恒星（从 bodyCenterIndex 取位置）
        for (java.util.Map.Entry<Long, Vector3> entry : bodyCenterIndex.entrySet()) {
            // 恒星已在 bodyCenterIndex 中
            Vector3 bodyPos = entry.getValue();
            if (bodyPos == null)
                continue;
            // 粗略半径：恒星用 40f，行星从 bodyCenterIndex 没有半径信息，用 10f 保守
            float radius = 10f;
            if (Intersector.intersectRaySphere(ray, bodyPos, radius, tmpIntersect)) {
                float dist = tmpIntersect.dst2(ray.origin);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestId = entry.getKey();
                }
            }
        }

        // 检测所有舰船（使用 3D 模型渲染器的 2D 屏幕拾取，覆盖投影可视区域）
        long shipPickId = shipRenderer.pick(screenX, screenY);
        if (shipPickId >= 0) {
            closestId = shipPickId;
        }

        // 2D 圆标拾取委托给 overlay（仅天体圆标，舰船已由 3D 模型渲染器处理）
        closestId = overlay.pickDots(screenX, screenY, closestId, closestDist,
                bodyCenterIndex, camera.camera.position);

        return closestId;
    }

    /**
     * 获取天体当前在系统局部空间中的位置（用于镜头聚焦）。
     *
     * @param entityId 目标实体 ID
     * @param out      输出位置（系统局部坐标）
     * @return 找到返回 true，未找到返回 false
     */
    public boolean getBodyPosition(long entityId, Vector3 out) {
        // 从 bodyCenterIndex 查找（包含恒星、行星、小行星、卫星）
        Vector3 cached = bodyCenterIndex.get(entityId);
        if (cached != null) {
            out.set(cached);
            return true;
        }
        // 检查舰船
        for (EntitySnapshot snap : currentFrameShips) {
            if (snap.entityId == entityId && snap.posWorldGU != null) {
                out.set((float) snap.posWorldGU.x(), (float) snap.posWorldGU.y(), (float) snap.posWorldGU.z());
                return true;
            }
        }
        return false;
    }
}
