package staraxis.server;

public class TickState {

    public static final int TICKS_PER_SECOND = 25;
    public static final double BASE_DT_GAME_HOURS = 1.0 / TICKS_PER_SECOND;

    private long serverTick;
    private double timeScale;
    private double dtGameHours;

    public TickState() {
        this.serverTick = 0;
        this.timeScale = 1.0;
        this.dtGameHours = BASE_DT_GAME_HOURS * this.timeScale;
    }

    public long getServerTick() {
        return serverTick;
    }

    public double getTimeScale() {
        return timeScale;
    }

    public double getDtGameHours() {
        return dtGameHours;
    }

    public void setTimeScale(double timeScale) {
        this.timeScale = timeScale;
        this.dtGameHours = BASE_DT_GAME_HOURS * this.timeScale;
    }

    public void advanceTick() {
        this.serverTick += 1;
        this.dtGameHours = BASE_DT_GAME_HOURS * this.timeScale;
    }
}
