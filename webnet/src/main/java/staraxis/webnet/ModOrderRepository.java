package staraxis.webnet;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class ModOrderRepository {

    private static final String MOD_ORDER_PATH = "gamedata/mods/mod-order.json";

    private final ObjectMapper mapper = new ObjectMapper();

    public ModOrder load() {
        File f = new File(MOD_ORDER_PATH);
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
