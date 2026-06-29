package staraxis.ui.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * GPU 枚举服务。
 *
 * - 平台侧（Lwjgl3Launcher）负责枚举系统 GPU 并填充列表。
 * - 索引 0 = 当前激活的 GPU。
 * - UI 层（SettingsScreen）只读取出列表用于选择器。
 */
public final class GpuService {

    private GpuService() {}

    /** 可用 GPU/显示器名称列表（平台侧填充） */
    public static final List<String> available = new ArrayList<>();

    /**
     * 获取指定索引的 GPU 名称，越界返回 "Default"。
     */
    public static String getName(int index) {
        if (index >= 0 && index < available.size())
            return available.get(index);
        return "Default";
    }

    /**
     * 获取当前选中的 GPU 名称。
     */
    public static String getSelectedName(int index) {
        return getName(index);
    }
}
