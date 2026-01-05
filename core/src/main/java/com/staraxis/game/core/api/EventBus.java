package com.staraxis.game.core.api;

/**
 * 全局事件总线接口 (Global Event Bus Interface) 逻辑层与 UI 层的唯一通信通道，遵循分层架构原则。
 */
public interface EventBus {

    /**
     * 发布一个事件对象。
     *
     * @param event 事件数据对象
     */
    void post(Object event);

    /**
     * 注册一个订阅者。
     *
     * @param subscriber 订阅者对象
     */
    void register(Object subscriber);

    /**
     * 注销一个订阅者。
     *
     * @param subscriber 订阅者对象
     */
    void unregister(Object subscriber);
}
