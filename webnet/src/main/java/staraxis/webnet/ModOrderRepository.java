package staraxis.webnet;

/**
 * ModOrderRepository
 *
 * 作用：
 * - 读取 Mod 加载顺序的权威配置文件：gamedata/mods/mod-order.json。
 * - 将其解析为 ModOrder 对象供 ModManager 使用。
 *
 * 口径：
 * - 若文件不存在或解析失败，则返回一个默认的 ModOrder（空 order）。
 *
 * 注意事项：
 * - 文件读取与 JSON 解析属于阻塞 IO：如果在 Undertow 请求线程中调用，应使用 exchange.dispatch(...) 切换到 worker 线程。
 */

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

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
