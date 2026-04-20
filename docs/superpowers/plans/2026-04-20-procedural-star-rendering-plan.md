# StarAxis 恒星程序化渲染迁移计划

**日期**：2026-04-20  
**目标**：将 `LayerStarRenderer`（恒星分层渲染器）从静态贴图 `SpriteMaterial`（精灵贴图材质）路线，迁移为按恒星类型/温度驱动的程序化渲染方案喵。

---

## 1. 当前现状

当前恒星渲染位于 `web/src/rendering/layers/celestial/renderers/starRenderer.ts` 喵。  
现状特点如下喵：

- 使用 `THREE.Sprite`（Three.js 精灵对象）+ `THREE.SpriteMaterial`（精灵材质）喵。
- 近景优先使用 `surfaceTexturePath`（恒星表面贴图路径）加载普通位图喵。
- 远景退化到 `CanvasTexture`（画布纹理）圆点喵。
- 颜色主要由 `temperatureK`（表面温度）做色相映射喵。
- 不存在真正的程序化表面、动态扰动、边缘光晕或可扩展的光影表达喵。

### 现有可用恒星数据

从 `StarDetails`（前端恒星快照数据）可直接拿到这些字段喵：

- `starTypeId`（恒星类型 ID）喵。
- `radiusGU`（恒星半径）喵。
- `massSolar`（太阳质量倍数）喵。
- `temperatureK`（表面温度）喵。
- `description`（描述文本）喵。
- `surfaceTexturePath`（旧贴图路径）喵。

---

## 2. 迁移目标

第一阶段只处理恒星喵，不动行星喵。  
目标不是一步做到完整天体物理渲染，而是先解决当前最明显的问题喵：

1. 放大后贴图模糊喵。
2. 贴图无法动态变化喵。
3. 光晕/表面扰动只能靠静态贴图假装，表现不够稳定喵。
4. 现有恒星类型信息没有参与渲染风格分层喵。

### 第一阶段交付标准

- 恒星不再依赖 `surfaceTexturePath` 作为主渲染来源喵。
- 恒星外观由 `starTypeId`（恒星类型）和 `temperatureK`（温度）共同驱动喵。
- 至少支持：
  - 程序化表面颜色分布喵。
  - 程序化动态扰动喵。
  - 程序化边缘光晕/外发光喵。
  - 近景放大时不再出现位图模糊喵。
- 保留现有 LOD（细节层级系统）和对象池思路，避免一次把性能模型打碎喵。

---

## 3. 推荐技术路线

### 推荐方案：程序化 billboard（面向镜头的着色器面片）喵

第一阶段推荐仍保留“2D 面向镜头”的渲染形态喵，但把静态贴图换成 `ShaderMaterial`（着色器材质）喵。

理由如下喵：

- 改动范围比直接上 `SphereGeometry`（球体网格）小很多喵。
- 仍能复用当前大部分对象池、LOD、剔除和缩放逻辑喵。
- 已经足够解决：
  - 贴图模糊喵。
  - 表面动态喵。
  - 边缘光晕喵。
- 后续若要升级成真正球体，也可以沿用这套程序化参数体系喵。

### 不推荐第一阶段直接做真实球体的原因

- 需要重新处理恒星永远朝向、深度排序、远景性能、LOD 切换喵。
- 需要补充光照模型、法线、相机缩放语义，改动面会从“恒星渲染器”扩大到“星体层整体策略”喵。

---

## 4. 计划分阶段

### 阶段 A：建立程序化恒星参数模型喵

目标：先定义“不同恒星类型看起来应该有什么区别”喵。

计划内容喵：

1. 在前端新增恒星渲染参数映射表喵。
2. 以 `starTypeId` 为第一优先级，`temperatureK` 为兜底分类喵。
3. 输出统一渲染参数结构，例如喵：
   - `baseColor`（基础色）喵。
   - `hotColor`（高温亮部色）喵。
   - `rimColor`（边缘光颜色）喵。
   - `noiseScale`（表面噪声尺度）喵。
   - `pulseSpeed`（脉动速度）喵。
   - `glowIntensity`（辉光强度）喵。
   - `surfaceBanding`（条纹/颗粒程度）喵。

交付文件建议喵：

- `web/src/rendering/layers/celestial/renderers/starProfile.ts`

---

### 阶段 B：将星体渲染核心改成程序化材质喵

目标：替换当前 `SpriteMaterial`（精灵材质）路径喵。

计划内容喵：

1. 在 `starRenderer.ts` 中引入程序化星体材质喵。
2. 将当前 `THREE.Sprite` 替换为更适合 shader（着色器）的渲染对象喵。
3. 在 shader 中实现喵：
   - 径向亮度分布喵。
   - 时间驱动的表面噪声扰动喵。
   - 边缘辉光喵。
   - 温度/类型驱动的颜色混合喵。
4. 保留对象池喵。
5. 保留现有 `LOD` 判断和远景最小尺寸策略喵。

候选实现喵：

- `PlaneGeometry`（平面网格）+ `ShaderMaterial` 喵。
- 或 `Sprite` + 自定义 shader（如果 Three.js 当前约束允许）喵。

---

### 阶段 C：接入时间驱动的动态效果喵

目标：让恒星不是静止贴纸喵。

计划内容喵：

1. 在每帧更新时传入时间 uniform（着色器统一变量）喵。
2. 支持这些动态效果喵：
   - 表面流动喵。
   - 轻微脉动喵。
   - 边缘闪烁喵。
3. 为不同类型恒星设置不同动态强度喵，例如喵：
   - 蓝白巨星更活跃喵。
   - 黄矮星更稳定喵。
   - 红巨星脉动更缓慢喵。

---

### 阶段 D：兼容旧数据与回退策略喵

目标：在迁移初期降低风险喵。

计划内容喵：

1. `surfaceTexturePath` 暂不删除字段喵。
2. 先让它退化成“兼容保留字段”，不再作为主路径喵。
3. 如程序化材质初始化失败，允许回退到旧圆形后备纹理喵。

---

## 5. 具体改动范围

### 主要修改文件喵

- `web/src/rendering/layers/celestial/renderers/starRenderer.ts`

### 新增文件建议喵

- `web/src/rendering/layers/celestial/renderers/starProfile.ts`
- `web/src/rendering/layers/celestial/renderers/shaders/starSurface.glsl.ts`  
  或  
- `web/src/rendering/layers/celestial/renderers/starMaterial.ts`

### 可能需要少量联动的文件喵

- `web/src/rendering/layers/celestial/celestialLayer.ts`
- `web/src/rendering/subsystems/lodSystem.ts`（仅当需要新增恒星近景/远景阈值参数时）喵。

---

## 6. 风险点

### 风险 1：程序化 shader 性能抖动喵

缓解策略喵：

- 第一阶段只给恒星使用喵。
- 保持 billboard 路线，不直接上高面数球体喵。
- 继续用 LOD 控制远景细节喵。

### 风险 2：视觉风格一次变太多喵

缓解策略喵：

- 保持当前温度色带作为基础喵。
- 先实现“看起来像原版但更高级”的版本，再逐步增加风格化效果喵。

### 风险 3：星型分类标准不清晰喵

缓解策略喵：

- 第一阶段先用 `starTypeId` + `temperatureK` 做映射喵。
- 如果后端类型枚举和美术目标不一致，再补一层前端渲染 profile（渲染配置）映射喵。

---

## 7. 实施顺序建议

推荐按下面顺序实施喵：

1. 定义恒星类型到渲染参数的映射喵。
2. 做最小可用的程序化静态恒星材质喵。
3. 加入时间驱动动态效果喵。
4. 接回对象池与 LOD 喵。
5. 最后移除对 `surfaceTexturePath` 主渲染路径的依赖喵。

---

## 8. 已确认的实现决策喵

以下决策已由用户确认，后续实现以此为准喵：

### 决策 1：第一阶段几何路线喵

- **已选**：A 喵。
- **结论**：继续用面向镜头的 2D 面片/公告板，只把材质改成程序化 shader（着色器材质）喵。

### 决策 2：恒星类型分组依据喵

- **已选**：A 喵。
- **结论**：以 `starTypeId`（恒星类型 ID）为主，`temperatureK`（恒星表面温度）为辅喵。

### 决策 3：旧贴图字段处理方式喵

- **已选**：A 喵。
- **结论**：保留 `surfaceTexturePath`（旧恒星贴图路径）字段，但不再参与主渲染，只作为兼容/回退喵。

### 决策 4：视觉风格方向喵

- **已选**：B 喵。
- **结论**：整体视觉风格偏风格化，辉光、脉动、表面扰动会更明显喵。

---

## 9. 据此收敛后的实施基线喵

基于以上决策，第一阶段的最小实现范围固定为喵：

1. 使用 billboard（面向镜头公告板）几何喵。
2. 使用 `starTypeId`（恒星类型 ID）驱动主风格分组喵。
3. 使用 `temperatureK`（恒星表面温度）驱动颜色带和类型兜底喵。
4. 旧 `surfaceTexturePath`（旧恒星贴图路径）退出主渲染路径，仅保留回退用途喵。
5. 视觉表现以更明显的程序化辉光、脉动和表面扰动为目标喵。

### 第一阶段建议拆分喵

#### 任务 1：建立恒星渲染 profile（渲染配置）映射喵
- 新增 `starProfile.ts`（恒星渲染配置映射）喵。
- 按 `starTypeId`（恒星类型 ID）建立风格分组喵。
- 至少包含：
  - `baseColor`（基础颜色）喵。
  - `hotColor`（高温亮部颜色）喵。
  - `rimColor`（边缘辉光颜色）喵。
  - `noiseScale`（表面噪声尺度）喵。
  - `pulseSpeed`（脉动速度）喵。
  - `glowIntensity`（辉光强度）喵。
  - `surfaceBanding`（条纹/颗粒强度）喵。

#### 任务 2：建立程序化恒星材质喵
- 新增 `starMaterial.ts`（恒星材质构建模块）或 shader 文件喵。
- 将当前贴图驱动的 `SpriteMaterial`（精灵贴图材质）替换为程序化 `ShaderMaterial`（着色器材质）喵。
- 实现：
  - 径向亮度衰减喵。
  - 表面噪声扰动喵。
  - 风格化边缘辉光喵。
  - 时间驱动脉动喵。

#### 任务 3：接入 `LayerStarRenderer`（恒星分层渲染器）喵
- 将当前 `surfaceTexturePath`（旧恒星贴图路径）加载逻辑降级为回退路径喵。
- 保留对象池喵。
- 保留现有 LOD（细节层级）和缩放逻辑喵。
- 将恒星 profile（渲染配置）参数传入 shader（着色器）喵。

#### 任务 4：做远景/近景分级喵
- 远景继续使用低成本圆盘/简化效果喵。
- 中近景启用完整程序化表面与辉光喵。
- 避免所有恒星在极远距离也跑完整动态效果喵。

---

## 10. 计划完成后的下一步

用户决策已经齐全喵。  
下一步可以直接把第一阶段细化为“具体文件级任务清单 + 实现步骤”喵，或者直接开始落地实现喵。

---

## 11. 第一阶段具体文件级任务清单喵

本节将第一阶段拆成可以直接执行的文件级任务喵。  
默认实施顺序为：先新增配置与材质模块，再替换 `LayerStarRenderer`（恒星分层渲染器），最后做 LOD（细节层级）收口与回退策略喵。

### 任务 1：新增恒星渲染 profile（渲染配置）模块喵

**文件**：`web/src/rendering/layers/celestial/renderers/starProfile.ts` 喵。  
**职责**：把 `starTypeId`（恒星类型 ID）和 `temperatureK`（恒星表面温度）转换为统一的程序化渲染参数喵。

#### 需要定义的内容喵

1. `ProceduralStarProfile`（程序化恒星渲染配置）类型喵。  
2. `getStarProfile(details: StarDetails)`（根据恒星快照生成渲染配置）函数喵。  
3. `getStarColorByTemperature(temperatureK)`（按温度映射基础色）函数喵。  
4. `starTypeId`（恒星类型 ID）到 profile（渲染配置）的映射表喵。

#### 该文件建议包含的字段喵

- `baseColor`（基础主色）喵。  
- `coreColor`（核心高亮色）喵。  
- `rimColor`（边缘辉光色）喵。  
- `noiseScale`（噪声尺度）喵。  
- `noiseSpeed`（表面流动速度）喵。  
- `pulseSpeed`（脉动速度）喵。  
- `pulseAmplitude`（脉动幅度）喵。  
- `glowIntensity`（辉光强度）喵。  
- `surfaceBanding`（条纹/颗粒强度）喵。  
- `flareStrength`（边缘活跃度）喵。  
- `styleGroup`（风格分组，例如蓝白星、黄矮星、红巨星）喵。

#### 完成标准喵

- 不依赖旧贴图也能仅凭 `StarDetails`（恒星快照数据）生成完整程序化参数喵。  
- 同一 `starTypeId`（恒星类型 ID）输入得到稳定一致的风格参数喵。  
- 未知 `starTypeId`（未知恒星类型 ID）时可自动回退到温度分类喵。

---

### 任务 2：新增恒星程序化材质模块喵

**文件**：建议二选一喵。  
- `web/src/rendering/layers/celestial/renderers/starMaterial.ts` 喵。  
或喵。  
- `web/src/rendering/layers/celestial/renderers/shaders/starSurface.glsl.ts` + `starMaterial.ts` 喵。

**职责**：封装 `ShaderMaterial`（着色器材质）创建逻辑，避免 `starRenderer.ts`（恒星渲染器）里堆满 shader（着色器）细节喵。

#### 建议导出内容喵

1. `ProceduralStarUniforms`（程序化恒星 uniform 变量结构）类型喵。  
2. `createProceduralStarMaterial()`（创建恒星着色器材质）函数喵。  
3. `applyStarProfileToMaterial(material, profile)`（将渲染配置写入材质）函数喵。  
4. `updateStarMaterialTime(material, elapsedSeconds)`（更新时间 uniform）函数喵。

#### shader（着色器）最低能力要求喵

- 根据 UV（纹理坐标）构建圆盘遮罩喵。  
- 核心更亮、边缘更柔和的径向亮度分布喵。  
- 表面噪声/流动扰动喵。  
- 风格化边缘辉光喵。  
- 时间驱动脉动喵。  
- 使用 profile（渲染配置）中的颜色和强度参数喵。

#### 完成标准喵

- 单独创建一个材质就能渲染出程序化恒星基础效果喵。  
- 不依赖 `surfaceTexturePath`（旧恒星贴图路径）喵。  
- 支持每帧更新时间喵。  
- 支持根据不同 profile（渲染配置）切换恒星风格喵。

---

### 任务 3：替换 LayerStarRenderer（恒星分层渲染器）内部实现喵

**文件**：`web/src/rendering/layers/celestial/renderers/starRenderer.ts` 喵。  
**职责**：将旧的 `SpriteMaterial`（精灵贴图材质）+ 纹理加载逻辑，迁移为 `PlaneGeometry`（平面网格）+ 程序化 `ShaderMaterial`（着色器材质）喵。

#### 具体改动点喵

1. 将对象池类型从 `THREE.Sprite[]`（精灵数组）改为更适合 shader（着色器）的对象喵：  
   - 推荐 `THREE.Mesh[]`（网格对象池）喵。  
2. 保留 `activeStarSpritesByEntityId`（当前活跃恒星映射）语义，但变量名建议改成：  
   - `activeStarsByEntityId` 喵。  
3. 删除旧的主渲染路径喵：  
   - `loadAndApplyTexture()`（加载旧恒星贴图）不再作为主逻辑喵。  
4. 引入 `getStarProfile()`（恒星渲染配置获取）喵。  
5. 引入 `createProceduralStarMaterial()`（创建程序化恒星材质）喵。  
6. 在 `updateStarSprite()`（当前恒星更新函数）中重构为：  
   - 计算尺寸喵。  
   - 读取 profile（渲染配置）喵。  
   - 写入 uniform（着色器统一变量）喵。  
   - 更新时间喵。  
   - 设置位置与缩放喵。

#### 建议重命名的内部结构喵

- `starSpritePool` → `starMeshPool` 喵。  
- `activeStarSpritesByEntityId` → `activeStarMeshesByEntityId` 喵。  
- `acquireStarSprite()` → `acquireStarMesh()` 喵。  
- `releaseStarSprite()` → `releaseStarMesh()` 喵。  
- `updateStarSprite()` → `updateStarVisual()` 喵。

#### 完成标准喵

- 恒星主渲染路径不再依赖位图贴图喵。  
- `LayerStarRenderer`（恒星分层渲染器）仍然维持：
  - 对象池喵。  
  - 视口剔除喵。  
  - 选中状态特判喵。  
  - 与 `LodState`（细节层级状态）兼容喵。  
- 恒星放大时不再出现普通贴图模糊喵。

---

### 任务 4：保留远景 fallback（回退显示）路径喵

**文件**：`web/src/rendering/layers/celestial/renderers/starRenderer.ts` 喵。  
**职责**：在程序化路径之外保留极远景低成本回退，避免所有恒星都跑完整效果喵。

#### 具体改动点喵

1. 保留 `fallbackCircleTexture`（远景回退圆形纹理）或改成更轻量的远景材质喵。  
2. 增加程序化与回退两档逻辑喵：  
   - 极远景：简化圆盘/低 uniform 更新喵。  
   - 中近景：完整程序化恒星材质喵。  
3. 原来的 `MIN_TEXTURE_PIXEL_SIZE`（最小贴图像素阈值）逻辑改造成：  
   - `MIN_PROCEDURAL_PIXEL_SIZE`（最小程序化启用阈值）喵。

#### 完成标准喵

- 屏幕上极小恒星不需要跑完整程序化表面喵。  
- 中近景恒星仍然是程序化主路径喵。  
- 性能上保留现有“远景更便宜”的基本策略喵。

---

### 任务 5：补充时间驱动与生命周期管理喵

**文件**：  
- `web/src/rendering/layers/celestial/renderers/starRenderer.ts` 喵。  
- `web/src/rendering/layers/celestial/renderers/starMaterial.ts`（若存在）喵。

**职责**：保证 shader（着色器）时间更新、对象回收和销毁行为稳定喵。

#### 具体改动点喵

1. 在 renderer（渲染器）层维护统一的 `elapsedSeconds`（累计时间秒数）或直接使用 `performance.now()`（高精度时间戳）喵。  
2. 每帧给活跃恒星材质写入时间喵。  
3. 回收对象时重置关键 uniform（着色器统一变量）喵。  
4. `dispose()`（资源销毁）时正确释放：
   - geometry（几何体）喵。  
   - material（材质）喵。  
   - fallback texture（回退纹理）喵。

#### 完成标准喵

- 恒星动态效果不会因对象池复用而串状态喵。  
- 材质和几何体在 dispose（销毁）后没有明显资源泄漏风险喵。

---

### 任务 6：必要时微调 LOD（细节层级）配置喵

**文件**：`web/src/rendering/subsystems/lodSystem.ts` 喵。  
**职责**：只在当前阈值明显不适合程序化方案时才调整喵。

#### 可能的改动点喵

1. 为恒星增加更明确的“完整程序化 / 简化程序化 / 极远景回退”阈值喵。  
2. 重新审视 `star.thresholds`（恒星阈值数组）是否过于贴近旧贴图逻辑喵。  
3. 保持 `allowHidden: false`（恒星不隐藏）不变喵，除非用户另行要求喵。

#### 完成标准喵

- 程序化恒星的视觉层级切换自然喵。  
- 不出现“刚放大一点就突然完全换风格”的明显跳变喵。

---

### 任务 7：清理旧贴图主路径并保留兼容注释喵

**文件**：  
- `web/src/rendering/layers/celestial/renderers/starRenderer.ts` 喵。  
- `web/src/net/snapshotWs.ts`（仅注释层面，若需要）喵。  
- 可能涉及计划文档或结构文档喵。

#### 具体改动点喵

1. 删除或降级 `surfaceTexturePath`（旧恒星贴图路径）的主流程调用喵。  
2. 在注释中明确：
   - 该字段为旧数据兼容/回退用途喵。  
   - 程序化恒星现在是主路径喵。  
3. 不建议本阶段删除数据字段本身喵。

#### 完成标准喵

- 阅读代码的人能明确知道旧贴图路径已经不是主路线喵。  
- 兼容信息仍然保留，避免误删后端字段喵。

---

## 12. 推荐实施顺序喵

建议严格按下面顺序推进喵：

1. `starProfile.ts`（恒星渲染配置映射）喵。  
2. `starMaterial.ts` / shader（恒星材质与着色器）喵。  
3. `starRenderer.ts` 主路径替换喵。  
4. 远景 fallback（回退显示）喵。  
5. 时间与资源生命周期管理喵。  
6. 需要时再调 `lodSystem.ts`（细节层级系统）喵。  
7. 最后补注释与清理旧贴图主路径喵。

---

## 13. 每步完成后的人工验收点喵

### 验收点 A：静态程序化恒星喵

- 恒星已不再读取普通位图作为主显示喵。  
- 放大后表面不会出现明显像素模糊喵。  
- 不同 `starTypeId`（恒星类型 ID）看起来明显有风格差异喵。

### 验收点 B：动态恒星效果喵

- 能看到表面缓慢流动喵。  
- 能看到轻微脉动或辉光变化喵。  
- 不同类型恒星动态幅度不同喵。

### 验收点 C：LOD 与性能喵

- 远景恒星不会全部跑满特效喵。  
- 大量恒星同时出现时没有明显卡顿恶化喵。  
- 选中状态下恒星仍然稳定可见喵。

---

## 14. 任务完成后建议同步更新的文档喵

完成实现后建议同步更新这些文档喵：

- `0-docs/日志/YYYY-MM-DD.md`（当日工作日志）喵。  
- `web/src/文件结构.md`（若新增 `starProfile.ts`、`starMaterial.ts` 需要补目录说明）喵。  
- 当前计划文件本身，勾选已完成项并记录最终方案喵。
