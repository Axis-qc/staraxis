package staraxis.game;

import staraxis.game.sim.SimulationClock;
import staraxis.game.sim.SimulationTime;
import staraxis.game.state.DailySettlementStateBuffer;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.RealTimeWorldStateBuffer;
import staraxis.game.state.WorldState;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldGenerator;
import staraxis.game.world.WorldSector;

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

    public StarAxisGameRuntime(WorldState worldState) {
        this.worldState = worldState;
    }

    public static StarAxisGameRuntime newGame(WorldGenConfig cfg) {
        SimulationTime time = new SimulationTime();
        return new StarAxisGameRuntime(new WorldState(time, WorldGenerator.generate(cfg)));
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
        SimulationClock.prepareTick(worldState.time);

        // TODO: 处理 Command 队列并更新 WorldState
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

        realTimeBuffer.swapPublish();
    }
}
