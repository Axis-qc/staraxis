package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * EntityInfoViewModel（实体信息视图模型）喵。
 *
 * UI 组件（{@link EntitySummaryPanel} 左下摘要区 / {@link EntityInfoPanel} 详细窗口）
 * 只消费本模型，不关心数据来自真实快照（Phase 2 Step B 的 EntityInfoAssembler）
 * 还是演示假数据（EntityPanelDemoData）。
 */
public class EntityInfoViewModel {

    /** 字段行：key-value 展示，color 为可选的值着色，section 用于分组标题。 */
    public record FieldEntry(String key, String value, Color color, boolean section) {
        public FieldEntry(String key, String value) {
            this(key, value, null, false);
        }

        public FieldEntry(String key, String value, Color color) {
            this(key, value, color, false);
        }

        /** 创建分组标题行，分组标题不作为普通 key-value 字段渲染。 */
        public static FieldEntry section(String title) {
            return new FieldEntry(title, "", null, true);
        }
    }

    /** 指令按钮：点击时 id 上抛给外部处理；enabled=false 时置灰且点击不上抛喵 */
    public record ActionEntry(String id, String label, boolean enabled) {}

    /** 实体名称（标题）喵 */
    public String title = "";
    /** 类型标签文本（如"宜居"/"严酷"），空串则不显示喵 */
    public String typeLabel = "";
    /** 类型标签颜色（habitability 等着色），null 时用主题正文色喵 */
    public Color typeColor;
    /** 摘要字段（左下摘要区显示，建议不超过 4 行）喵 */
    public final List<FieldEntry> summaryFields = new ArrayList<>();
    /** 完整字段（详细窗口显示）喵 */
    public final List<FieldEntry> detailFields = new ArrayList<>();
    /** 指令按钮列表喵 */
    public final List<ActionEntry> actions = new ArrayList<>();

    public EntityInfoViewModel title(String title) {
        this.title = title;
        return this;
    }

    public EntityInfoViewModel typeLabel(String label, Color color) {
        this.typeLabel = label != null ? label : "";
        this.typeColor = color;
        return this;
    }

    public EntityInfoViewModel summary(String key, String value) {
        this.summaryFields.add(new FieldEntry(key, value));
        return this;
    }

    public EntityInfoViewModel detail(String key, String value) {
        return detail(key, value, null);
    }

    public EntityInfoViewModel detail(String key, String value, Color color) {
        this.detailFields.add(new FieldEntry(key, value, color));
        return this;
    }

    public EntityInfoViewModel action(String id, String label, boolean enabled) {
        this.actions.add(new ActionEntry(id, label, enabled));
        return this;
    }
}
