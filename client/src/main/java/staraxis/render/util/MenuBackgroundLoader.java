package staraxis.render.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
/** MenuBackgroundLoader — 从主菜单配置 JSON 中读取背景图路径。
 *
 * ClientGame 和 UiPreviewApp 共用，避免重复代码。
 */
public final class MenuBackgroundLoader {

    private MenuBackgroundLoader() {
    }

    /**
    /** 从 ui/gameui/main-menu/main_menu.json 中解析 backgroundImage 字段。
     */
    public static String loadBackgroundImage() {
        JsonValue root = new JsonReader().parse(Gdx.files.internal("ui/gameui/main-menu/main_menu.json"));
        JsonValue props = root.get("properties");
        return props == null ? null : props.getString("backgroundImage", null);
    }
}
