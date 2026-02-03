package staraxis.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.astro.AstroData;
import staraxis.game.astro.AstroGenerator;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.sim.SimulationClock;
import staraxis.game.sim.SimulationTime;
import staraxis.game.state.DailySettlementStateBuffer;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.RealTimeWorldStateBuffer;
import staraxis.game.state.WorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.OrbitSnapshot;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldGenerator;
import staraxis.game.world.WorldSector;
import staraxis.game.command.CommandBus;
import staraxis.game.command.SetTimeScaleCommand;
import staraxis.game.command.SetTimeScaleHandler;

import java.util.List;

/**
 * StarAxisGameRuntime
 *
 * 最小可用权威运行时：
 * - simulationTick 固定脉冲
 * - 游戏时间推进 dtGameHours
 * - tick 结束发布 RealTimeWorldState（双缓冲）
 * - 跨日发布 DailySettlementState
 */
public class StarAxisGameRuntime implements GameRuntime {

    private final WorldState worldState;

    private final RealTimeWorldStateBuffer realTimeBuffer = new RealTimeWorldStateBuffer();

    private final DailySettlementStateBuffer dailySettlementBuffer = new DailySettlementStateBuffer();

    private final CommandBus commandBus = new CommandBus();

    public StarAxisGameRuntime(WorldState worldState) {
        this.worldState = worldState;

        commandBus.register(SetTimeScaleCommand.class, new SetTimeScaleHandler());
    }

    public CommandBus getCommandBusForSimOnly() {
        return commandBus;
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg) {
        SimulationTime time = new SimulationTime();

        var worldMap = WorldGenerator.generate(cfg);

        AstroAssetRepository astroAssets = new AstroAssetRepository(new ObjectMapper());
        astroAssets.loadAll();
        AstroGenerator astroGenerator = new AstroGenerator(astroAssets, cfg.worldSeed);
        List<StarSystem> systems = astroGenerator.generateSystemsForMap(worldMap, cfg);

        AstroData astro = new AstroData(systems);
        return new StarAxisGameRuntime(new WorldState(time, worldMap, astro));
    }

    @Override
    public void start() {
        publishRealTimeSnapshot();
        // 开局先发布一份“上一日结算”（占位）：settledDay=当前日-1（下限 0）
        dailySettlementBuffer.publishForDay(Math.max(0, worldState.time.gameDatetimeDay - 1),
                worldState.worldMap.getSectorsByCoordView().size());
    }

    @Override
    public void update(float dtSeconds) {
        // PrepareTick
        double dtGameHours = SimulationClock.prepareTick(worldState.time);

        // 处理 Command 队列并更新 WorldState
        commandBus.executeCommands(worldState, dtGameHours);
        // TODO: 各系统使用 dtGameHours 推进

        // Commit
        boolean dayChanged = SimulationClock.commitTick(worldState.time);
        if (dayChanged) {
            // 跨日结算：发布“上一日已落账结果”（占位）
            int settledDay = Math.max(0, worldState.time.gameDatetimeDay - 1);
            dailySettlementBuffer.publishForDay(settledDay, worldState.worldMap.getSectorsByCoordView().size());
        }

        publishRealTimeSnapshot();
    }

    @Override
    public void stop() {
    }

    public WorldState getWorldStateForSimOnly() {
        return worldState;
    }

    public RealTimeWorldState getRealTimeWorldStateReadonly() {
        return realTimeBuffer.getActive();
    }

    public DailySettlementStateBuffer getDailySettlementStateBufferForReadonly() {
        return dailySettlementBuffer;
    }

    private void publishRealTimeSnapshot() {
        RealTimeWorldState s = realTimeBuffer.beginFillInactive();

        s.simulationTick = worldState.time.simulationTick;
        s.gameDatetimeDay = worldState.time.gameDatetimeDay;
        s.accGameHoursInDay = worldState.time.accGameHoursInDay;
        s.worldRadius = worldState.worldMap.radius;

        for (WorldSector sector : worldState.worldMap.getSectorsView()) {
            s.putSectorCenter(sector.coord, sector.centerWorldGU);
        }

        for (StarSystem system : worldState.astro.getSystemsView()) {
            // 1. 创建并注册重心实体
            Entity barycenter = new Entity();
            barycenter.entityId = system.barycenterEntityId;
            barycenter.entityType = EntityType.SYSTEM_BARYCENTER;
            barycenter.systemId = system.systemId;
            barycenter.parentEntityId = 0;
            barycenter.sectorCoord = system.sectorCoord;
            barycenter.posWorldGU = system.centerWorldGU;
            s.putEntity(barycenter);
            s.putEntitySnapshot(new EntitySnapshot(
                    barycenter.entityId,
                    barycenter.entityType,
                    barycenter.systemId,
                    barycenter.parentEntityId,
                    barycenter.sectorCoord,
                    barycenter.posWorldGU,
                    new EntitySnapshot.SystemBarycenterDetails()));

            // 2. 注册恒星实体
            for (StarBody star : system.stars) {
                star.systemId = system.systemId;
                star.parentEntityId = system.barycenterEntityId; // 单星系统也挂在重心下
                star.sectorCoord = system.sectorCoord;
                star.posWorldGU = system.centerWorldGU; // 单星系统：恒星位置=重心位置
                s.putEntity(star);
                s.putEntitySnapshot(new EntitySnapshot(
                        star.entityId,
                        star.entityType,
                        star.systemId,
                        star.parentEntityId,
                        star.sectorCoord,
                        star.posWorldGU,
                        new EntitySnapshot.StarDetails(star.starTypeId, star.radiusGU, star.massSolar,
                                star.temperatureK)));
            }

            // 3. 注册行星实体
            for (PlanetBody planet : system.planets) {
                planet.systemId = system.systemId;
                // 默认行星归属重心（单星系统里重心=恒星）
                planet.parentEntityId = system.barycenterEntityId;
                if (planet.orbit != null) {
                    planet.orbit.orbitCenterEntityId = system.barycenterEntityId;
                }
                planet.sectorCoord = system.sectorCoord;
                // 行星 posWorldGU 由前端根据轨道计算，这里不填充
                s.putEntity(planet);

                OrbitSnapshot orbitSnapshot = null;
                if (planet.orbit != null) {
                    orbitSnapshot = new OrbitSnapshot(
                            planet.orbit.orbitCenterEntityId,
                            planet.orbit.semiMajorAxisGU,
                            planet.orbit.eccentricity,
                            planet.orbit.inclinationDeg,
                            planet.orbit.periapsisArgDeg,
                            planet.orbit.orbitalPeriodDays,
                            planet.orbit.meanAnomalyDegAtEpoch);
                }

                s.putEntitySnapshot(new EntitySnapshot(
                        planet.entityId,
                        planet.entityType,
                        planet.systemId,
                        planet.parentEntityId,
                        planet.sectorCoord,
                        planet.posWorldGU,
                        new EntitySnapshot.PlanetDetails(planet.planetTypeId, planet.radiusGU,
                                planet.rotationPeriodHours,
                                orbitSnapshot)));
            }
        }

        realTimeBuffer.swapPublish();
    }
}
