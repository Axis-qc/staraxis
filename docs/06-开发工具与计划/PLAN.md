# StarAxis 多人实时经济系统实施计划

> 本计划详细到每个文件的创建/修改步骤，可直接按顺序执行喵。

## 阶段一：科技研究系统（第1-3天）

### 目标
实现国家级别的科技研究队列，玩家可以开始研究科技，服务器实时推进进度，前端显示进度条。

---

### Day 1：科技配置与定义（2-3小时）

#### 任务1.1：创建科技配置目录和文件

**文件路径**：`assets/tech/technologies.json`（新建）

**内容**（可直接复制）：
```json
[
    {
        "id": "BASIC_COLONIZATION",
        "nameKey": "tech.basic_colonization.name",
        "descriptionKey": "tech.basic_colonization.desc",
        "category": "COLONIZATION",
        "tier": 1,
        "baseResearchPoints": 3600,
        "prerequisites": [],
        "unlocks": {
            "buildings": ["OUTPOST_HABITAT"],
            "components": ["BASIC_COLONY_MODULE"]
        }
    },
    {
        "id": "IMPROVED_MINING",
        "nameKey": "tech.improved_mining.name",
        "descriptionKey": "tech.improved_mining.desc",
        "category": "INDUSTRY",
        "tier": 1,
        "baseResearchPoints": 7200,
        "prerequisites": ["BASIC_COLONIZATION"],
        "unlocks": {
            "buildings": ["MINING_COMPLEX"],
            "modules": ["MINING_LASER_BASIC"]
        },
        "effects": {
            "miningEfficiency": 0.15
        }
    },
    {
        "id": "ORBITAL_CONSTRUCTION",
        "nameKey": "tech.orbital_construction.name",
        "descriptionKey": "tech.orbital_construction.desc",
        "category": "ENGINEERING",
        "tier": 2,
        "baseResearchPoints": 14400,
        "prerequisites": ["IMPROVED_MINING"],
        "unlocks": {
            "stations": ["ORBITAL_DOCKYARD"],
            "shipModules": ["CARGO_HOLD_BASIC"]
        }
    },
    {
        "id": "BASIC_LOGISTICS",
        "nameKey": "tech.basic_logistics.name",
        "descriptionKey": "tech.basic_logistics.desc",
        "category": "ECONOMICS",
        "tier": 1,
        "baseResearchPoints": 5400,
        "prerequisites": ["BASIC_COLONIZATION"],
        "unlocks": {
            "shipTypes": ["CARGO_SHUTTLE"],
            "components": ["NAVIGATION_BASIC"]
        },
        "effects": {
            "cargoCapacityBonus": 0.10,
            "transportSpeedBonus": 0.05
        }
    },
    {
        "id": "ADVANCED_LOGISTICS",
        "nameKey": "tech.advanced_logistics.name",
        "descriptionKey": "tech.advanced_logistics.desc",
        "category": "ECONOMICS",
        "tier": 2,
        "baseResearchPoints": 18000,
        "prerequisites": ["BASIC_LOGISTICS", "ORBITAL_CONSTRUCTION"],
        "unlocks": {
            "shipTypes": ["HEAVY_TRANSPORT"],
            "stations": ["TRADING_HUB"]
        },
        "effects": {
            "cargoCapacityBonus": 0.25,
            "transportSpeedBonus": 0.15,
            "loadingTimeReduction": 0.20
        }
    },
    {
        "id": "WARP_DRIVE_BASIC",
        "nameKey": "tech.warp_drive_basic.name",
        "descriptionKey": "tech.warp_drive_basic.desc",
        "category": "PROPULSION",
        "tier": 2,
        "baseResearchPoints": 21600,
        "prerequisites": ["ORBITAL_CONSTRUCTION"],
        "unlocks": {
            "shipModules": ["WARP_ENGINE_MK1"]
        },
        "effects": {
            "interstellarSpeed": 1.0
        }
    }
]
```

**执行命令**：
```bash
mkdir -p assets/tech
touch assets/tech/technologies.json
# 粘贴上述内容
```

---

#### 任务1.2：扩展 TechDef.java

**文件路径**：`game/src/main/java/staraxis/game/tech/TechDef.java`（修改）

**新增字段**（添加到现有类中）：
```java
    /**
     * 科技领域分类喵。
     * 例如：COLONIZATION, INDUSTRY, ENGINEERING, ECONOMICS, PROPULSION, MILITARY
     */
    public String category;

    /**
     * 科技等级（1-5），影响前置条件复杂度喵。
     */
    public int tier;

    /**
     * 研究所需的研究点数（基础值）喵。
     * 实际研究时间 = baseResearchPoints / 实验室产出效率
     */
    public double baseResearchPoints;

    /**
     * 前置科技ID列表喵。
     */
    public List<String> prerequisites = new ArrayList<>();

    /**
     * 解锁内容喵。
     * key: 类型（buildings, stations, shipTypes, components, modules, shipModules）
     * value: 该类型下解锁的ID列表
     */
    public Map<String, List<String>> unlocks = new HashMap<>();

    /**
     * 科技效果（全局加成）喵。
     * key: 效果类型
     * value: 加成数值（0.15 = 15%加成）
     */
    public Map<String, Double> effects = new HashMap<>();

    /**
     * 检查指定科技列表是否满足本科技的前置条件喵。
     *
     * @param unlockedTechs 已解锁科技ID集合
     * @return 如果所有前置科技都已解锁，返回true喵
     */
    public boolean arePrerequisitesMet(Set<String> unlockedTechs) {
        if (prerequisites == null || prerequisites.isEmpty()) {
            return true;
        }
        for (String prereq : prerequisites) {
            if (!unlockedTechs.contains(prereq)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取本科技解锁的指定类型内容喵。
     *
     * @param unlockType 解锁类型
     * @return 该类型下解锁的ID列表，如果没有返回空列表喵
     */
    public List<String> getUnlocksByType(String unlockType) {
        if (unlocks == null) {
            return List.of();
        }
        return unlocks.getOrDefault(unlockType, List.of());
    }

    /**
     * 获取指定类型的效果值喵。
     *
     * @param effectType 效果类型
     * @return 效果值，如果不存在返回0喵
     */
    public double getEffect(String effectType) {
        if (effects == null) {
            return 0.0;
        }
        return effects.getOrDefault(effectType, 0.0);
    }
```

---

#### 任务1.3：创建 TechAssetRepository.java

**文件路径**：`game/src/main/java/staraxis/game/tech/TechAssetRepository.java`（新建）

**完整代码**：
```java
package staraxis.game.tech;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import staraxis.game.mod.ModManager;
import staraxis.game.mod.ModOrderRepository;

/**
 * TechAssetRepository（科技资产仓库）喵。
 *
 * 作用：
 * - 从 assets/tech/ 目录加载科技定义喵。
 * - 支持Mod覆盖和扩展喵。
 * - 提供科技查询和验证接口喵。
 */
public class TechAssetRepository {

    private final ObjectMapper objectMapper;

    private List<TechDef> technologies = List.of();
    private Map<String, TechDef> techMap = new HashMap<>();

    public TechAssetRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加载所有科技定义喵。
     */
    public void loadAll() {
        technologies = readMergedListByKey(
                "assets/tech/technologies.json",
                "tech/technologies.json",
                TechDef[].class,
                def -> def == null ? null : def.id);

        buildLookupMap();
    }

    /**
     * 构建快速查找映射喵。
     */
    private void buildLookupMap() {
        techMap.clear();
        for (TechDef def : technologies) {
            if (def.id != null && !def.id.isBlank()) {
                techMap.put(def.id, def);
            }
        }
    }

    /**
     * 获取所有科技定义喵。
     *
     * @return 不可修改的科技列表喵。
     */
    public List<TechDef> getAllTechnologies() {
        return Collections.unmodifiableList(technologies);
    }

    /**
     * 根据ID获取科技定义喵。
     *
     * @param techId 科技ID
     * @return 科技定义，如果不存在返回null喵
     */
    public TechDef getTech(String techId) {
        return techMap.get(techId);
    }

    /**
     * 检查科技ID是否有效喵。
     *
     * @param techId 科技ID
     * @return 如果存在该科技定义，返回true喵
     */
    public boolean isValidTech(String techId) {
        return techMap.containsKey(techId);
    }

    /**
     * 获取指定分类的所有科技喵。
     *
     * @param category 科技分类
     * @return 该分类下的科技列表喵
     */
    public List<TechDef> getTechsByCategory(String category) {
        List<TechDef> result = new ArrayList<>();
        for (TechDef def : technologies) {
            if (category.equals(def.category)) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 获取指定等级的所有科技喵。
     *
     * @param tier 科技等级
     * @return 该等级下的科技列表喵
     */
    public List<TechDef> getTechsByTier(int tier) {
        List<TechDef> result = new ArrayList<>();
        for (TechDef def : technologies) {
            if (def.tier == tier) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 获取玩家可研究的科技列表（前置条件已满足且未解锁）喵。
     *
     * @param unlockedTechs 已解锁科技集合
     * @return 可研究的科技列表喵
     */
    public List<TechDef> getAvailableTechs(Set<String> unlockedTechs) {
        List<TechDef> result = new ArrayList<>();
        for (TechDef def : technologies) {
            if (!unlockedTechs.contains(def.id) && def.arePrerequisitesMet(unlockedTechs)) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 读取基础配置与所有已启用Mod的配置并按ID覆盖合并喵。
     */
    private <T> List<T> readMergedListByKey(
            String basePath,
            String modRelativePath,
            Class<?> arrayClazz,
            Function<T, String> keyExtractor) {

        Map<String, T> mergedMap = new java.util.LinkedHashMap<>();

        // 1. 加载本体配置喵
        List<T> baseList = readList(basePath, arrayClazz);
        for (T item : baseList) {
            String key = keyExtractor.apply(item);
            if (key != null) {
                mergedMap.put(key, item);
            }
        }

        // 2. 加载所有已启用Mod的配置并覆盖喵
        ModManager modMgr = new ModManager(new ModOrderRepository());
        List<String> modIds = modMgr.listModIdsOrderedAndEnabled();

        for (String modId : modIds) {
            String modPath = "gamedata/mods/" + modId + "/" + modRelativePath;
            List<T> modList = readList(modPath, arrayClazz);
            for (T item : modList) {
                String key = keyExtractor.apply(item);
                if (key != null) {
                    mergedMap.put(key, item);
                }
            }
        }

        return new ArrayList<>(mergedMap.values());
    }

    /**
     * 读取JSON文件为对象列表喵。
     */
    private <T> List<T> readList(String path, Class<?> arrayClazz) {
        try {
            File file = new File(path);
            if (!file.isFile()) {
                System.out.println("[WARN TechAssetRepository] File not found: " + path);
                return List.of();
            }
            Object arr = objectMapper.readValue(file, arrayClazz);
            if (!(arr instanceof Object[])) {
                return List.of();
            }
            Object[] a = (Object[]) arr;
            ArrayList<T> out = new ArrayList<>(a.length);
            for (Object o : a) {
                @SuppressWarnings("unchecked")
                T t = (T) o;
                out.add(t);
            }
            return out;
        } catch (Exception e) {
            System.out.println("[ERROR TechAssetRepository] Failed to read " + path + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 验证所有科技定义是否有效喵。
     *
     * @return 如果所有定义都有效，返回true喵
     */
    public boolean validateAll() {
        boolean valid = true;

        for (TechDef def : technologies) {
            if (def.id == null || def.id.isBlank()) {
                System.out.println("[ERROR TechAssetRepository] Invalid TechDef: missing id");
                valid = false;
                continue;
            }
            if (def.nameKey == null || def.nameKey.isBlank()) {
                System.out.println("[ERROR TechAssetRepository] Tech " + def.id + ": missing nameKey");
                valid = false;
            }
            if (def.baseResearchPoints <= 0) {
                System.out.println("[ERROR TechAssetRepository] Tech " + def.id + ": invalid baseResearchPoints");
                valid = false;
            }
            // 验证前置科技是否存在
            if (def.prerequisites != null) {
                for (String prereq : def.prerequisites) {
                    if (!isValidTech(prereq)) {
                        System.out.println("[ERROR TechAssetRepository] Tech " + def.id + ": unknown prerequisite " + prereq);
                        valid = false;
                    }
                }
            }
        }

        return valid;
    }
}
```

---

### Day 2：国家研究队列（3-4小时）

#### 任务2.1：创建研究项目类

**文件路径**：`game/src/main/java/staraxis/game/tech/ResearchProject.java`（新建）

**完整代码**：
```java
package staraxis.game.tech;

/**
 * ResearchProject（研究项目）喵。
 *
 * 表示一个国家正在进行的一项科技研究喵。
 */
public class ResearchProject {

    /**
     * 项目唯一ID喵。
     */
    public final long projectId;

    /**
     * 研究的科技ID喵。
     */
    public final String techId;

    /**
     * 分配的研究实验室ID喵。
     */
    public final long labId;

    /**
     * 总研究点数需求喵。
     */
    public final double totalResearchPoints;

    /**
     * 已完成研究点数喵。
     */
    public double completedResearchPoints;

    /**
     * 研究开始时间（游戏内总秒数）喵。
     */
    public final double startTimeSeconds;

    /**
     * 研究效率加成（1.0 = 100%，来自实验室和科学家）喵。
     */
    public double efficiencyMultiplier;

    public ResearchProject(long projectId, String techId, long labId,
                           double totalResearchPoints, double startTimeSeconds) {
        this.projectId = projectId;
        this.techId = techId;
        this.labId = labId;
        this.totalResearchPoints = totalResearchPoints;
        this.startTimeSeconds = startTimeSeconds;
        this.completedResearchPoints = 0;
        this.efficiencyMultiplier = 1.0;
    }

    /**
     * 获取研究进度百分比（0.0 ~ 100.0）喵。
     */
    public double getProgressPercent() {
        if (totalResearchPoints <= 0) {
            return 100.0;
        }
        return (completedResearchPoints / totalResearchPoints) * 100.0;
    }

    /**
     * 推进研究进度喵。
     *
     * @param researchPoints 本次tick产生的研究点数
     * @return 如果研究完成，返回true喵
     */
    public boolean progress(double researchPoints) {
        completedResearchPoints += researchPoints * efficiencyMultiplier;
        return isComplete();
    }

    /**
     * 检查研究是否已完成喵。
     */
    public boolean isComplete() {
        return completedResearchPoints >= totalResearchPoints;
    }

    /**
     * 获取剩余所需研究点数喵。
     */
    public double getRemainingPoints() {
        return Math.max(0, totalResearchPoints - completedResearchPoints);
    }

    /**
     * 预估剩余时间（秒）喵。
     *
     * @param researchOutputPerSecond 当前研究产出速率（点/秒）
     * @return 预估剩余秒数，如果产出为0返回-1喵
     */
    public double estimateRemainingSeconds(double researchOutputPerSecond) {
        if (researchOutputPerSecond <= 0) {
            return -1;
        }
        double effectiveOutput = researchOutputPerSecond * efficiencyMultiplier;
        return getRemainingPoints() / effectiveOutput;
    }
}
```

---

#### 任务2.2：创建研究实验室类

**文件路径**：`game/src/main/java/staraxis/game/tech/ResearchLab.java`（新建）

**完整代码**：
```java
package staraxis.game.tech;

/**
 * ResearchLab（研究实验室）喵。
 *
 * 执行实际研究的设施，可以是行星上的研究所或空间站的研究模块喵。
 */
public class ResearchLab {

    /**
     * 实验室ID（关联到实体ID）喵。
     */
    public final long labId;

    /**
     * 实验室名称喵。
     */
    public String name;

    /**
     * 所属实体ID（行星/空间站）喵。
     */
    public long hostEntityId;

    /**
     * 基础研究产出（点/游戏日）喵。
     * 游戏日 = 86400秒
     */
    public double baseResearchOutputPerDay;

    /**
     * 效率加成（科学家技能、设施等级等）喵。
     */
    public double efficiencyBonus;

    /**
     * 当前活跃的研究项目ID，0表示空闲喵。
     */
    public long activeProjectId;

    /**
     * 是否运行中喵。
     */
    public boolean isActive;

    public ResearchLab(long labId, String name, long hostEntityId) {
        this.labId = labId;
        this.name = name;
        this.hostEntityId = hostEntityId;
        this.baseResearchOutputPerDay = 100.0; // 默认值喵
        this.efficiencyBonus = 0.0;
        this.activeProjectId = 0;
        this.isActive = true;
    }

    /**
     * 计算实际研究产出（点/秒）喵。
     */
    public double getResearchOutputPerSecond() {
        if (!isActive) {
            return 0;
        }
        double effectiveOutput = baseResearchOutputPerDay * (1.0 + efficiencyBonus);
        return effectiveOutput / 86400.0; // 转换为每秒产出喵
    }

    /**
     * 检查实验室是否空闲喵。
     */
    public boolean isIdle() {
        return isActive && activeProjectId == 0;
    }

    /**
     * 分配研究项目喵。
     */
    public void assignProject(long projectId) {
        this.activeProjectId = projectId;
    }

    /**
     * 清空当前项目（研究完成或取消）喵。
     */
    public void clearProject() {
        this.activeProjectId = 0;
    }
}
```

---

#### 任务2.3：创建国家研究队列（核心类）

**文件路径**：`game/src/main/java/staraxis/game/tech/NationalResearchQueue.java`（新建）

**完整代码**：
```java
package staraxis.game.tech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import staraxis.game.state.WorldState;

/**
 * NationalResearchQueue（国家研究队列）喵。
 *
 * 管理一个国家的所有研究活动：
 * - 多个研究实验室（并行研究）
 * - 活跃研究项目
 * - 已解锁科技
 * - 科技效果加成
 *
 * 存储在 NationState 中，随国家持久化喵。
 */
public class NationalResearchQueue {

    /**
     * 国家ID喵。
     */
    public final String nationId;

    /**
     * 研究实验室列表（key: labId）喵。
     */
    public final Map<Long, ResearchLab> labs = new HashMap<>();

    /**
     * 活跃研究项目（key: projectId）喵。
     */
    public final Map<Long, ResearchProject> activeProjects = new HashMap<>();

    /**
     * 已解锁科技ID集合喵。
     */
    public final Set<String> unlockedTechs = new HashSet<>();

    /**
     * 科技效果累计（key: effectType, value: totalBonus）喵。
     */
    public final Map<String, Double> cumulativeEffects = new HashMap<>();

    /**
     * 项目ID生成器喵。
     */
    private final AtomicLong projectIdGenerator = new AtomicLong(1);

    /**
     * 默认构造函数（用于反序列化）喵。
     */
    public NationalResearchQueue() {
        this.nationId = "";
    }

    public NationalResearchQueue(String nationId) {
        this.nationId = nationId;
    }

    /**
     * 添加研究实验室喵。
     *
     * @param lab 实验室
     */
    public void addLab(ResearchLab lab) {
        labs.put(lab.labId, lab);
    }

    /**
     * 移除研究实验室喵。
     *
     * @param labId 实验室ID
     * @return 如果实验室有活跃项目，返回false喵
     */
    public boolean removeLab(long labId) {
        ResearchLab lab = labs.get(labId);
        if (lab == null) {
            return true;
        }
        if (lab.activeProjectId != 0) {
            return false; // 有活跃项目，不能移除喵
        }
        labs.remove(labId);
        return true;
    }

    /**
     * 开始新的研究项目喵。
     *
     * @param techDef 科技定义
     * @param techAssetRepo 科技仓库
     * @return 项目ID，如果无法开始返回-1喵
     */
    public long startResearchProject(TechDef techDef, TechAssetRepository techAssetRepo) {
        // 检查是否已解锁
        if (unlockedTechs.contains(techDef.id)) {
            return -1;
        }

        // 检查前置条件
        if (!techDef.arePrerequisitesMet(unlockedTechs)) {
            return -1;
        }

        // 检查是否已在研究中
        for (ResearchProject project : activeProjects.values()) {
            if (project.techId.equals(techDef.id)) {
                return -1; // 已在研究中喵
            }
        }

        // 寻找空闲实验室
        ResearchLab idleLab = findIdleLab();
        if (idleLab == null) {
            return -1; // 没有空闲实验室喵
        }

        // 创建项目
        long projectId = projectIdGenerator.getAndIncrement();
        ResearchProject project = new ResearchProject(
                projectId,
                techDef.id,
                idleLab.labId,
                techDef.baseResearchPoints,
                0 // 时间戳由tick时设置喵
        );

        // 分配项目到实验室
        idleLab.assignProject(projectId);
        activeProjects.put(projectId, project);

        return projectId;
    }

    /**
     * 寻找空闲实验室喵。
     */
    private ResearchLab findIdleLab() {
        for (ResearchLab lab : labs.values()) {
            if (lab.isIdle()) {
                return lab;
            }
        }
        return null;
    }

    /**
     * 获取空闲实验室数量喵。
     */
    public int getIdleLabCount() {
        int count = 0;
        for (ResearchLab lab : labs.values()) {
            if (lab.isIdle()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 取消研究项目喵。
     *
     * @param projectId 项目ID
     * @return 是否成功取消喵
     */
    public boolean cancelProject(long projectId) {
        ResearchProject project = activeProjects.get(projectId);
        if (project == null) {
            return false;
        }

        // 释放实验室
        ResearchLab lab = labs.get(project.labId);
        if (lab != null) {
            lab.clearProject();
        }

        activeProjects.remove(projectId);
        return true;
    }

    /**
     * 每tick推进所有活跃项目喵。
     *
     * @param worldState 世界状态
     * @param dtGameSeconds 本tick的游戏秒数
     * @return 本tick完成的项目列表喵
     */
    public List<ResearchProject> tickResearch(WorldState worldState, double dtGameSeconds) {
        List<ResearchProject> completed = new ArrayList<>();

        for (ResearchProject project : new ArrayList<>(activeProjects.values())) {
            ResearchLab lab = labs.get(project.labId);
            if (lab == null || !lab.isActive) {
                continue; // 实验室不可用喵
            }

            // 更新项目效率（可能因科学家变动而改变）
            project.efficiencyMultiplier = 1.0 + lab.efficiencyBonus;

            // 计算本tick产出
            double researchOutput = lab.getResearchOutputPerSecond() * dtGameSeconds;

            // 推进进度
            boolean isComplete = project.progress(researchOutput);

            if (isComplete) {
                completed.add(project);
            }
        }

        // 处理完成的项目
        for (ResearchProject project : completed) {
            completeProject(project);
        }

        return completed;
    }

    /**
     * 完成研究项目喵。
     */
    private void completeProject(ResearchProject project) {
        // 解锁科技
        unlockedTechs.add(project.techId);

        // 释放实验室
        ResearchLab lab = labs.get(project.labId);
        if (lab != null) {
            lab.clearProject();
        }

        // 从活跃列表移除
        activeProjects.remove(project.projectId);

        // 累积科技效果
        TechDef tech = TechAssetRepository.get(project.techId); // 需要静态访问或注入喵
        if (tech != null && tech.effects != null) {
            for (Map.Entry<String, Double> effect : tech.effects.entrySet()) {
                cumulativeEffects.merge(effect.getKey(), effect.getValue(), Double::sum);
            }
        }
    }

    /**
     * 获取指定效果的累计加成喵。
     *
     * @param effectType 效果类型
     * @return 累计加成值喵
     */
    public double getEffectBonus(String effectType) {
        return cumulativeEffects.getOrDefault(effectType, 0.0);
    }

    /**
     * 获取所有活跃项目的只读列表喵。
     */
    public List<ResearchProject> getActiveProjects() {
        return Collections.unmodifiableList(new ArrayList<>(activeProjects.values()));
    }

    /**
     * 获取已解锁科技的只读集合喵。
     */
    public Set<String> getUnlockedTechs() {
        return Collections.unmodifiableSet(new HashSet<>(unlockedTechs));
    }

    /**
     * 检查科技是否已解锁喵。
     */
    public boolean isTechUnlocked(String techId) {
        return unlockedTechs.contains(techId);
    }

    /**
     * 获取指定项目的进度信息喵。
     */
    public ResearchProject getProject(long projectId) {
        return activeProjects.get(projectId);
    }
}
```

---

#### 任务2.4：修改 NationState.java 添加研究队列

**文件路径**：`game/src/main/java/staraxis/game/nation/NationState.java`（修改）

**在类中添加字段**（在 `ownedEntityIdsByType` 字段后添加）：
```java
    /**
     * 国家研究队列（科技研究系统）喵。
     * 管理所有研究实验室、活跃项目和已解锁科技喵。
     */
    public final staraxis.game.tech.NationalResearchQueue researchQueue;
```

**修改构造函数**：
```java
    /**
     * 构造函数：创建指定ID的国家运行时状态喵。
     *
     * @param nationId 国家唯一标识
     */
    public NationState(String nationId) {
        this.nationId = nationId;
        this.researchQueue = new staraxis.game.tech.NationalResearchQueue(nationId);
    }
```

---

### Day 3：命令、API和前端（4-5小时）

#### 任务3.1：创建开始研究命令

**文件路径**：`game/src/main/java/staraxis/game/command/StartResearchCommand.java`（新建）

**完整代码**：
```java
package staraxis.game.command;

/**
 * StartResearchCommand（开始研究命令）喵。
 *
 * 用于让一个国家开始研究指定科技喵。
 * 执行前会验证：科技是否存在、前置条件是否满足、是否有空闲实验室喵。
 */
public class StartResearchCommand extends Command {

    private final String nationId;
    private final String techId;

    public StartResearchCommand(String nationId, String techId) {
        super("startResearch");
        this.nationId = nationId;
        this.techId = techId;
    }

    public String getNationId() {
        return nationId;
    }

    public String getTechId() {
        return techId;
    }
}
```

---

#### 任务3.2：创建开始研究处理器

**文件路径**：`game/src/main/java/staraxis/game/command/StartResearchHandler.java`（新建）

**完整代码**：
```java
package staraxis.game.command;

import staraxis.game.state.WorldState;
import staraxis.game.tech.TechAssetRepository;
import staraxis.game.tech.TechDef;
import staraxis.game.tech.NationalResearchQueue;

/**
 * StartResearchHandler（开始研究处理器）喵。
 *
 * 处理 StartResearchCommand，验证条件并开始研究项目喵。
 */
public class StartResearchHandler implements CommandHandler<StartResearchCommand> {

    private final TechAssetRepository techAssetRepo;

    public StartResearchHandler(TechAssetRepository techAssetRepo) {
        this.techAssetRepo = techAssetRepo;
    }

    @Override
    public void handle(StartResearchCommand command, WorldState worldState, double dtGameHours) {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        if (worldState == null) {
            throw new IllegalArgumentException("world_state_required");
        }

        String nationId = command.getNationId();
        String techId = command.getTechId();

        // 验证国家存在
        var nation = worldState.nationManager.getNationState(nationId);
        if (nation == null) {
            throw new IllegalArgumentException("nation_not_found: " + nationId);
        }

        // 验证科技存在
        TechDef tech = techAssetRepo.getTech(techId);
        if (tech == null) {
            throw new IllegalArgumentException("tech_not_found: " + techId);
        }

        // 尝试开始研究
        NationalResearchQueue queue = nation.researchQueue;
        long projectId = queue.startResearchProject(tech, techAssetRepo);

        if (projectId < 0) {
            throw new IllegalStateException("cannot_start_research: " + techId);
        }

        System.out.println("[Research] Nation " + nationId + " started research on " + techId +
                ", projectId=" + projectId);
    }
}
```

---

#### 任务3.3：创建研究API

**文件路径**：`webnet/src/main/java/staraxis/webnet/api/ResearchApi.java`（新建）

**完整代码**：
```java
package staraxis.webnet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.StartResearchCommand;
import staraxis.game.tech.NationalResearchQueue;
import staraxis.game.tech.ResearchProject;
import staraxis.game.tech.TechAssetRepository;
import staraxis.game.tech.TechDef;
import staraxis.webnet.game.GameSessions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ResearchApi（研究系统API）喵。
 *
 * 提供科技研究相关的HTTP接口喵。
 */
public final class ResearchApi {

    private ResearchApi() {
    }

    /**
     * GET /api/research/available
     * 获取玩家可研究的科技列表喵。
     */
    public static Map<String, Object> handleGetAvailableTechs(ObjectMapper objectMapper,
                                                               String worldId,
                                                               String nationId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        var nation = runtime.getWorldStateForSimOnly().nationManager.getNationState(nationId);
        if (nation == null) {
            return Map.of("ok", false, "error", "nation_not_found");
        }

        TechAssetRepository techRepo = runtime.getTechAssetRepository();
        if (techRepo == null) {
            return Map.of("ok", false, "error", "tech_system_not_initialized");
        }

        Set<String> unlocked = nation.researchQueue.getUnlockedTechs();
        List<TechDef> available = techRepo.getAvailableTechs(unlocked);

        List<Map<String, Object>> techList = new ArrayList<>();
        for (TechDef tech : available) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", tech.id);
            t.put("nameKey", tech.nameKey);
            t.put("descriptionKey", tech.descriptionKey);
            t.put("category", tech.category);
            t.put("tier", tech.tier);
            t.put("baseResearchPoints", tech.baseResearchPoints);
            t.put("prerequisites", tech.prerequisites);
            techList.add(t);
        }

        return Map.of(
                "ok", true,
                "availableTechs", techList,
                "unlockedCount", unlocked.size()
        );
    }

    /**
     * GET /api/research/active
     * 获取玩家活跃的研究项目喵。
     */
    public static Map<String, Object> handleGetActiveProjects(ObjectMapper objectMapper,
                                                               String worldId,
                                                               String nationId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        var nation = runtime.getWorldStateForSimOnly().nationManager.getNationState(nationId);
        if (nation == null) {
            return Map.of("ok", false, "error", "nation_not_found");
        }

        NationalResearchQueue queue = nation.researchQueue;
        List<ResearchProject> projects = queue.getActiveProjects();

        List<Map<String, Object>> projectList = new ArrayList<>();
        for (ResearchProject project : projects) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("projectId", project.projectId);
            p.put("techId", project.techId);
            p.put("progressPercent", Math.round(project.getProgressPercent() * 100.0) / 100.0);
            p.put("completedPoints", Math.round(project.completedResearchPoints * 100.0) / 100.0);
            p.put("totalPoints", Math.round(project.totalResearchPoints * 100.0) / 100.0);

            // 获取预估剩余时间
            double outputPerSec = queue.getLabs().get(project.labId).getResearchOutputPerSecond();
            double remainingSec = project.estimateRemainingSeconds(outputPerSec);
            p.put("estimatedRemainingSeconds", Math.round(remainingSec));

            projectList.add(p);
        }

        return Map.of(
                "ok", true,
                "activeProjects", projectList,
                "idleLabCount", queue.getIdleLabCount()
        );
    }

    /**
     * POST /api/research/start
     * 开始研究指定科技喵。
     */
    public static Map<String, Object> handleStartResearch(ObjectMapper objectMapper,
                                                           String worldId,
                                                           Map<String, Object> req) {
        String nationId = req.get("nationId") == null ? null : String.valueOf(req.get("nationId"));
        String techId = req.get("techId") == null ? null : String.valueOf(req.get("techId"));

        if (nationId == null || nationId.isBlank()) {
            return Map.of("ok", false, "error", "nationId_required");
        }
        if (techId == null || techId.isBlank()) {
            return Map.of("ok", false, "error", "techId_required");
        }

        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        // 提交命令到游戏层执行喵
        StartResearchCommand command = new StartResearchCommand(nationId, techId);
        runtime.submitCommand(command);

        return Map.of(
                "ok", true,
                "message", "research_started",
                "techId", techId
        );
    }

    /**
     * GET /api/research/unlocked
     * 获取已解锁科技列表喵。
     */
    public static Map<String, Object> handleGetUnlockedTechs(ObjectMapper objectMapper,
                                                              String worldId,
                                                              String nationId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        var nation = runtime.getWorldStateForSimOnly().nationManager.getNationState(nationId);
        if (nation == null) {
            return Map.of("ok", false, "error", "nation_not_found");
        }

        Set<String> unlocked = nation.researchQueue.getUnlockedTechs();

        // 获取科技详情
        TechAssetRepository techRepo = runtime.getTechAssetRepository();
        List<Map<String, Object>> techDetails = new ArrayList<>();
        for (String techId : unlocked) {
            TechDef tech = techRepo.getTech(techId);
            if (tech != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", tech.id);
                t.put("nameKey", tech.nameKey);
                t.put("category", tech.category);
                t.put("tier", tech.tier);
                techDetails.add(t);
            }
        }

        return Map.of(
                "ok", true,
                "unlockedTechs", unlocked,
                "techDetails", techDetails
        );
    }
}
```

---

#### 任务3.4：在 StarAxisGameRuntime 初始化科技系统

需要在 `StarAxisGameRuntime` 中添加：
1. `TechAssetRepository` 字段和getter
2. 在 `newGame()` 中加载科技配置
3. 在 `update()` 中调用研究队列tick
4. 在 `submitCommand()` 中注册 `StartResearchHandler`

由于文件较长，这里给出关键修改点：

**添加字段**：
```java
private TechAssetRepository techAssetRepository;

public TechAssetRepository getTechAssetRepository() {
    return techAssetRepository;
}
```

**在 newGame() 中初始化**：
```java
// 加载科技定义喵
techAssetRepository = new TechAssetRepository(objectMapper);
techAssetRepository.loadAll();
if (!techAssetRepository.validateAll()) {
    System.out.println("[WARN] Some tech definitions failed validation");
}
```

**给新国家添加默认实验室**（在出生/创建国家时）：
```java
// 给新国家一个默认研究实验室喵
ResearchLab defaultLab = new ResearchLab(
    worldState.generateEntityId(),
    "Main Research Lab",
    nationState.capitalPlanetEntityId // 或首都空间站
);
defaultLab.baseResearchOutputPerDay = 100.0;
nationState.researchQueue.addLab(defaultLab);
```

---

#### 任务3.5：前端 Vue 组件

**文件路径**：`web/src/components/research/TechTreePanel.vue`（新建）

这是一个基础框架，需要配合你的前端技术栈完善：

```vue
<template>
  <div class="tech-tree-panel">
    <h2>科技研究</h2>

    <!-- 活跃项目 -->
    <section class="active-projects">
      <h3>进行中 ({{ activeProjects.length }})</h3>
      <div v-for="project in activeProjects" :key="project.projectId" class="project-card">
        <div class="tech-name">{{ $t(project.techId) }}</div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: project.progressPercent + '%' }"></div>
        </div>
        <div class="progress-text">
          {{ project.progressPercent.toFixed(1) }}% -
          剩余 {{ formatTime(project.estimatedRemainingSeconds) }}
        </div>
      </div>
      <div v-if="activeProjects.length === 0" class="empty-state">
        没有进行中的研究项目
      </div>
    </section>

    <!-- 可研究科技 -->
    <section class="available-techs">
      <h3>可研究 ({{ availableTechs.length }})</h3>
      <div v-for="tech in availableTechs" :key="tech.id" class="tech-card">
        <div class="tech-header">
          <span class="tech-name">{{ $t(tech.nameKey) }}</span>
          <span class="tech-category">{{ tech.category }}</span>
        </div>
        <div class="tech-desc">{{ $t(tech.descriptionKey) }}</div>
        <div class="tech-meta">
          <span>Tier {{ tech.tier }}</span>
          <span>{{ tech.baseResearchPoints }} 研究点</span>
        </div>
        <button
          @click="startResearch(tech.id)"
          :disabled="idleLabCount === 0"
          class="research-btn"
        >
          {{ idleLabCount > 0 ? '开始研究' : '无空闲实验室' }}
        </button>
      </div>
    </section>

    <!-- 已解锁科技 -->
    <section class="unlocked-techs">
      <h3>已解锁 ({{ unlockedTechs.length }})</h3>
      <div class="tech-tags">
        <span v-for="tech in unlockedTechs" :key="tech.id" class="tech-tag">
          {{ $t(tech.nameKey) }}
        </span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  worldId: string
  nationId: string
}>()

const activeProjects = ref([])
const availableTechs = ref([])
const unlockedTechs = ref([])
const idleLabCount = ref(0)

let refreshInterval: number | null = null

async function fetchResearchData() {
  // 并行获取数据喵
  const [activeRes, availableRes, unlockedRes] = await Promise.all([
    fetch(`/api/research/active?worldId=${props.worldId}&nationId=${props.nationId}`).then(r => r.json()),
    fetch(`/api/research/available?worldId=${props.worldId}&nationId=${props.nationId}`).then(r => r.json()),
    fetch(`/api/research/unlocked?worldId=${props.worldId}&nationId=${props.nationId}`).then(r => r.json())
  ])

  if (activeRes.ok) {
    activeProjects.value = activeRes.activeProjects
    idleLabCount.value = activeRes.idleLabCount
  }
  if (availableRes.ok) {
    availableTechs.value = availableRes.availableTechs
  }
  if (unlockedRes.ok) {
    unlockedTechs.value = unlockedRes.techDetails
  }
}

async function startResearch(techId: string) {
  const res = await fetch(`/api/research/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      worldId: props.worldId,
      nationId: props.nationId,
      techId
    })
  }).then(r => r.json())

  if (res.ok) {
    await fetchResearchData() // 刷新数据喵
  } else {
    alert('无法开始研究: ' + res.error)
  }
}

function formatTime(seconds: number): string {
  if (seconds < 0) return '计算中...'
  const hours = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  if (hours > 0) return `${hours}小时${mins}分钟`
  return `${mins}分钟`
}

onMounted(() => {
  fetchResearchData()
  // 每5秒刷新一次进度喵
  refreshInterval = window.setInterval(fetchResearchData, 5000)
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<style scoped>
.tech-tree-panel {
  padding: 20px;
  max-width: 800px;
}

.project-card, .tech-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  background: var(--panel-bg);
}

.progress-bar {
  height: 20px;
  background: rgba(0,0,0,0.3);
  border-radius: 10px;
  overflow: hidden;
  margin: 8px 0;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4A90E2, #67B8DE);
  transition: width 0.3s ease;
}

.research-btn {
  margin-top: 8px;
  padding: 8px 16px;
  background: #4A90E2;
  border: none;
  border-radius: 4px;
  color: white;
  cursor: pointer;
}

.research-btn:disabled {
  background: #666;
  cursor: not-allowed;
}

.tech-tag {
  display: inline-block;
  padding: 4px 8px;
  margin: 4px;
  background: rgba(74, 144, 226, 0.2);
  border: 1px solid #4A90E2;
  border-radius: 4px;
  font-size: 12px;
}
</style>
```

---

## 阶段二：制造设施系统（第4-6天）

### 目标
实现制造设施，科技解锁后可以建造设施，设施可以执行制造订单（真实时间生产）。

---

### Day 4：制造设施基础（3-4小时）

#### 任务4.1：创建制造设施定义配置

**文件路径**：`assets/industry/facility-types.json`（新建）

```json
[
    {
        "facilityTypeId": "BASIC_FACTORY",
        "nameKey": "facility.basic_factory.name",
        "descriptionKey": "facility.basic_factory.desc",
        "category": "PLANETARY",
        "baseProductionPointsPerDay": 500,
        "allowedJobTypes": ["CONSUMER_GOODS", "INDUSTRIAL_COMPONENTS"],
        "constructionCost": {
            "BASIC_MATERIALS": 2000,
            "ENERGY": 1000
        },
        "constructionTimeDays": 1
    },
    {
        "facilityTypeId": "ORBITAL_DOCKYARD",
        "nameKey": "facility.orbital_dockyard.name",
        "descriptionKey": "facility.orbital_dockyard.desc",
        "category": "ORBITAL",
        "baseProductionPointsPerDay": 800,
        "allowedJobTypes": ["SHIP_CONSTRUCTION", "STATION_MODULES"],
        "constructionCost": {
            "BASIC_MATERIALS": 5000,
            "ADVANCED_COMPONENTS": 1000,
            "ENERGY": 3000
        },
        "constructionTimeDays": 3,
        "requiredTech": "ORBITAL_CONSTRUCTION"
    },
    {
        "facilityTypeId": "RESEARCH_LAB",
        "nameKey": "facility.research_lab.name",
        "descriptionKey": "facility.research_lab.desc",
        "category": "PLANETARY",
        "researchPointsPerDay": 100,
        "constructionCost": {
            "BASIC_MATERIALS": 1500,
            "RESEARCH_DATA": 500
        },
        "constructionTimeDays": 2
    }
]
```

---

#### 任务4.2：创建制造订单类

**文件路径**：`game/src/main/java/staraxis/game/industry/ManufacturingJob.java`（新建）

```java
package staraxis.game.industry;

import java.util.Map;

/**
 * ManufacturingJob（制造订单）喵。
 *
 * 表示一个正在进行的制造/生产任务喵。
 */
public class ManufacturingJob {

    public final long jobId;
    public final String jobType;           // "BUILDING", "SHIP", "COMPONENT"
    public final String blueprintId;       // 蓝图/设计ID
    public final int runs;                 // 生产批次

    public final long facilityId;          // 执行设施的实体ID

    // 生产需求
    public final double totalProductionPoints;
    public double completedProductionPoints;

    // 时间
    public final double startTimeSeconds;
    public double estimatedCompletionTime;

    // 状态
    public JobStatus status;

    // 输入原料（已托管到设施）
    public Map<String, Double> inputMaterials;

    public ManufacturingJob(long jobId, String jobType, String blueprintId, int runs,
                            long facilityId, double totalProductionPoints, double startTimeSeconds) {
        this.jobId = jobId;
        this.jobType = jobType;
        this.blueprintId = blueprintId;
        this.runs = runs;
        this.facilityId = facilityId;
        this.totalProductionPoints = totalProductionPoints;
        this.startTimeSeconds = startTimeSeconds;
        this.completedProductionPoints = 0;
        this.status = JobStatus.PENDING;
    }

    public double getProgressPercent() {
        if (totalProductionPoints <= 0) return 100.0;
        return (completedProductionPoints / totalProductionPoints) * 100.0;
    }

    public boolean progress(double productionPoints) {
        completedProductionPoints += productionPoints;
        if (completedProductionPoints >= totalProductionPoints) {
            status = JobStatus.COMPLETED;
            return true;
        }
        return false;
    }

    public boolean isComplete() {
        return status == JobStatus.COMPLETED;
    }

    public enum JobStatus {
        PENDING,      // 等待原料
        ACTIVE,       // 生产中
        COMPLETED,    // 完成等待取货
        DELIVERED,    // 已交付
        CANCELLED     // 已取消
    }
}
```

---

#### 任务4.3：创建制造设施定义类

**文件路径**：`game/src/main/java/staraxis/game/industry/FacilityTypeDef.java`（新建）

```java
package staraxis.game.industry;

import java.util.List;
import java.util.Map;

/**
 * FacilityTypeDef（制造设施类型定义）喵。
 *
 * 定义一种制造设施的属性，从配置加载喵。
 */
public class FacilityTypeDef {

    public String facilityTypeId;
    public String nameKey;
    public String descriptionKey;
    public String category;  // PLANETARY, ORBITAL, STATION

    // 生产能力
    public double baseProductionPointsPerDay;
    public Double researchPointsPerDay;  // 如果是研究实验室

    // 允许的生产类型
    public List<String> allowedJobTypes;

    // 建造成本
    public Map<String, Double> constructionCost;

    // 建造时间（游戏日）
    public double constructionTimeDays;

    // 需要的科技（可选）
    public String requiredTech;

    /**
     * 检查是否允许指定类型的生产任务喵。
     */
    public boolean allowsJobType(String jobType) {
        if (allowedJobTypes == null) return false;
        return allowedJobTypes.contains(jobType);
    }

    /**
     * 获取建造成本喵。
     */
    public Map<String, Double> getConstructionCost() {
        return constructionCost != null ? constructionCost : Map.of();
    }
}
```

---

#### 任务4.4：创建 IndustryAssetRepository

**文件路径**：`game/src/main/java/staraxis/game/industry/IndustryAssetRepository.java`（新建）

```java
package staraxis.game.industry;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;
import java.util.function.Function;

import staraxis.game.mod.ModManager;
import staraxis.game.mod.ModOrderRepository;

/**
 * IndustryAssetRepository（工业资产仓库）喵。
 *
 * 管理制造设施类型定义和蓝图定义喵。
 */
public class IndustryAssetRepository {

    private final ObjectMapper objectMapper;

    private List<FacilityTypeDef> facilityTypes = List.of();
    private Map<String, FacilityTypeDef> facilityTypeMap = new HashMap<>();

    public IndustryAssetRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void loadAll() {
        facilityTypes = readMergedListByKey(
                "assets/industry/facility-types.json",
                "industry/facility-types.json",
                FacilityTypeDef[].class,
                def -> def == null ? null : def.facilityTypeId);

        buildLookupMap();
    }

    private void buildLookupMap() {
        facilityTypeMap.clear();
        for (FacilityTypeDef def : facilityTypes) {
            if (def.facilityTypeId != null && !def.facilityTypeId.isBlank()) {
                facilityTypeMap.put(def.facilityTypeId, def);
            }
        }
    }

    public FacilityTypeDef getFacilityType(String facilityTypeId) {
        return facilityTypeMap.get(facilityTypeId);
    }

    public List<FacilityTypeDef> getAllFacilityTypes() {
        return Collections.unmodifiableList(facilityTypes);
    }

    public List<FacilityTypeDef> getAvailableFacilityTypes(Set<String> unlockedTechs) {
        List<FacilityTypeDef> available = new ArrayList<>();
        for (FacilityTypeDef def : facilityTypes) {
            if (def.requiredTech == null || def.requiredTech.isBlank()) {
                available.add(def);
            } else if (unlockedTechs.contains(def.requiredTech)) {
                available.add(def);
            }
        }
        return available;
    }

    private <T> List<T> readMergedListByKey(
            String basePath,
            String modRelativePath,
            Class<?> arrayClazz,
            Function<T, String> keyExtractor) {

        Map<String, T> mergedMap = new LinkedHashMap<>();

        List<T> baseList = readList(basePath, arrayClazz);
        for (T item : baseList) {
            String key = keyExtractor.apply(item);
            if (key != null) {
                mergedMap.put(key, item);
            }
        }

        ModManager modMgr = new ModManager(new ModOrderRepository());
        List<String> modIds = modMgr.listModIdsOrderedAndEnabled();

        for (String modId : modIds) {
            String modPath = "gamedata/mods/" + modId + "/" + modRelativePath;
            List<T> modList = readList(modPath, arrayClazz);
            for (T item : modList) {
                String key = keyExtractor.apply(item);
                if (key != null) {
                    mergedMap.put(key, item);
                }
            }
        }

        return new ArrayList<>(mergedMap.values());
    }

    private <T> List<T> readList(String path, Class<?> arrayClazz) {
        try {
            File file = new File(path);
            if (!file.isFile()) {
                System.out.println("[WARN IndustryAssetRepository] File not found: " + path);
                return List.of();
            }
            Object arr = objectMapper.readValue(file, arrayClazz);
            if (!(arr instanceof Object[])) {
                return List.of();
            }
            Object[] a = (Object[]) arr;
            ArrayList<T> out = new ArrayList<>(a.length);
            for (Object o : a) {
                @SuppressWarnings("unchecked")
                T t = (T) o;
                out.add(t);
            }
            return out;
        } catch (Exception e) {
            System.out.println("[ERROR IndustryAssetRepository] Failed to read " + path + ": " + e.getMessage());
            return List.of();
        }
    }
}
```

---

### Day 5：制造设施实体和管理（4-5小时）

#### 任务5.1：创建 ManufacturingFacility 实体

**文件路径**：`game/src/main/java/staraxis/game/industry/ManufacturingFacility.java`（新建）

```java
package staraxis.game.industry;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ManufacturingFacility（制造设施）喵。
 *
 * 执行实际制造任务的设施实体喵。
 */
public class ManufacturingFacility extends Entity {

    // 设施类型
    public String facilityTypeId;

    // 设施名称
    public String facilityName;

    // 输入原料缓存
    public Map<String, Double> inputBuffer = new HashMap<>();

    // 输出成品缓存
    public Map<String, Double> outputBuffer = new HashMap<>();

    // 活跃制造订单
    public final Map<Long, ManufacturingJob> activeJobs = new HashMap<>();

    // 订单ID生成器
    private final AtomicLong jobIdGenerator = new AtomicLong(1);

    // 设施效率加成
    public double efficiencyBonus = 0.0;

    // 是否正在运行
    public boolean isActive = true;

    public ManufacturingFacility() {
        this.entityType = EntityType.STATION; // 或专用类型
    }

    /**
     * 计算实际生产产出（点/秒）喵。
     */
    public double getProductionOutputPerSecond(FacilityTypeDef typeDef) {
        if (!isActive || typeDef == null) {
            return 0;
        }
        double baseOutput = typeDef.baseProductionPointsPerDay;
        double effectiveOutput = baseOutput * (1.0 + efficiencyBonus);
        return effectiveOutput / 86400.0; // 转换为每秒产出喵
    }

    /**
     * 创建新制造订单喵。
     */
    public ManufacturingJob createJob(String jobType, String blueprintId, int runs,
                                       double totalProductionPoints, double currentTimeSeconds) {
        FacilityTypeDef typeDef = getFacilityTypeDef(); // 需要外部提供或缓存喵

        // 检查设施是否允许此类型任务
        if (typeDef != null && !typeDef.allowsJobType(jobType)) {
            return null;
        }

        long jobId = jobIdGenerator.getAndIncrement();
        ManufacturingJob job = new ManufacturingJob(
                jobId, jobType, blueprintId, runs,
                this.entityId, totalProductionPoints, currentTimeSeconds
        );

        // 检查原料是否充足
        if (hasRequiredMaterials(job)) {
            job.status = ManufacturingJob.JobStatus.ACTIVE;
            consumeMaterials(job);
        } else {
            job.status = ManufacturingJob.JobStatus.PENDING;
        }

        activeJobs.put(jobId, job);
        return job;
    }

    /**
     * 检查是否有足够原料喵。
     */
    private boolean hasRequiredMaterials(ManufacturingJob job) {
        if (job.inputMaterials == null) return true;
        for (Map.Entry<String, Double> req : job.inputMaterials.entrySet()) {
            double available = inputBuffer.getOrDefault(req.getKey(), 0.0);
            if (available < req.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 消耗原料喵。
     */
    private void consumeMaterials(ManufacturingJob job) {
        if (job.inputMaterials == null) return;
        for (Map.Entry<String, Double> req : job.inputMaterials.entrySet()) {
            inputBuffer.merge(req.getKey(), -req.getValue(), Double::sum);
            if (inputBuffer.get(req.getKey()) <= 0) {
                inputBuffer.remove(req.getKey());
            }
        }
    }

    /**
     * 添加原料到输入缓存喵。
     */
    public void addInputMaterial(String resourceId, double amount) {
        inputBuffer.merge(resourceId, amount, Double::sum);

        // 检查是否有PENDING订单现在可以开始
        for (ManufacturingJob job : activeJobs.values()) {
            if (job.status == ManufacturingJob.JobStatus.PENDING && hasRequiredMaterials(job)) {
                job.status = ManufacturingJob.JobStatus.ACTIVE;
                consumeMaterials(job);
            }
        }
    }

    /**
     * 每tick推进所有活跃订单喵。
     */
    public List<ManufacturingJob> tickProduction(double dtGameSeconds, FacilityTypeDef typeDef) {
        List<ManufacturingJob> completed = new ArrayList<>();

        double productionOutput = getProductionOutputPerSecond(typeDef) * dtGameSeconds;

        for (ManufacturingJob job : new ArrayList<>(activeJobs.values())) {
            if (job.status != ManufacturingJob.JobStatus.ACTIVE) {
                continue;
            }

            boolean isComplete = job.progress(productionOutput);

            if (isComplete) {
                // 产出生成到outputBuffer
                produceOutput(job);
                completed.add(job);
            }
        }

        return completed;
    }

    /**
     * 生成产出喵。
     */
    private void produceOutput(ManufacturingJob job) {
        // 简化的产出逻辑，实际应该根据blueprintId查询蓝图喵
        String outputProduct = job.blueprintId + "_PRODUCT";
        double outputAmount = job.runs * 1.0; // 每个run产出1单位
        outputBuffer.merge(outputProduct, outputAmount, Double::sum);
    }

    /**
     * 取走产出喵。
     */
    public double takeOutput(String productId, double maxAmount) {
        double available = outputBuffer.getOrDefault(productId, 0.0);
        double toTake = Math.min(available, maxAmount);
        outputBuffer.merge(productId, -toTake, Double::sum);
        if (outputBuffer.get(productId) <= 0) {
            outputBuffer.remove(productId);
        }
        return toTake;
    }

    /**
     * 取消订单喵。
     */
    public boolean cancelJob(long jobId) {
        ManufacturingJob job = activeJobs.get(jobId);
        if (job == null) return false;

        // 如果已经开始生产，不退还原料
        // 如果还在PENDING，退还原料
        if (job.status == ManufacturingJob.JobStatus.PENDING && job.inputMaterials != null) {
            for (Map.Entry<String, Double> req : job.inputMaterials.entrySet()) {
                inputBuffer.merge(req.getKey(), req.getValue(), Double::sum);
            }
        }

        activeJobs.remove(jobId);
        return true;
    }

    private FacilityTypeDef getFacilityTypeDef() {
        // 实际应该从IndustryAssetRepository查询喵
        return null;
    }
}
```

---

#### 任务5.2：创建国家工业管理器

**文件路径**：`game/src/main/java/staraxis/game/industry/NationalIndustryManager.java`（新建）

```java
package staraxis.game.industry;

import staraxis.game.state.WorldState;

import java.util.*;

/**
 * NationalIndustryManager（国家工业管理器）喵。
 *
 * 管理一个国家所有的制造设施和制造订单喵。
 * 存储在 NationState 中喵。
 */
public class NationalIndustryManager {

    public final String nationId;

    // 所有制造设施（key: facility entityId）
    public final Map<Long, ManufacturingFacility> facilities = new HashMap<>();

    // 完成等待取货的订单
    public final List<ManufacturingJob> completedJobs = new ArrayList<>();

    public NationalIndustryManager() {
        this.nationId = "";
    }

    public NationalIndustryManager(String nationId) {
        this.nationId = nationId;
    }

    /**
     * 注册设施喵。
     */
    public void registerFacility(ManufacturingFacility facility) {
        facilities.put(facility.entityId, facility);
    }

    /**
     * 获取所有活跃订单喵。
     */
    public List<ManufacturingJob> getAllActiveJobs() {
        List<ManufacturingJob> allJobs = new ArrayList<>();
        for (ManufacturingFacility facility : facilities.values()) {
            allJobs.addAll(facility.activeJobs.values());
        }
        return allJobs;
    }

    /**
     * 获取所有完成订单喵。
     */
    public List<ManufacturingJob> getCompletedJobs() {
        return Collections.unmodifiableList(completedJobs);
    }

    /**
     * 每tick推进所有设施生产喵。
     */
    public void tickProduction(WorldState worldState, double dtGameSeconds,
                                IndustryAssetRepository industryRepo) {
        for (ManufacturingFacility facility : facilities.values()) {
            FacilityTypeDef typeDef = industryRepo.getFacilityType(facility.facilityTypeId);
            List<ManufacturingJob> completed = facility.tickProduction(dtGameSeconds, typeDef);

            for (ManufacturingJob job : completed) {
                completedJobs.add(job);
                // TODO: 通知AI顾问喵
            }
        }
    }

    /**
     * 取走设施产出喵。
     */
    public Map<String, Double> collectOutput(long facilityId, Map<String, Double> requests) {
        ManufacturingFacility facility = facilities.get(facilityId);
        if (facility == null) return Map.of();

        Map<String, Double> collected = new HashMap<>();
        for (Map.Entry<String, Double> req : requests.entrySet()) {
            double amount = facility.takeOutput(req.getKey(), req.getValue());
            if (amount > 0) {
                collected.put(req.getKey(), amount);
            }
        }
        return collected;
    }
}
```

---

#### 任务5.3：修改 NationState 添加工业管理器

**文件路径**：`game/src/main/java/staraxis/game/nation/NationState.java`（修改）

在 `researchQueue` 字段后添加：

```java
    /**
     * 国家工业管理器（制造设施系统）喵。
     */
    public final staraxis.game.industry.NationalIndustryManager industryManager;
```

修改构造函数：

```java
    public NationState(String nationId) {
        this.nationId = nationId;
        this.researchQueue = new staraxis.game.tech.NationalResearchQueue(nationId);
        this.industryManager = new staraxis.game.industry.NationalIndustryManager(nationId);
    }
```

---

### Day 6：制造命令、API和前端（4-5小时）

#### 任务6.1：创建建造设施命令

**文件路径**：`game/src/main/java/staraxis/game/command/BuildFacilityCommand.java`（新建）

```java
package staraxis.game.command;

/**
 * BuildFacilityCommand（建造设施命令）喵。
 */
public class BuildFacilityCommand extends Command {

    private final String nationId;
    private final String facilityTypeId;
    private final long hostEntityId;  // 建造在哪个行星/空间站上
    private final String facilityName;

    public BuildFacilityCommand(String nationId, String facilityTypeId,
                                 long hostEntityId, String facilityName) {
        super("buildFacility");
        this.nationId = nationId;
        this.facilityTypeId = facilityTypeId;
        this.hostEntityId = hostEntityId;
        this.facilityName = facilityName;
    }

    public String getNationId() { return nationId; }
    public String getFacilityTypeId() { return facilityTypeId; }
    public long getHostEntityId() { return hostEntityId; }
    public String getFacilityName() { return facilityName; }
}
```

---

#### 任务6.2：创建开始制造命令

**文件路径**：`game/src/main/java/staraxis/game/command/StartManufacturingCommand.java`（新建）

```java
package staraxis.game.command;

import java.util.Map;

/**
 * StartManufacturingCommand（开始制造命令）喵。
 */
public class StartManufacturingCommand extends Command {

    private final String nationId;
    private final long facilityId;
    private final String jobType;
    private final String blueprintId;
    private final int runs;
    private final Map<String, Double> inputMaterials;

    public StartManufacturingCommand(String nationId, long facilityId,
                                      String jobType, String blueprintId,
                                      int runs, Map<String, Double> inputMaterials) {
        super("startManufacturing");
        this.nationId = nationId;
        this.facilityId = facilityId;
        this.jobType = jobType;
        this.blueprintId = blueprintId;
        this.runs = runs;
        this.inputMaterials = inputMaterials;
    }

    // getters...
    public String getNationId() { return nationId; }
    public long getFacilityId() { return facilityId; }
    public String getJobType() { return jobType; }
    public String getBlueprintId() { return blueprintId; }
    public int getRuns() { return runs; }
    public Map<String, Double> getInputMaterials() { return inputMaterials; }
}
```

---

#### 任务6.3：创建 IndustryApi

**文件路径**：`webnet/src/main/java/staraxis/webnet/api/IndustryApi.java`（新建）

```java
package staraxis.webnet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.BuildFacilityCommand;
import staraxis.game.command.StartManufacturingCommand;
import staraxis.game.industry.*;
import staraxis.webnet.game.GameSessions;

import java.util.*;

public final class IndustryApi {

    private IndustryApi() {}

    /**
     * GET /api/industry/facilities
     */
    public static Map<String, Object> handleGetFacilities(ObjectMapper objectMapper,
                                                          String worldId, String nationId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        var nation = runtime.getWorldStateForSimOnly().nationManager.getNationState(nationId);
        if (nation == null) {
            return Map.of("ok", false, "error", "nation_not_found");
        }

        List<Map<String, Object>> facilityList = new ArrayList<>();
        for (ManufacturingFacility facility : nation.industryManager.facilities.values()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("entityId", facility.entityId);
            f.put("facilityTypeId", facility.facilityTypeId);
            f.put("facilityName", facility.facilityName);
            f.put("isActive", facility.isActive);
            f.put("activeJobCount", facility.activeJobs.size());
            facilityList.add(f);
        }

        return Map.of("ok", true, "facilities", facilityList);
    }

    /**
     * GET /api/industry/facility-types
     */
    public static Map<String, Object> handleGetFacilityTypes(ObjectMapper objectMapper,
                                                             String worldId, String nationId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        var nation = runtime.getWorldStateForSimOnly().nationManager.getNationState(nationId);
        if (nation == null) {
            return Map.of("ok", false, "error", "nation_not_found");
        }

        IndustryAssetRepository repo = runtime.getIndustryAssetRepository();
        Set<String> unlockedTechs = nation.researchQueue.getUnlockedTechs();
        List<FacilityTypeDef> available = repo.getAvailableFacilityTypes(unlockedTechs);

        List<Map<String, Object>> typeList = new ArrayList<>();
        for (FacilityTypeDef def : available) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("facilityTypeId", def.facilityTypeId);
            t.put("nameKey", def.nameKey);
            t.put("category", def.category);
            t.put("baseProductionPointsPerDay", def.baseProductionPointsPerDay);
            t.put("constructionCost", def.constructionCost);
            t.put("constructionTimeDays", def.constructionTimeDays);
            typeList.add(t);
        }

        return Map.of("ok", true, "facilityTypes", typeList);
    }

    /**
     * POST /api/industry/build-facility
     */
    public static Map<String, Object> handleBuildFacility(ObjectMapper objectMapper,
                                                          String worldId, Map<String, Object> req) {
        String nationId = String.valueOf(req.get("nationId"));
        String facilityTypeId = String.valueOf(req.get("facilityTypeId"));
        long hostEntityId = ((Number) req.get("hostEntityId")).longValue();
        String facilityName = req.get("facilityName") != null
                ? String.valueOf(req.get("facilityName"))
                : "New Facility";

        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        BuildFacilityCommand command = new BuildFacilityCommand(
                nationId, facilityTypeId, hostEntityId, facilityName);
        runtime.submitCommand(command);

        return Map.of("ok", true, "message", "facility_construction_started");
    }

    /**
     * POST /api/industry/start-manufacturing
     */
    public static Map<String, Object> handleStartManufacturing(ObjectMapper objectMapper,
                                                               String worldId, Map<String, Object> req) {
        String nationId = String.valueOf(req.get("nationId"));
        long facilityId = ((Number) req.get("facilityId")).longValue();
        String jobType = String.valueOf(req.get("jobType"));
        String blueprintId = String.valueOf(req.get("blueprintId"));
        int runs = req.get("runs") instanceof Number n ? n.intValue() : 1;

        @SuppressWarnings("unchecked")
        Map<String, Double> inputMaterials = req.get("inputMaterials") instanceof Map
                ? (Map<String, Double>) req.get("inputMaterials")
                : Map.of();

        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        StartManufacturingCommand command = new StartManufacturingCommand(
                nationId, facilityId, jobType, blueprintId, runs, inputMaterials);
        runtime.submitCommand(command);

        return Map.of("ok", true, "message", "manufacturing_started");
    }
}
```

---

## 阶段三：物流系统（第7-9天）

### 目标
实现实体物流舰队，货物真实物理运输，玩家可以追踪运输船位置，有被拦截风险。

---

### Day 7：物流舰队实体（4-5小时）

#### 任务7.1：创建 LogisticsFleet 实体

**文件路径**：`game/src/main/java/staraxis/game/logistics/LogisticsFleet.java`（新建）

```java
package staraxis.game.logistics;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

import java.util.*;

/**
 * LogisticsFleet（物流舰队）喵。
 *
 * 执行货物运输任务的实体舰队喵。
 */
public class LogisticsFleet extends Entity {

    // 舰队组成
    public List<Long> shipEntityIds = new ArrayList<>();

    // 当前任务
    public LogisticsMission mission;

    // 航线状态
    public RouteState routeState = RouteState.IDLE;

    // 载货清单
    public CargoHold cargoHold = new CargoHold();

    // 速度参数（GU/秒）
    public double baseSpeedGU = 100.0;
    public double currentSpeedGU = 100.0;

    // 航线路径点
    public List<Waypoint> waypoints = new ArrayList<>();
    public int currentWaypointIndex = 0;

    public LogisticsFleet() {
        this.entityType = EntityType.SHIP; // 或专用LOGISTICS_FLEET类型
    }

    /**
     * 设置新的运输任务喵。
     */
    public void setMission(LogisticsMission mission, List<Waypoint> path) {
        this.mission = mission;
        this.waypoints = path;
        this.currentWaypointIndex = 0;
        this.routeState = RouteState.LOADING;
    }

    /**
     * 每tick更新位置和状态喵。
     */
    public void tickMovement(double dtGameSeconds, double currentGameTime) {
        if (mission == null || waypoints.isEmpty()) {
            return;
        }

        switch (routeState) {
            case LOADING:
                handleLoading(dtGameSeconds, currentGameTime);
                break;
            case IN_TRANSIT:
                handleTransit(dtGameSeconds);
                break;
            case UNLOADING:
                handleUnloading(dtGameSeconds, currentGameTime);
                break;
            case RETURNING:
                handleReturn(dtGameSeconds);
                break;
            default:
                break;
        }
    }

    private void handleLoading(double dt, double currentTime) {
        // 简化为瞬时装载，实际应该有装载时间喵
        if (mission.departureTime <= currentTime) {
            routeState = RouteState.IN_TRANSIT;
            mission.actualDepartureTime = currentTime;
        }
    }

    private void handleTransit(double dt) {
        if (currentWaypointIndex >= waypoints.size()) {
            routeState = RouteState.UNLOADING;
            return;
        }

        Waypoint target = waypoints.get(currentWaypointIndex);
        Vec2d toTarget = new Vec2d(
                target.position.x() - posWorldGU.x(),
                target.position.y() - posWorldGU.y()
        );
        double distance = toTarget.length();

        if (distance < 10.0) { // 到达路径点
            currentWaypointIndex++;
            if (currentWaypointIndex >= waypoints.size()) {
                routeState = RouteState.UNLOADING;
            }
            return;
        }

        // 移动
        Vec2d direction = toTarget.normalize();
        double moveDistance = Math.min(currentSpeedGU * dt, distance);
        posWorldGU = new Vec2d(
                posWorldGU.x() + direction.x() * moveDistance,
                posWorldGU.y() + direction.y() * moveDistance
        );

        // 更新速度向量用于前端显示
        velWorldGU = new Vec2d(direction.x() * currentSpeedGU, direction.y() * currentSpeedGU);
    }

    private void handleUnloading(double dt, double currentTime) {
        // 简化为瞬时卸货喵
        mission.actualArrivalTime = currentTime;
        routeState = RouteState.COMPLETED;
    }

    private void handleReturn(double dt) {
        // 空载返回逻辑喵
        if (currentWaypointIndex <= 0) {
            routeState = RouteState.IDLE;
            mission = null;
            return;
        }

        currentWaypointIndex--;
        Waypoint target = waypoints.get(currentWaypointIndex);
        // 类似handleTransit的移动逻辑喵...
    }

    /**
     * 获取ETA（预估到达时间）喵。
     */
    public double getEstimatedArrival(double currentTime) {
        if (routeState != RouteState.IN_TRANSIT) {
            return currentTime;
        }

        double remainingDistance = 0;
        Vec2d currentPos = posWorldGU;

        for (int i = currentWaypointIndex; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            Vec2d toWp = new Vec2d(
                    wp.position.x() - currentPos.x(),
                    wp.position.y() - currentPos.y()
            );
            remainingDistance += toWp.length();
            currentPos = wp.position;
        }

        return currentTime + (remainingDistance / currentSpeedGU);
    }

    public enum RouteState {
        IDLE,       // 空闲
        LOADING,    // 装载中
        IN_TRANSIT, // 运输中
        UNLOADING,  // 卸载中
        RETURNING,  // 返回中
        COMPLETED   // 已完成
    }

    public static class Waypoint {
        public Vec2d position;
        public SectorCoord sectorCoord;
        public long entityId;  // 关联的空间站/行星ID

        public Waypoint(Vec2d position, SectorCoord sectorCoord, long entityId) {
            this.position = position;
            this.sectorCoord = sectorCoord;
            this.entityId = entityId;
        }
    }
}
```

---

#### 任务7.2：创建 LogisticsMission

**文件路径**：`game/src/main/java/staraxis/game/logistics/LogisticsMission.java`（新建）

```java
package staraxis.game.logistics;

import java.util.Map;
import java.util.UUID;

/**
 * LogisticsMission（物流任务）喵。
 */
public class LogisticsMission {

    public final String missionId;
    public final String nationId;

    // 起点和终点
    public final long originEntityId;
    public final long destinationEntityId;

    // 货物
    public final Map<String, Double> cargo;
    public final double cargoValue;

    // 时间安排
    public final double scheduledDepartureTime;
    public double actualDepartureTime;
    public double estimatedArrivalTime;
    public double actualArrivalTime;

    // 状态
    public MissionStatus status;

    // 运输方式
    public TransportMethod method;

    public LogisticsMission(String nationId, long originEntityId, long destinationEntityId,
                            Map<String, Double> cargo, double cargoValue,
                            double scheduledDepartureTime, double estimatedArrivalTime) {
        this.missionId = UUID.randomUUID().toString();
        this.nationId = nationId;
        this.originEntityId = originEntityId;
        this.destinationEntityId = destinationEntityId;
        this.cargo = cargo;
        this.cargoValue = cargoValue;
        this.scheduledDepartureTime = scheduledDepartureTime;
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.status = MissionStatus.SCHEDULED;
        this.method = TransportMethod.PLAYER_FLEET;
    }

    public enum MissionStatus {
        SCHEDULED,   // 已计划
        LOADING,     // 装载中
        IN_TRANSIT,  // 运输中
        DELIVERED,   // 已送达
        DELAYED,     // 延误
        INTERRUPTED, // 被拦截/中断
        CANCELLED    // 已取消
    }

    public enum TransportMethod {
        PLAYER_FLEET,  // 玩家自有舰队
        NPC_COURIER,   // NPC快递（慢但安全）
        CONTRACTED     // 外包给其他玩家
    }
}
```

---

#### 任务7.3：创建 CargoHold

**文件路径**：`game/src/main/java/staraxis/game/logistics/CargoHold.java`（新建）

```java
package staraxis.game.logistics;

import java.util.HashMap;
import java.util.Map;

/**
 * CargoHold（货舱）喵。
 */
public class CargoHold {

    public double maxCapacity = 1000.0;  // 最大载货量
    public double currentLoad = 0.0;

    public Map<String, Double> contents = new HashMap<>();

    /**
     * 添加货物喵。
     */
    public boolean addCargo(String resourceId, double amount) {
        if (currentLoad + amount > maxCapacity) {
            return false;
        }
        contents.merge(resourceId, amount, Double::sum);
        currentLoad += amount;
        return true;
    }

    /**
     * 移除货物喵。
     */
    public double removeCargo(String resourceId, double maxAmount) {
        double available = contents.getOrDefault(resourceId, 0.0);
        double toRemove = Math.min(available, maxAmount);
        contents.merge(resourceId, -toRemove, Double::sum);
        if (contents.get(resourceId) <= 0) {
            contents.remove(resourceId);
        }
        currentLoad -= toRemove;
        return toRemove;
    }

    /**
     * 获取指定货物数量喵。
     */
    public double getCargoAmount(String resourceId) {
        return contents.getOrDefault(resourceId, 0.0);
    }

    /**
     * 获取剩余容量喵。
     */
    public double getRemainingCapacity() {
        return maxCapacity - currentLoad;
    }

    /**
     * 清空货舱喵。
     */
    public Map<String, Double> unloadAll() {
        Map<String, Double> allCargo = new HashMap<>(contents);
        contents.clear();
        currentLoad = 0;
        return allCargo;
    }
}
```

---

### Day 8：物流管理器（4-5小时）

#### 任务8.1：创建 NationalLogisticsManager

**文件路径**：`game/src/main/java/staraxis/game/logistics/NationalLogisticsManager.java`（新建）

```java
package staraxis.game.logistics;

import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;

import java.util.*;

/**
 * NationalLogisticsManager（国家物流管理器）喵。
 *
 * 管理一个国家所有的物流舰队和运输任务喵。
 */
public class NationalLogisticsManager {

    public final String nationId;

    // 所有物流舰队
    public final Map<Long, LogisticsFleet> fleets = new HashMap<>();

    // 活跃任务
    public final Map<String, LogisticsMission> activeMissions = new HashMap<>();

    // 任务历史
    public final List<LogisticsMission> missionHistory = new ArrayList<>();

    public NationalLogisticsManager() {
        this.nationId = "";
    }

    public NationalLogisticsManager(String nationId) {
        this.nationId = nationId;
    }

    /**
     * 注册物流舰队喵。
     */
    public void registerFleet(LogisticsFleet fleet) {
        fleets.put(fleet.entityId, fleet);
    }

    /**
     * 创建新运输任务喵。
     */
    public LogisticsMission createMission(long originId, long destinationId,
                                          Map<String, Double> cargo, double cargoValue,
                                          double currentTime, double distanceGU) {
        // 计算ETA：距离 / 速度
        double travelTime = distanceGU / 100.0;  // 假设基础速度100GU/秒喵
        double departureTime = currentTime + 60; // 1分钟后出发
        double eta = departureTime + travelTime;

        LogisticsMission mission = new LogisticsMission(
                nationId, originId, destinationId,
                cargo, cargoValue,
                departureTime, eta
        );

        activeMissions.put(mission.missionId, mission);
        return mission;
    }

    /**
     * 分配舰队执行任务喵。
     */
    public boolean assignFleetToMission(long fleetId, String missionId, WorldState worldState) {
        LogisticsFleet fleet = fleets.get(fleetId);
        LogisticsMission mission = activeMissions.get(missionId);

        if (fleet == null || mission == null) {
            return false;
        }

        // 计算航线
        List<LogisticsFleet.Waypoint> path = calculatePath(
                worldState, mission.originEntityId, mission.destinationEntityId);

        fleet.setMission(mission, path);
        mission.status = LogisticsMission.MissionStatus.LOADING;

        return true;
    }

    /**
     * 计算航线路径点喵。
     */
    private List<LogisticsFleet.Waypoint> calculatePath(WorldState worldState,
                                                         long originId, long destId) {
        List<LogisticsFleet.Waypoint> path = new ArrayList<>();

        var origin = worldState.entitiesById.get(originId);
        var dest = worldState.entitiesById.get(destId);

        if (origin != null && dest != null) {
            path.add(new LogisticsFleet.Waypoint(origin.posWorldGU, origin.sectorCoord, originId));
            path.add(new LogisticsFleet.Waypoint(dest.posWorldGU, dest.sectorCoord, destId));
        }

        return path;
    }

    /**
     * 每tick更新所有舰队喵。
     */
    public void tickLogistics(WorldState worldState, double dtGameSeconds, double currentGameTime) {
        for (LogisticsFleet fleet : fleets.values()) {
            fleet.tickMovement(dtGameSeconds, currentGameTime);

            // 检查任务完成
            if (fleet.mission != null && fleet.routeState == LogisticsFleet.RouteState.COMPLETED) {
                completeMission(fleet.mission);
            }
        }
    }

    private void completeMission(LogisticsMission mission) {
        mission.status = LogisticsMission.MissionStatus.DELIVERED;
        mission.actualArrivalTime = System.currentTimeMillis() / 1000.0;
        activeMissions.remove(mission.missionId);
        missionHistory.add(mission);

        // TODO: 通知AI顾问喵
    }

    /**
     * 获取所有活跃任务的追踪信息喵。
     */
    public List<Map<String, Object>> getActiveMissionTracking(double currentTime) {
        List<Map<String, Object>> tracking = new ArrayList<>();

        for (LogisticsMission mission : activeMissions.values()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("missionId", mission.missionId);
            info.put("status", mission.status.name());
            info.put("originId", mission.originEntityId);
            info.put("destinationId", mission.destinationEntityId);
            info.put("cargo", mission.cargo);
            info.put("eta", mission.estimatedArrivalTime);
            info.put("remainingSeconds", Math.max(0, mission.estimatedArrivalTime - currentTime));
            tracking.add(info);
        }

        return tracking;
    }
}
```

---

#### 任务8.2：修改 NationState 添加物流管理器

**文件路径**：`game/src/main/java/staraxis/game/nation/NationState.java`（修改）

在 `industryManager` 字段后添加：

```java
    /**
     * 国家物流管理器（物流系统）喵。
     */
    public final staraxis.game.logistics.NationalLogisticsManager logisticsManager;
```

修改构造函数：

```java
    public NationState(String nationId) {
        this.nationId = nationId;
        this.researchQueue = new staraxis.game.tech.NationalResearchQueue(nationId);
        this.industryManager = new staraxis.game.industry.NationalIndustryManager(nationId);
        this.logisticsManager = new staraxis.game.logistics.NationalLogisticsManager(nationId);
    }
```

---

### Day 9：物流命令、API和前端（4-5小时）

#### 任务9.1：创建创建运输任务命令

**文件路径**：`game/src/main/java/staraxis/game/command/CreateTransportMissionCommand.java`（新建）

```java
package staraxis.game.command;

import java.util.Map;

public class CreateTransportMissionCommand extends Command {

    private final String nationId;
    private final long originEntityId;
    private final long destinationEntityId;
    private final Map<String, Double> cargo;
    private final long fleetId;  // 0 = 自动分配

    public CreateTransportMissionCommand(String nationId, long originEntityId,
                                          long destinationEntityId, Map<String, Double> cargo,
                                          long fleetId) {
        super("createTransportMission");
        this.nationId = nationId;
        this.originEntityId = originEntityId;
        this.destinationEntityId = destinationEntityId;
        this.cargo = cargo;
        this.fleetId = fleetId;
    }

    public String getNationId() { return nationId; }
    public long getOriginEntityId() { return originEntityId; }
    public long getDestinationEntityId() { return destinationEntityId; }
    public Map<String, Double> getCargo() { return cargo; }
    public long getFleetId() { return fleetId; }
}
```

---

#### 任务9.2：创建 LogisticsApi

**文件路径**：`webnet/src/main/java/staraxis/webnet/api/LogisticsApi.java`（新建）

```java
package staraxis.webnet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.CreateTransportMissionCommand;
import staraxis.game.logistics.*;
import staraxis.webnet.game.GameSessions;

import java.util.*;

public final class LogisticsApi {

    private LogisticsApi() {}

    /**
     * GET /api/logistics/fleets
     */
    public static Map<String, Object> handleGetFleets(ObjectMapper objectMapper,
                                                      String worldId, String nationId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        var nation = runtime.getWorldStateForSimOnly().nationManager.getNationState(nationId);
        if (nation == null) {
            return Map.of("ok", false, "error", "nation_not_found");
        }

        List<Map<String, Object>> fleetList = new ArrayList<>();
        for (LogisticsFleet fleet : nation.logisticsManager.fleets.values()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("entityId", fleet.entityId);
            f.put("cargoLoad", fleet.cargoHold.currentLoad);
            f.put("cargoCapacity", fleet.cargoHold.maxCapacity);
            f.put("routeState", fleet.routeState.name());
            f.put("posX", fleet.posWorldGU.x());
            f.put("posY", fleet.posWorldGU.y());

            if (fleet.mission != null) {
                f.put("missionId", fleet.mission.missionId);
                f.put("eta", fleet.getEstimatedArrival(
                        runtime.getWorldStateForSimOnly().time.totalGameSecondsAcc));
            }

            fleetList.add(f);
        }

        return Map.of("ok", true, "fleets", fleetList);
    }

    /**
     * GET /api/logistics/missions
     */
    public static Map<String, Object> handleGetMissions(ObjectMapper objectMapper,
                                                        String worldId, String nationId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        var nation = runtime.getWorldStateForSimOnly().nationManager.getNationState(nationId);
        if (nation == null) {
            return Map.of("ok", false, "error", "nation_not_found");
        }

        double currentTime = runtime.getWorldStateForSimOnly().time.totalGameSecondsAcc;
        List<Map<String, Object>> tracking = nation.logisticsManager.getActiveMissionTracking(currentTime);

        return Map.of("ok", true, "missions", tracking);
    }

    /**
     * POST /api/logistics/create-mission
     */
    public static Map<String, Object> handleCreateMission(ObjectMapper objectMapper,
                                                          String worldId, Map<String, Object> req) {
        String nationId = String.valueOf(req.get("nationId"));
        long originId = ((Number) req.get("originEntityId")).longValue();
        long destId = ((Number) req.get("destinationEntityId")).longValue();
        long fleetId = req.get("fleetId") instanceof Number n ? n.longValue() : 0L;

        @SuppressWarnings("unchecked")
        Map<String, Double> cargo = req.get("cargo") instanceof Map
                ? (Map<String, Double>) req.get("cargo")
                : Map.of();

        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        CreateTransportMissionCommand command = new CreateTransportMissionCommand(
                nationId, originId, destId, cargo, fleetId);
        runtime.submitCommand(command);

        return Map.of("ok", true, "message", "transport_mission_created");
    }
}
```

---

#### 任务9.3：前端物流追踪组件

**文件路径**：`web/src/components/logistics/LogisticsTracker.vue`（新建）

```vue
<template>
  <div class="logistics-tracker">
    <h2>物流追踪</h2>

    <!-- 活跃任务 -->
    <section class="active-missions">
      <h3>运输中 ({{ missions.length }})</h3>
      <div v-for="mission in missions" :key="mission.missionId" class="mission-card">
        <div class="mission-route">
          <span class="origin">{{ getEntityName(mission.originId) }}</span>
          <span class="arrow">→</span>
          <span class="destination">{{ getEntityName(mission.destinationId) }}</span>
        </div>
        <div class="mission-status" :class="mission.status.toLowerCase()">
          {{ formatStatus(mission.status) }}
        </div>
        <div class="mission-cargo">
          <span v-for="(amount, resource) in mission.cargo" :key="resource">
            {{ resource }}: {{ amount }}
          </span>
        </div>
        <div class="mission-eta">
          ETA: {{ formatTime(mission.remainingSeconds) }}
        </div>
      </div>
    </section>

    <!-- 舰队列表 -->
    <section class="fleet-list">
      <h3>物流舰队 ({{ fleets.length }})</h3>
      <div v-for="fleet in fleets" :key="fleet.entityId" class="fleet-item">
        <div class="fleet-id">舰队 #{{ fleet.entityId }}</div>
        <div class="fleet-status">{{ formatRouteState(fleet.routeState) }}</div>
        <div class="fleet-cargo">{{ fleet.cargoLoad }} / {{ fleet.cargoCapacity }} 吨</div>
        <div class="fleet-position">({{ Math.round(fleet.posX) }}, {{ Math.round(fleet.posY) }})</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  worldId: string
  nationId: string
}>()

const missions = ref([])
const fleets = ref([])
let refreshInterval: number | null = null

async function fetchLogisticsData() {
  const [missionsRes, fleetsRes] = await Promise.all([
    fetch(`/api/logistics/missions?worldId=${props.worldId}&nationId=${props.nationId}`).then(r => r.json()),
    fetch(`/api/logistics/fleets?worldId=${props.worldId}&nationId=${props.nationId}`).then(r => r.json())
  ])

  if (missionsRes.ok) missions.value = missionsRes.missions
  if (fleetsRes.ok) fleets.value = fleetsRes.fleets
}

function formatStatus(status: string): string {
  const map: Record<string, string> = {
    'SCHEDULED': '已计划',
    'LOADING': '装载中',
    'IN_TRANSIT': '运输中',
    'DELIVERED': '已送达',
    'DELAYED': '延误',
    'INTERRUPTED': '被拦截'
  }
  return map[status] || status
}

function formatRouteState(state: string): string {
  const map: Record<string, string> = {
    'IDLE': '空闲',
    'LOADING': '装载中',
    'IN_TRANSIT': '航行中',
    'UNLOADING': '卸载中',
    'COMPLETED': '已完成'
  }
  return map[state] || state
}

function formatTime(seconds: number): string {
  if (seconds < 60) return Math.round(seconds) + '秒'
  if (seconds < 3600) return Math.round(seconds / 60) + '分钟'
  return Math.round(seconds / 3600) + '小时'
}

function getEntityName(entityId: number): string {
  return '位置-' + entityId
}

onMounted(() => {
  fetchLogisticsData()
  refreshInterval = window.setInterval(fetchLogisticsData, 3000) // 3秒刷新喵
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<style scoped>
.logistics-tracker {
  padding: 20px;
}

.mission-card, .fleet-item {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
  background: var(--panel-bg);
}

.mission-route {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: bold;
}

.mission-status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  margin: 8px 0;
}

.mission-status.in_transit {
  background: #4A90E2;
  color: white;
}

.mission-status.delivered {
  background: #7ED321;
  color: white;
}

.fleet-item {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 12px;
  align-items: center;
}
</style>
```

---

## 执行检查清单

### 阶段一：科技研究系统

#### Day 1 检查项
- [ ] `mkdir -p assets/tech` 执行完成
- [ ] `assets/tech/technologies.json` 创建并填充6个基础科技
- [ ] `TechDef.java` 扩展字段（category, tier, baseResearchPoints, prerequisites, unlocks, effects）
- [ ] `TechAssetRepository.java` 创建完成
- [ ] 运行测试：`TechAssetRepository.loadAll()` 无报错，能读取6个科技

#### Day 2 检查项
- [ ] `ResearchProject.java` 创建完成
- [ ] `ResearchLab.java` 创建完成
- [ ] `NationalResearchQueue.java` 创建完成（包含startResearchProject, tickResearch方法）
- [ ] `NationState.java` 添加 `researchQueue` 字段和初始化

#### Day 3 检查项
- [ ] `StartResearchCommand.java` 创建完成
- [ ] `StartResearchHandler.java` 创建完成
- [ ] `ResearchApi.java` 创建完成（4个API端点）
- [ ] `StarAxisGameRuntime` 集成：
  - [ ] 添加 `TechAssetRepository` 字段
  - [ ] `newGame()` 中加载科技配置
  - [ ] `update()` 中调用 `researchQueue.tickResearch()`
  - [ ] 注册 `StartResearchHandler`
- [ ] 给新国家添加默认实验室（出生流程中）
- [ ] 前端 `TechTreePanel.vue` 基础版本运行
- [ ] 测试：可以通过API开始研究，进度条走动

---

### 阶段二：制造设施系统

#### Day 4 检查项
- [ ] `mkdir -p assets/industry` 执行完成
- [ ] `assets/industry/facility-types.json` 创建完成（3种设施类型）
- [ ] `ManufacturingJob.java` 创建完成（含JobStatus枚举）
- [ ] `FacilityTypeDef.java` 创建完成
- [ ] `IndustryAssetRepository.java` 创建完成
- [ ] 运行测试：`IndustryAssetRepository.loadAll()` 无报错

#### Day 5 检查项
- [ ] `ManufacturingFacility.java` 创建完成（含tickProduction, createJob方法）
- [ ] `NationalIndustryManager.java` 创建完成
- [ ] `NationState.java` 添加 `industryManager` 字段和初始化
- [ ] 测试：可以创建制造设施实体并注册到管理器

#### Day 6 检查项
- [ ] `BuildFacilityCommand.java` 创建完成
- [ ] `StartManufacturingCommand.java` 创建完成
- [ ] `BuildFacilityHandler.java` 创建完成（处理建造命令）
- [ ] `StartManufacturingHandler.java` 创建完成
- [ ] `IndustryApi.java` 创建完成（4个API端点）
- [ ] `StarAxisGameRuntime` 集成：
  - [ ] 添加 `IndustryAssetRepository` 字段
  - [ ] `update()` 中调用 `industryManager.tickProduction()`
  - [ ] 注册Handler
- [ ] 前端基础界面（设施列表、创建订单）
- [ ] 测试：可以创建设施并开始制造订单，进度推进

---

### 阶段三：物流系统

#### Day 7 检查项
- [ ] `LogisticsFleet.java` 创建完成（含RouteState, Waypoint）
- [ ] `LogisticsMission.java` 创建完成（含MissionStatus, TransportMethod）
- [ ] `CargoHold.java` 创建完成
- [ ] 测试：可以创建物流舰队实体并设置任务

#### Day 8 检查项
- [ ] `NationalLogisticsManager.java` 创建完成（含createMission, assignFleetToMission）
- [ ] `NationState.java` 添加 `logisticsManager` 字段和初始化
- [ ] `StarAxisGameRuntime.update()` 中调用 `logisticsManager.tickLogistics()`
- [ ] 测试：物流舰队可以在地图上移动，位置更新

#### Day 9 检查项
- [ ] `CreateTransportMissionCommand.java` 创建完成
- [ ] `CreateTransportMissionHandler.java` 创建完成
- [ ] `LogisticsApi.java` 创建完成（3个API端点）
- [ ] 注册Handler到CommandBus
- [ ] 前端 `LogisticsTracker.vue` 完成
- [ ] 测试：可以创建运输任务，在地图上追踪舰队位置，货物送达

---

## 9天完整开发路线图

```
Day 1-3: 科技研究系统
  └─ 玩家可以研究科技，真实时间推进，解锁新设施

Day 4-6: 制造设施系统
  └─ 科技解锁设施，设施执行制造订单，真实时间生产

Day 7-9: 物流系统
  └─ 设施产出需要运输，实体舰队运输，可追踪位置

完成后你将拥有：
✅ 完整的科技树系统（研究→解锁）
✅ 制造设施系统（建造→生产）
✅ 物流运输系统（装载→运输→交付）
✅ 三个系统互相联动的基础经济循环
```

## 快速开始命令

```bash
# 立即执行的第一步
mkdir -p assets/tech
mkdir -p assets/industry
mkdir -p game/src/main/java/staraxis/game/tech
mkdir -p game/src/main/java/staraxis/game/industry
mkdir -p game/src/main/java/staraxis/game/logistics
mkdir -p webnet/src/main/java/staraxis/webnet/api

# 然后打开 PLAN.md 从 Day 1 任务1.1 开始复制代码
```

---

**提示**：
1. 每个文件都有**完整代码**，直接复制粘贴即可喵
2. 每天完成检查清单后再进入下一天
3. 遇到报错先检查检查清单的依赖是否都完成
4. 需要解释任何代码或设计决策随时问我喵！

### Day 1 检查项
- [ ] `assets/tech/technologies.json` 创建完成
- [ ] `TechDef.java` 扩展字段完成
- [ ] `TechAssetRepository.java` 创建完成
- [ ] 运行测试：`TechAssetRepository.loadAll()` 无报错

### Day 2 检查项
- [ ] `ResearchProject.java` 创建完成
- [ ] `ResearchLab.java` 创建完成
- [ ] `NationalResearchQueue.java` 创建完成
- [ ] `NationState.java` 添加 `researchQueue` 字段

### Day 3 检查项
- [ ] `StartResearchCommand.java` 创建完成
- [ ] `StartResearchHandler.java` 创建完成
- [ ] `ResearchApi.java` 创建完成
- [ ] `StarAxisGameRuntime` 集成科技系统
- [ ] 前端 `TechTreePanel.vue` 基础版本运行

---

**下一步**：确认阶段一计划后，我将为你详细编写阶段二（制造设施）和阶段三（物流系统）的执行计划喵。
