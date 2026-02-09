package staraxis.game.mod;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ModOrder
 *
 * 对应 gamedata/mods/mod-order.json 的数据模型喵。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModOrder {

    public int schemaVersion = 1;

    public List<String> order = new ArrayList<>();

    public Set<String> disabled = new HashSet<>();
}
