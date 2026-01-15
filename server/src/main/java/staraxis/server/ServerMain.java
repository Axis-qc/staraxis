package staraxis.server;

import staraxis.server.net.ControlChannelServer;
import staraxis.server.net.DataChannelServer;
import staraxis.server.utils.Logger;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        Logger.info("Server starting ...");
        int controlPort = 9000;
        int dataPort = 9001;

        SessionManager sessionManager = new SessionManager();

        ControlChannelServer controlServer = new ControlChannelServer(controlPort, sessionManager);
        controlServer.start();
        DataChannelServer dataServer = new DataChannelServer(dataPort, sessionManager);
        dataServer.start();

        ServerMainLoop loop = new ServerMainLoop(sessionManager);
        Thread loopThread = new Thread(loop, "ServerMainLoop");
        loopThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutdown requested, stopping...");
            loop.shutdown();
            controlServer.shutdown();
            dataServer.shutdown();
            try { loopThread.join(); } catch (InterruptedException ignored) {}
        }));
    }
}
