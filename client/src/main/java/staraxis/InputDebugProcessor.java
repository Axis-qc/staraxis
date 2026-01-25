package staraxis;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

public class InputDebugProcessor implements InputProcessor {

    private static String keyNameSafe(int keycode) {
        try {
            return Input.Keys.toString(keycode);
        } catch (Exception e) {
            return String.valueOf(keycode);
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        Gdx.app.log("InputDebug", "keyDown keycode=" + keycode + " (" + keyNameSafe(keycode) + ")");
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        Gdx.app.log("InputDebug", "keyUp keycode=" + keycode + " (" + keyNameSafe(keycode) + ")");
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        Gdx.app.log("InputDebug", "keyTyped char='" + character + "' (code=" + (int) character + ")");
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Gdx.app.log("InputDebug",
                "touchDown x=" + screenX + " y=" + screenY + " pointer=" + pointer + " button=" + button);
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        Gdx.app.log("InputDebug",
                "touchUp x=" + screenX + " y=" + screenY + " pointer=" + pointer + " button=" + button);
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        Gdx.app.log("InputDebug",
                "touchCancelled x=" + screenX + " y=" + screenY + " pointer=" + pointer + " button=" + button);
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        Gdx.app.log("InputDebug", "scrolled amountX=" + amountX + " amountY=" + amountY);
        return false;
    }
}
