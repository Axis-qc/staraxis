# Data Model – 012 真实比例宇宙生成

## Entity Relationship Diagram (textual)

```text
Galaxy 1 ── * Sector (hex)
Sector 1 ── * StarSystem
StarSystem 1 ── * Planet
Planet 1 ── * Moon
```

## Entities

### Galaxy
| Field | Type | Description |
|-------|------|-------------|
| id | long | 唯一 ID (hash of seed) |
| name | String | 名称 (可脚本生成) |
| seed | long | 随机种子 |
| sectorCount | int | 星区数量 |
| hexRadiusLy | float | 六边形半径 (ly) |

### Sector
| Field | Type | Description |
|-------|------|-------------|
| sectorId | long | 64 位整数，坐标轴六边形映射 |
| centerX | double | 银河坐标 X (ly) |
| centerY | double | 银河坐标 Y (ly) |
| starSystemCount | int | 本区恒星系数量 |
| deepSpaceRatio | float | 深空（空区）比例 |

### StarSystem
| Field | Type | Description |
|-------|------|-------------|
| systemId | long | 唯一 ID (seed 派生) |
| sectorId | long | 所属 Sector |
| localXkm | double | Sector 局部坐标 X (km) |
| localYkm | double | Y |
| localZkm | double | Z |
| starType | enum | 恒星光谱分类 |
| planets | List<Planet> | 行星列表 |

### Planet
| Field | Type | Description |
|-------|------|-------------|
| planetId | long | 唯一 |
| systemId | long | 外键 |
| orbitRadiusKm | double | 轨道半径 |
| orbitalPeriodDay | float | 公转周期 |
| radiusKm | double | 行星半径 |
| eccentricity | float | 轨道偏心率 |
| moons | List<Moon> | 卫星 |

### Moon
| Field | Type | Description |
|-------|------|-------------|
| moonId | long | 唯一 |
| planetId | long | 外键 |
| orbitRadiusKm | double | |
| orbitalPeriodDay | float | |
| radiusKm | double | |

## Validation Rules
- 星体重叠：`distance(centerA, centerB) >= 0.999 * (radiusA + radiusB)`
- 开普勒三定律误差 <2%
- Sector hex 边长 = `hexRadiusLy`

