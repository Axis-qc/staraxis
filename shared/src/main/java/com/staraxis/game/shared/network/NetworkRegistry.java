package com.staraxis.game.shared.network;

import com.esotericsoftware.kryo.Kryo;
import com.staraxis.game.shared.model.*;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * 网络注册表 (Network Protocol Registry)
 *
 * 使用的接口: Kryo 提供的接口: 注册所有 C/S 通讯所需的类，确保序列化顺序一致
 */
public class NetworkRegistry {

    /**
     * 注册类 (Register Classes) 必须严格遵守 contracts/network-protocol.md 定义的顺序
     */
    public static void register(Kryo kryo) {
        kryo.register(HashMap.class);
        kryo.register(ArrayList.class);
        kryo.register(Vector2.class);
        kryo.register(ConnectionRequest.class);
        kryo.register(ConnectionResponse.class);
        kryo.register(PlayerCommandMessage.class);
        kryo.register(GameStateUpdate.class);
        kryo.register(GameState.class);
        kryo.register(EntityState.class);
        kryo.register(WorldMetadata.class);
        kryo.register(Command.class);
        // 后续按需追加 (Append more as needed)
    }
}
