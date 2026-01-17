package staraxis.ui.widgets;

import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import staraxis.ui.i18n.I18nService;

public class DevelopingDialog extends Dialog {

    public DevelopingDialog(Skin skin, I18nService i18n) {
        super(i18n.get("dialog.developing.title"), skin);
        text(i18n.get("dialog.developing.text"));
        button(i18n.get("dialog.developing.confirm"));
        setModal(true);
        setMovable(false);
        setResizable(false);
    }

}
