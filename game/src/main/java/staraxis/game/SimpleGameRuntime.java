package staraxis.game;

public class SimpleGameRuntime implements GameRuntime {

    private int simulationTick;

    @Override
    public void start() {
        simulationTick = 0;
    }

    @Override
    public void update(float dtSeconds) {
        simulationTick++;
    }

    @Override
    public void stop() {
    }

    public int getSimulationTick() {
        return simulationTick;
    }
}
