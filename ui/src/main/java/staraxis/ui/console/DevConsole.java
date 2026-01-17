package staraxis.ui.console;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import staraxis.ui.Gui;
import staraxis.ui.utils.DragAndClampListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开发者控制台系统（独立小窗口）。
 *
 * 设计要点：
 * - 这是一个纯 UI/表现层工具，不包含任何核心模拟逻辑，遵循分层边界。
 * - 通过 Gui 访问 UI 层服务（Skin/Stage），并通过 actionId/服务调用触发 UI 层行为（如 reload_ui）。
 * - 快捷键（~, F12, ESC）通过 InputAdapter 全局监听，确保在任何焦点状态下都能响应。
 * - 拖动与边界限制由通用的 {@link DragAndClampListener} 实现，避免重复逻辑。
 */
public class DevConsole extends InputAdapter implements ConsoleOutput {

    private final Gui gui;
    private final DevConsoleView view;

    // NOTE: 这里用 Table 自己实现“窗口”，而不是用 libGDX 的 Window。
    // 原因是 Window 的标题栏样式与拖动行为高度依赖 Skin，容易出现“看不见/拖不动”的问题。
    // 自己拼装 Table(titleBar) + Table(content) 更可控、更稳定。
    private final Table window;
    private final Table titleBar;
    private final Label titleLabel;

    private final Map<String, ConsoleCommand> commands = new HashMap<>();
    private final DragAndClampListener dragListener;

    private boolean visible = false;

    public DevConsole(Gui gui) {
        this.gui = gui;
        Skin skin = gui.get(Skin.class);
        this.view = new DevConsoleView(skin);

        this.window = new Table(skin);
        this.titleBar = new Table(skin);
        this.titleLabel = new Label("Console", skin);
        this.dragListener = new DragAndClampListener(window);

        setupWindow();
        registerDefaultCommands();
    }

    private void setupWindow() {
        Stage stage = gui.getStage();
        Skin skin = window.getSkin();

        try {
            Drawable bg = skin.getDrawable("window");
            window.setBackground(bg);
        } catch (Exception ignored) {
        }

        titleLabel.setColor(Color.WHITE);
        titleBar.add(titleLabel).left().padLeft(8f).expandX().fillX();
        titleBar.row();

        try {
            Drawable titleBg = skin.getDrawable("gray");
            titleBar.setBackground(titleBg);
        } catch (Exception ignored) {
        }

        window.clearChildren();
        window.add(titleBar).height(20f).expandX().fillX().row();
        window.add(view).expand().fill().row();

        float width = Math.min(640f, stage.getWidth() * 0.8f);
        float height = Math.min(320f, stage.getHeight() * 0.5f);
        window.setSize(width, height);

        repositionToTopLeft();
        window.setVisible(false);
        stage.addActor(window);

        titleBar.addListener(dragListener);

        // NOTE: 监听 Stage 级别的 touchDown，用于实现“点击窗口外取消焦点”。
        // 返回 false 确保事件能继续传递给其他 Actor。
        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!visible)
                    return false;

                Actor hit = stage.hit(x, y, true);
                if (hit != null && hit.isDescendantOf(window)) {
                    stage.setKeyboardFocus(view.getInputField());
                    window.toFront();
                } else {
                    stage.setKeyboardFocus(null);
                }
                return false;
            }
        });

        TextField input = view.getInputField();
        input.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                execute(textField.getText());
                textField.setText("");
            }
        });
    }

    /**
     * 在窗口 resize 后调用，确保控制台位置被夹紧在屏幕内。
     */
    public void onResize() {
        dragListener.onResize();
    }

    private void repositionToTopLeft() {
        Stage stage = gui.getStage();
        float margin = 8f;
        window.setPosition(margin, stage.getHeight() - window.getHeight() - margin);
    }

    private void registerDefaultCommands() {
        register(new HelpCommand());
        register(new ClearCommand());
        register(new ReloadUiCommand(gui));
        register(new LogTailCommand());
    }

    public void register(ConsoleCommand command) {
        commands.put(command.name().toLowerCase(), command);
    }

    private void execute(String input) {
        if (input == null || input.isBlank())
            return;
        info("> " + input);

        String[] parts = input.trim().split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        ConsoleCommand command = commands.get(commandName);
        if (command == null) {
            error("Unknown command: " + commandName);
            return;
        }

        try {
            command.execute(args, this);
        } catch (Exception e) {
            error("Error executing command: " + e.getMessage());
            Gdx.app.error("DevConsole", "Command execution failed", e);
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        // NOTE: 全局热键始终响应，确保在失去焦点时也能关闭/打开。
        if (keycode == Input.Keys.GRAVE || keycode == Input.Keys.F12) {
            toggle();
            return true;
        }
        // NOTE: ESC 仅在控制台可见时生效，不要求窗口获得键盘焦点。
        if (keycode == Input.Keys.ESCAPE && visible) {
            hide();
            return true;
        }
        return false;
    }

    public void toggle() {
        if (visible)
            hide();
        else
            show();
    }

    public void show() {
        visible = true;
        repositionToTopLeft();
        window.setVisible(true);
        window.toFront();
        // NOTE: 显示后默认不抢焦点，必须点击窗口才获得焦点。
        gui.getStage().setKeyboardFocus(null);
    }

    public void hide() {
        visible = false;
        window.setVisible(false);
        gui.getStage().setKeyboardFocus(null);
    }

    @Override
    public void info(String message) {
        view.addLog(message, "default");
    }

    @Override
    public void error(String message) {
        view.addLog(message, "default");
    }

    // --- Built-in Commands (as inner classes for simplicity) ---

    private class HelpCommand implements ConsoleCommand {
        @Override
        public String name() {
            return "help";
        }

        @Override
        public String help() {
            return "Lists all available commands.";
        }

        @Override
        public void execute(String[] args, ConsoleOutput out) {
            String commandList = commands.keySet().stream().sorted().collect(Collectors.joining(", "));
            out.info("Available commands: " + commandList);
        }
    }

    private class ClearCommand implements ConsoleCommand {
        @Override
        public String name() {
            return "clear";
        }

        @Override
        public String help() {
            return "Clears the console output.";
        }

        @Override
        public void execute(String[] args, ConsoleOutput out) {
            view.clear();
        }
    }

    private static class ReloadUiCommand implements ConsoleCommand {
        private final Gui gui;

        public ReloadUiCommand(Gui gui) {
            this.gui = gui;
        }

        @Override
        public String name() {
            return "reload_ui";
        }

        @Override
        public String help() {
            return "Reloads the main menu UI from JSON.";
        }

        @Override
        public void execute(String[] args, ConsoleOutput out) {
            out.info("Reloading main menu...");
            gui.showMainMenu();
        }
    }

    private static class LogTailCommand implements ConsoleCommand {
        @Override
        public String name() {
            return "log_tail";
        }

        @Override
        public String help() {
            return "Shows the last N lines of the error log. Usage: log_tail <n>";
        }

        @Override
        public void execute(String[] args, ConsoleOutput out) {
            int n = 10;
            if (args.length > 0) {
                try {
                    n = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    out.error("Invalid number: " + args[0]);
                    return;
                }
            }
            try {
                java.io.File logFile = new java.io.File("../gamedata/logs/error.log");
                if (!logFile.exists()) {
                    out.error("error.log not found.");
                    return;
                }
                java.util.List<String> lines = java.nio.file.Files.readAllLines(logFile.toPath());
                int start = Math.max(0, lines.size() - n);
                for (int i = start; i < lines.size(); i++) {
                    out.info(lines.get(i));
                }
            } catch (java.io.IOException e) {
                out.error("Failed to read error.log: " + e.getMessage());
            }
        }
    }
}
