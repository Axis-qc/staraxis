package staraxis.ui.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod 加载顺序的数据模型（POJO），对应于 gamedata/mods/mod-order.json。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModOrder {

    public int schemaVersion = 1;
    public List<String> order = new ArrayList<>();

}
