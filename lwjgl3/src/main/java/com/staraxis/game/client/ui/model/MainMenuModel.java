package com.staraxis.game.client.ui.model;

import com.badlogic.gdx.utils.Array;

/**
 * 主菜单数据模型 (Main Menu Model)
 */
public class MainMenuModel extends BaseUIModel {

    private final Array<String> menuItems = new Array<>();

    public void setMenuItems(String[] items) {
        menuItems.clear();
        menuItems.addAll(items);
        setDirty(true);
    }

    public Array<String> getMenuItems() {
        return menuItems;
    }
}
