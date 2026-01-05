package io.staraxis;

import java.util.LinkedList;
import java.util.Queue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;
import com.staraxis.game.client.GameClient;
import com.staraxis.game.core.engine.GameServer;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.network.ConnectionRequest;
import com.staraxis.game.shared.network.ConnectionResponse;
import com.staraxis.game.shared.network.MemoryQueue;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all
 * platforms.
 */
public class Main extends ApplicationAdapter {

    private SpriteBatch batch;
    private Texture image;
    private BitmapFont font;

    private GameServer server;
    private GameClient client;
    private MemoryQueue<Object> clientToServer;
    private long myPlayerId;

    private final Queue<GameState> networkDelayQueue = new LinkedList<>();
    private final float SIMULATED_RTT_MS = 150f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        image = new Texture("libgdx.png");
        font = new BitmapFont();

        // 初始化本地服务器与客户端 (Local simulation setup)
        clientToServer = new MemoryQueue<>();
        server = new GameServer();
        client = new GameClient(clientToServer);

        server.start();

        // 模拟握手 (Simulate Handshake)
        ConnectionRequest req = new ConnectionRequest();
        req.playerName = "LocalPlayer";
        req.version = "1.0.0";
        ConnectionResponse res = server.handleConnection(req);
        myPlayerId = res.assignedPlayerId;
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // 模拟网络延迟 (Simulate 150ms RTT - T036)
        final GameState currentServerState = server.getCurrentState();
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                client.onGameStateUpdate(currentServerState);
            }
        }, SIMULATED_RTT_MS / 1000f / 2f); // 往返的一半是单程延迟

        client.update(delta);

        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        batch.begin();
        batch.draw(image, 140, 210);

        // 调试信息叠加层 (Debug Overlay - T040)
        font.draw(batch, "Server Tick: " + server.getCurrentState().tick, 10, 470);
        font.draw(batch, "Client Connected: " + client.isConnected(), 10, 450);
        if (client.getInterpolatedState() != null) {
            font.draw(batch, "Interpolated Tick: " + client.getInterpolatedState().tick, 10, 430);
            font.draw(batch, "Sim Time: " + client.getInterpolatedState().simulationTime + "ms", 10, 410);
        }

        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        image.dispose();
        font.dispose();
        server.stop();
    }
}
