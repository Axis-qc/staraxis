package staraxis.game.command;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipBody;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

import java.util.List;
import java.util.Map;

/**
 * LoadWorldHandler（加载世界存档命令处理器）喵。
 *
 * 从 LoadWorldCommand 携带的存档数据恢复 WorldState 的时间轴、国家、实体和 ID 生成器状态。
 * 此处理器的逻辑原为 WorldSavesApi 中的 applyTimeState / applyNationState / applyEntitiesState / setNextEntityId 喵。
 */
public class LoadWorldHandler implements CommandHandler<LoadWorldCommand> {

    @Override
    public void handle(LoadWorldCommand command, WorldState worldState, double dtGameHours) {
        if (command == null || worldState == null) {
            return;
        }

        // 1. 恢复时间轴状态
        applyTimeState(worldState, command.getWorldData());

        // 2. 恢复国家状态
        applyNationState(worldState, command.getNations());

        // 3. 恢复动态实体状态
        applyEntitiesState(worldState, command.getEntities());

        // 4. 恢复实体 ID 生成器状态
        worldState.setNextEntityId(command.getNextEntityId());

        // 5. 恢复工业注册表状态（本地库存 / 加工设施 / 采集设施 / 运输记录 / ID 生成器）喵
        applyIndustryState(worldState, command.getWorldData());
    }

    /**
     * 恢复工业注册表状态喵。
     *
     * 存档中 industry 段挂载在 world 下（见 WorldSaveService / IndustryStateCodec），
     * 随 LoadWorldCommand.worldData 一起传入；旧存档无 industry 字段时保持空注册表（单向兼容）。
     */
    private static void applyIndustryState(WorldState ws, Map<String, Object> worldMap) {
        if (worldMap == null) {
            return;
        }
        Object industryObj = worldMap.get("industry");
        if (industryObj instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> industryMap = (Map<String, Object>) raw;
            staraxis.game.save.IndustryStateCodec.apply(industryMap, ws.industryRegistry);
        }
    }

    /**
     * 恢复时间轴状态喵。
     */
    private static void applyTimeState(WorldState ws, Map<String, Object> worldMap) {
        if (worldMap == null) {
            return;
        }
        Object tick = worldMap.get("simulationTick");
        Object totalSec = worldMap.get("totalGameSeconds");
        Object timeScale = worldMap.get("timeScale");
        Object gsprs = worldMap.get("gameSecondsPerRealSecond");

        if (tick instanceof Number n) {
            ws.time.simulationTick = n.longValue();
        }
        if (totalSec instanceof Number n) {
            ws.time.totalGameSecondsAcc = n.doubleValue();
            ws.time.gameDatetimeDay = (int) (Math.floor(ws.time.totalGameSecondsAcc / 86400.0) + 1);
            double daySec = ws.time.totalGameSecondsAcc % 86400.0;
            ws.time.accGameHoursInDay = daySec / 3600.0;
        }
        if (timeScale instanceof Number n) {
            ws.time.timeScale = n.doubleValue();
        }
        if (gsprs instanceof Number n) {
            ws.time.gameSecondsPerRealSecond = n.doubleValue();
        }
    }

    /**
     * 恢复国家状态（最小闭环字段）喵。
     */
    private static void applyNationState(WorldState ws, List<Map<String, Object>> nationsList) {
        if (nationsList == null) {
            return;
        }
        for (Map<String, Object> n : nationsList) {
            if (n == null) {
                continue;
            }
            Object nationIdObj = n.get("nationId");
            if (nationIdObj == null) {
                continue;
            }
            String nationId = String.valueOf(nationIdObj);
            if (!ws.nationManager.hasNation(nationId)) {
                ws.nationManager.registerNation(nationId);
            }
            var ns = ws.nationManager.getNationState(nationId);
            if (ns == null) {
                continue;
            }

            Object name = n.get("name");
            Object gov = n.get("governmentId");
            Object spawn = n.get("spawnSystemEntityId");
            Object capital = n.get("capitalPlanetEntityId");

            ns.name = name == null ? ns.name : String.valueOf(name);
            ns.governmentId = gov == null ? ns.governmentId : String.valueOf(gov);
            if (spawn instanceof Number sn) {
                ns.spawnSystemEntityId = sn.longValue();
            }
            if (capital instanceof Number cn) {
                ns.capitalPlanetEntityId = cn.longValue();
            }

            Object playerIdsObj = n.get("playerIds");
            if (playerIdsObj instanceof List<?> pidList) {
                for (Object pid : pidList) {
                    if (pid != null) {
                        // TODO AssetManager 统一处理：存档加载暂不自动绑定玩家-国家归属喵
                        // ws.nationManager.assignPlayerToNation(String.valueOf(pid), nationId);
                    }
                }
            }
        }
    }

    /**
     * 恢复动态实体状态（SHIP、STATION 等）喵。
     */
    private static void applyEntitiesState(WorldState ws, List<Map<String, Object>> entitiesList) {
        if (entitiesList == null) {
            return;
        }
        for (Map<String, Object> e : entitiesList) {
            if (e == null) {
                continue;
            }
            Object entityIdObj = e.get("entityId");
            if (entityIdObj == null) {
                continue;
            }
            long entityId = ((Number) entityIdObj).longValue();

            // 检查实体是否已存在（例如天文实体），避免重复注册喵
            if (ws.entitiesById.containsKey(entityId)) {
                continue;
            }

            Object entityTypeObj = e.get("entityType");
            EntityType entityType = null;
            if (entityTypeObj instanceof String typeStr) {
                try {
                    entityType = EntityType.valueOf(typeStr);
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (entityType == null) {
                continue;
            }

            // 仅恢复动态实体（SHIP、STATION）喵
            if (entityType != EntityType.SHIP && entityType != EntityType.STATION) {
                continue;
            }

            // 创建实体实例喵
            Entity entity;
            if (entityType == EntityType.SHIP) {
                ShipBody ship = new ShipBody();
                ship.entityId = entityId;
                ship.entityType = entityType;
                ship.designId = e.get("designId") == null ? null : String.valueOf(e.get("designId"));
                ship.hpHull = e.get("hpHull") instanceof Number n ? n.doubleValue() : 1.0;
                ship.power = e.get("power") instanceof Number n ? n.doubleValue() : 100.0;
                ship.fuelMass = e.get("fuelMass") instanceof Number n ? n.doubleValue()
                    : e.get("fuel") instanceof Number n ? n.doubleValue() : 0.0;
                Object flagsObj = e.get("customFlags");
                if (flagsObj instanceof List<?> flagList) {
                    for (Object flag : flagList) {
                        if (flag != null) {
                            ship.customFlags.add(String.valueOf(flag));
                        }
                    }
                }
                entity = ship;
            } else {
                // 未来支持 STATION 类型喵
                continue;
            }

            // 设置通用字段喵
            entity.systemId = e.get("systemId") instanceof Number n ? n.longValue() : 0L;
            entity.parentEntityId = e.get("parentEntityId") instanceof Number n ? n.longValue() : 0L;
            Object posX = e.get("posX");
            Object posY = e.get("posY");
            Object posZ = e.get("posZ");
            if (posX instanceof Number x && posY instanceof Number y) {
                double z = posZ instanceof Number zn ? zn.doubleValue() : 0.0;
                entity.posWorldGU = new SpacePosition(x.doubleValue(), y.doubleValue(), z);
            }
            Object velX = e.get("velX");
            Object velY = e.get("velY");
            Object velZ = e.get("velZ");
            if (velX instanceof Number vx && velY instanceof Number vy) {
                double vz = velZ instanceof Number vzn ? vzn.doubleValue() : 0.0;
                entity.velWorldGU = new SpacePosition(vx.doubleValue(), vy.doubleValue(), vz);
            }
            entity.ownerNationId = e.get("ownerNationId") == null ? null : String.valueOf(e.get("ownerNationId"));

            // 注册到世界状态喵
            ws.registerEntity(entity);

            // TODO AssetManager 统一处理：存档加载暂不自动分配资产归属喵
            // if (entity.ownerNationId != null && !entity.ownerNationId.isBlank()) {
            //     ws.nationAssetManager.assignEntityToNation(entityId, entity.ownerNationId);
            // }
        }
    }
}
