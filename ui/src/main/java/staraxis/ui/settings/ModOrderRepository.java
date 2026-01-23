package staraxis.ui.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod 顺序持久化仓库：负责读写 gamedata/mods/mod-order.json。
 */
public class ModOrderRepository {

    private static final String MODS_ROOT_PATH = "../gamedata/mods/";
    private static final String MOD_ORDER_FILENAME = "mod-order.json";

    private final ObjectMapper mapper;

    public ModOrderRepository() {
        this.mapper = new ObjectMapper();
    }

    public ModOrder load() {
        FileHandle orderFile = Gdx.files.local(MODS_ROOT_PATH + MOD_ORDER_FILENAME);
        if (!orderFile.exists()) {
            return new ModOrder();
        }

        try {
            return mapper.readValue(orderFile.read(), ModOrder.class);
        } catch (Exception e) {
            Gdx.app.error("ModOrderRepository", "Failed to parse " + MOD_ORDER_FILENAME + ", using default order.", e);
            return new ModOrder();
        }
    }

    public void save(List<String> orderedModIds) {
        ModOrder modOrder = new ModOrder();
        modOrder.order = orderedModIds != null ? new ArrayList<>(orderedModIds) : new ArrayList<>();

        FileHandle orderFile = Gdx.files.local(MODS_ROOT_PATH + MOD_ORDER_FILENAME);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(orderFile.writer(false), modOrder);
        } catch (Exception e) {
            Gdx.app.error("ModOrderRepository", "Failed to save " + MOD_ORDER_FILENAME, e);
        }
    }
}
