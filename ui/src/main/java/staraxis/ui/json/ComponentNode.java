package staraxis.ui.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级 POJO，用于保存解析后的 UI 组件树信息。
 */
public class ComponentNode {
    public String type;
    public String name;
    public Map<String, Object> properties = new HashMap<>();
    public List<ComponentNode> children = new ArrayList<>();

    public ComponentNode() {
    }

    public ComponentNode(String type) {
        this.type = type;
    }
}
