package staraxis.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简易模块注册表，只负责固定顺序调用。
 * 线程安全由外部（主循环线程）保证。
 */
public final class ModuleRegistry {
  private final List<IGameModule> modules = new ArrayList<>();

  public void register(IGameModule module) {
    modules.add(module);
  }

  public void freezeOrder() {
    // 按需要排序，这里保持注册顺序
  }

  public void onPrepare() { modules.forEach(IGameModule::onPrepare); }
  public void onUpdate(double dt) { modules.forEach(m -> m.onUpdate(dt)); }
  public void onCommit() { modules.forEach(IGameModule::onCommit); }
  public void onPostUpdate() { modules.forEach(IGameModule::onPostUpdate); }
}
