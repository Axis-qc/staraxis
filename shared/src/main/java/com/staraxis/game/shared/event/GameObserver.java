package com.staraxis.game.shared.event;

/**
 * 通用观察者接口 (Common Observer Interface)
 *
 * 使用的接口: 无 提供的接口: 实现 C/S 间的解耦消息监听
 */
public interface GameObserver<T> {

    void onEvent(T event);
}
