package staraxis.game.mod;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * ModOrderRepository
 *
 * 读取 Mod 加载顺序权威配置：gamedata/mods/mod-order.json 喵。
 */
public class ModOrderRepository {

    private static final String MOD_ORDER_PATH = "gamedata/mods/mod-order.json";

    private final ObjectMapper mapper = new ObjectMapper();

    public File file() {
        return new File(MOD_ORDER_PATH);
    }

    public ModOrder load() {
        File f = file();
        if (!f.exists() || !f.isFile()) {
            return new ModOrder();
        }
        try {
            return mapper.readValue(f, ModOrder.class);
        } catch (Exception e) {
            return new ModOrder();
        }
    }
}
