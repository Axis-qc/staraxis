package com.staraxis.game.shared.network;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 内存消息队列 (In-Memory Message Queue)
 *
 * 使用的接口: BlockingQueue 提供的接口: 模拟本地 C/S 通信，用于开发初期脱离网络环境测试
 */
public class MemoryQueue<T> /* 内存队列 */ {

    private final BlockingQueue<T> queue /* 内部队列 */ = new LinkedBlockingQueue<>();

    public void send /* 发送 */(T message) {
        queue.offer(message);
    }

    public T receive /* 接收 */() {
        return queue.poll();
    }

    public boolean isEmpty /* 是否为空 */() {
        return queue.isEmpty();
    }
}
