# Quickstart: 001-game-design-bible

## 目标

在最短时间内让新成员理解并遵循 StarAxis 的统一口径（坐标/单位/时间/术语/命名/交互分层），并能据此开展后续系统开发。

## 你会得到什么

- `spec.md`：Design Bible 总览入口（结论 + 链接）
- `conventions/`：命名/术语/时间推进等规范
- `data-model.md`：文档域数据模型（便于后续做自动校验与目录组织）

## 推荐阅读顺序（30 分钟）

1. `spec.md`：先理解范围、权威性、Out-of-scope
2. `conventions/terminology.md`：关键术语中英对照 + 禁用同义词
3. `conventions/time-advancement.md`：模拟时间、日结算、时间停止口径
4. `data-model/coordinates.md`：双坐标系、比例尺、zoomFactor、误差预算
5. `conventions/naming.md`：命名规则与覆盖示例

## 如何自检（手工）

- 能否在 5 分钟内找到并复述：
  - 坐标轴与角度方向
  - 100px=1AU 与 zoomFactor 换算
  - 24h=1d、1h=1s、日结算
  - 时间停止分域冻结规则
- 能否根据术语表判断“星区/星域”等是否允许混用
- 能否按命名规范写出一组字段名（坐标/时间/资源/战斗）且不使用拼音

## 贡献规则（写文档时）

- 首次引入术语：先检查术语表是否已存在
- 引用《游戏大纲.md》或宪章：必须注明来源与差异（若有）
- 避免实现细节：不要写具体代码、框架、类名（除非 spec 明确要求“可直接照做”的坐标/结算策略）
