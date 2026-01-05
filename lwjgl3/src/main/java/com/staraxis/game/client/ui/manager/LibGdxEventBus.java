package com.staraxis.game.client.ui.manager;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Method;
import com.staraxis.game.core.api.EventBus;

/**
 * 基于 LibGDX 的反射机制实现的简单事件总线。
 */
public class LibGdxEventBus implements EventBus {

    private final ObjectMap<Class<?>, Array<SubscriberMethod>> subscriptions = new ObjectMap<>();

    @Override
    public void post(Object event) {
        Class<?> eventClass = event.getClass();
        Array<SubscriberMethod> methods = subscriptions.get(eventClass);
        if (methods != null) {
            for (SubscriberMethod method : methods) {
                method.invoke(event);
            }
        }
    }

    @Override
    public void register(Object subscriber) {
        Method[] methods = ClassReflection.getMethods(subscriber.getClass());
        for (Method method : methods) {
            if (method.getName().startsWith("on") && method.getParameterTypes().length == 1) {
                Class<?> eventType = method.getParameterTypes()[0];
                Array<SubscriberMethod> subs = subscriptions.get(eventType);
                if (subs == null) {
                    subs = new Array<>();
                    subscriptions.put(eventType, subs);
                }
                subs.add(new SubscriberMethod(subscriber, method));
            }
        }
    }

    @Override
    public void unregister(Object subscriber) {
        for (Array<SubscriberMethod> subs : subscriptions.values()) {
            for (int i = subs.size - 1; i >= 0; i--) {
                if (subs.get(i).subscriber == subscriber) {
                    subs.removeIndex(i);
                }
            }
        }
    }

    private static class SubscriberMethod {

        final Object subscriber;
        final Method method;

        SubscriberMethod(Object subscriber, Method method) {
            this.subscriber = subscriber;
            this.method = method;
        }

        void invoke(Object event) {
            try {
                method.invoke(subscriber, event);
            } catch (Exception e) {
                throw new RuntimeException("事件处理失败: " + event.getClass().getSimpleName(), e);
            }
        }
    }
}
