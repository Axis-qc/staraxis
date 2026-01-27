package staraxis.webnet;

/**
 * ModOrder
 *
 * 作用：
 * - Mod 加载顺序配置的数据模型（对应 gamedata/mods/mod-order.json）。
 * - 由 ModOrderRepository 反序列化得到，并由 ModManager 用于确定 Mod 顺序。
 *
 * 字段说明：
 * - schemaVersion：配置结构版本号（便于未来演进）。
 * - order：modId 列表，表示优先加载顺序。
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModOrder {

    public int schemaVersion = 1;
    public List<String> order = new ArrayList<>();
    public Set<String> disabled = new HashSet<>();
}

