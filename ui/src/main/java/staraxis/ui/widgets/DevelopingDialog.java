package staraxis.ui.widgets;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class DevelopingDialog extends Dialog {

    public DevelopingDialog(Skin skin) {
        super("提示", skin);
        text("开发中");
        button("确定");
        setModal(true);
        setMovable(false);
        setResizable(false);
    }

}
