# 数据模型设计 - 014 基础坐标系与比例尺

## 核心实体

### 1. `WorldCoordinate`（世界坐标）
```java
/**
 * 表示大尺度世界坐标，用于星系级定位。
 * 采用分层坐标：WorldCoord（整型网格） + LocalOffset（局部浮点偏移）。
 */
public class WorldCoordinate {
    // 网格单元大小：1,000,000 km（1M km）
    public static final long CELL_SIZE_KM = 1_000_000L;
    
    // 网格坐标（大尺度，整型）
    private final int gridX, gridY, gridZ;
    // 局部偏移（相对于网格原点，单位：km）
    private final float offsetX, offsetY, offsetZ;
    
    // 构造方法、getter、toWorldCoords() 等辅助方法...
}
```

### 2. `ScaleSystem`（比例尺系统）
```java
/**
 * 管理与摄像机缩放级别联动的比例尺计算。
 */
public class ScaleSystem {
    // 最大放大（zoom=1.0）时的比例尺：1px = 1km
    public static final float BASE_KM_PER_PIXEL = 1.0f;
    
    // 当前缩放级别：zoom=1.0 为最大放大（最近）；zoom 越大表示视野越远（zoom out），对应 kmPerPixel 越大
    private float zoom = 1.0f;
    
    // 获取当前比例尺（km/px）
    public float getKmPerPixel() {
        return zoom * BASE_KM_PER_PIXEL;
    }
    
    // 更新缩放级别（由输入系统调用）
    public void updateZoom(float delta) { /* ... */ }
}
```

### 3. `DebugOverlay`（调试悬浮窗）
```java
/**
 * F3 调试悬浮窗，显示坐标与比例尺信息。
 */
public class DebugOverlay {
    private final BitmapFont font;
    private final WorldCoordinate cameraWorldPos;
    private final ScaleSystem scaleSystem;
    
    public void render(SpriteBatch batch) {
        // 显示相机世界坐标、缩放级别、比例尺等
        font.draw(batch, String.format("Position: %s", formatWorldCoord(cameraWorldPos)), 10, Gdx.graphics.getHeight() - 20);
        font.draw(batch, String.format("Zoom: %.2f", scaleSystem.getZoom()), 10, Gdx.graphics.getHeight() - 40);
        font.draw(batch, String.format("Scale: %s", formatScale(scaleSystem.getKmPerPixel())), 10, Gdx.graphics.getHeight() - 60);
    }
    
    private String formatScale(float kmPerPixel) {
        // 实现单位自动转换（m/km/AU/ly）
        // ...
    }
}
```

### 4. `WorldGridRenderer`（世界网格渲染器）
```java
/**
 * 渲染世界坐标轴与XY平面网格。
 */
public class WorldGridRenderer {
    private final ShapeRenderer shapeRenderer;
    private final ScaleSystem scaleSystem;
    
    public void render(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        
        // 1. 计算网格步长（目标：屏幕上约 100px 间距）
        float targetWorldStep = 100.0f * scaleSystem.getKmPerPixel();
        float gridStep = calculateNiceStep(targetWorldStep);
        
        // 2. 计算绘制范围（相机视锥 + 余量）
        float viewWidth = camera.viewportWidth * camera.zoom;
        float viewHeight = camera.viewportHeight * camera.zoom;
        float margin = 1.2f; // 20% 余量
        
        // 3. 绘制坐标轴（X:红, Y:绿, Z:蓝）
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        
        // X轴（红）
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.line(0, 0, 0, 1000, 0, 0);
        
        // Y轴（绿）
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.line(0, 0, 0, 0, 1000, 0);
        
        // Z轴（蓝）
        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.line(0, 0, 0, 0, 0, 1000);
        
        // 4. 绘制XY平面网格
        shapeRenderer.setColor(0.5f, 0.5f, 0.5f, 0.5f);
        // ... 根据 gridStep 和相机位置绘制网格线 ...
        
        shapeRenderer.end();
    }
    
    private float calculateNiceStep(float targetWorldStep) {
        // 实现 1-2-5 系列的“漂亮数”步长选择
        // ...
    }
}
```

## 关键流程

### 1. 坐标转换流程
```
WorldCoordinate (大尺度) → 渲染前局部化 → 浮点坐标 → GPU 渲染
          ↑↓ 坐标服务转换
Game Logic (逻辑层)
```

### 2. 比例尺更新流程
```
用户输入 → 更新 ScaleSystem.zoom → 触发重绘 → WorldGridRenderer 使用新比例尺
                                      → DebugOverlay 显示更新后的值
```

## 设计决策

1. **精度处理**：
   - 使用 `WorldCoordinate` 封装大尺度坐标，避免精度丢失
   - 渲染前将世界坐标转换为相机相对坐标

2. **性能优化**：
   - 网格线批处理（通过 `ShapeRenderer`）
   - 动态调整网格密度（基于相机距离）

3. **可维护性**：
   - 所有与坐标/比例尺相关的常量集中管理
   - 调试功能可通过配置开关

## 待办事项
- [ ] 实现 `WorldCoordinate` 的序列化/反序列化
- [ ] 添加网格线 LOD（Level of Detail）
- [ ] 性能测试：大规模坐标转换与渲染