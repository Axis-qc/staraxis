package staraxis.game;

public interface GameRuntime {

    void start();

    void update(float dtSeconds);

    void stop();
}
