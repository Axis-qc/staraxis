package staraxis.ui.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameDataProvider implements DataProvider {

    @Override
    public List<Map<String, Object>> getData(String source) {
        switch (source) {
            case "saves":
                return scanSaves();
            case "nations":
                return scanNations();
            default:
                Gdx.app.error("GameDataProvider", "Unknown data source: " + source);
                return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> scanSaves() {
        List<Map<String, Object>> saves = new ArrayList<>();
        try {
            FileHandle dir = Gdx.files.local("gamedata/saves");
            if (dir.exists() && dir.isDirectory()) {
                for (FileHandle fh : dir.list()) {
                    if (fh.isDirectory()) {
                        FileHandle state = fh.child("state.json");
                        if (state.exists()) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("name", fh.name());
                            item.put("id", fh.name());
                            saves.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("GameDataProvider", "Failed to scan saves", e);
        }
        return saves;
    }

    private List<Map<String, Object>> scanNations() {
        List<Map<String, Object>> nations = new ArrayList<>();
        try {
            FileHandle dir = Gdx.files.internal("nations");
            if (dir.exists() && dir.isDirectory()) {
                for (FileHandle fh : dir.list()) {
                    if (fh.isDirectory()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("name", fh.name());
                        item.put("id", fh.name());
                        nations.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("GameDataProvider", "Failed to scan nations", e);
        }
        if (nations.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("name", "player_empire");
            fallback.put("id", "player_empire");
            nations.add(fallback);
        }
        return nations;
    }
}
