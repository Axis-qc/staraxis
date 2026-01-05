package com.staraxis.game.client.ui.model;

import com.badlogic.gdx.utils.Array;

/**
 * 设置界面数据模型 (Settings Model)
 */
public class SettingsModel extends BaseUIModel {

    private final Array<String> resolutions = new Array<>();
    private String currentLanguage;
    private float musicVolume;
    private float soundVolume;

    public void setResolutions(Array<String> res) {
        this.resolutions.clear();
        this.resolutions.addAll(res);
        setDirty(true);
    }

    public Array<String> getResolutions() {
        return resolutions;
    }

    public void setCurrentLanguage(String language) {
        this.currentLanguage = language;
        setDirty(true);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setVolumes(float music, float sound) {
        this.musicVolume = music;
        this.soundVolume = sound;
        setDirty(true);
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public float getSoundVolume() {
        return soundVolume;
    }
}
