package staraxis.client;

import staraxis.client.net.ControlChannelClient;
import staraxis.client.net.DataChannelClient;
import staraxis.client.utils.Logger;
import staraxis.net.proto.ServerHello;

public class ClientMain {

    public static void main(String[] args) throws Exception {
        Logger.info("Client starting ...");
        String host = "127.0.0.1";
        int controlPort = 9000;
        int dataPort = 9001;

        ControlChannelClient control = new ControlChannelClient(host, controlPort);
        ServerHello hello = control.connect().get();
        Logger.info("Handshake success, clientId=" + hello.getClientId());

        DataChannelClient data = new DataChannelClient(host, dataPort, hello.getClientId());
        data.connect().get();

        // 简易阻塞
        Thread.currentThread().join();
    }
}
