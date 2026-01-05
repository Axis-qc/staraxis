package com.staraxis.game.shared.event;

/**
 * 事件发布者 (Event Subject)
 */
public interface EventPublisher<T> {

    void addObserver(GameObserver<T> observer);

    void removeObserver(GameObserver<T> observer);

    void notifyObservers(T event);
}
