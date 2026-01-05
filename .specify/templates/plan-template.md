# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]  
**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]  
**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]  
**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]  
**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]
**Project Type**: [single/web/mobile - determines source structure]  
**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]  
**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]  
**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

- **UI 层独立性 (Independent UI Layer)**: UI 是否作为独立层级存在？是否通过数据绑定或 UI Model 与逻辑解耦？

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **妯″潡鍖?(Modularization)**: 鏄惁閬靛惊妯″潡鍖栬璁★紵鏄惁瀛樺湪纭紪鐮佹垨纭灇涓撅紵
- **绔晶鍒嗙 (C/S Separation)**: 鏈嶅姟绔槸鍚︿粎璐熻矗婕旂畻锛熷鎴风鏄惁浠呰礋璐ｆ覆鏌撲笌浜や簰锛?- **鍛藉悕瑙勮寖 (Naming)**: 鍙橀噺涓庢柟娉曟槸鍚﹀寘鍚嫭鍙疯鏄庯紵鏂囦欢澶存槸鍚﹀寘鍚爣鍑嗘敞閲婏紵
- **Mod 鏀寔 (Extensibility)**: 鏄惁棰勭暀浜嗘墿灞曟帴鍙ｆ垨 API锛?- **妯℃嫙椹卞姩 (Simulation)**: 閫昏緫鏄惁閬靛惊妯℃嫙鏃堕棿锛熸槸鍚﹂伩鍏嶄簡涓嶅繀瑕佺殑姣忓抚鏇存柊锛?
## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
鈹溾攢鈹€ plan.md              # This file (/speckit.plan command output)
鈹溾攢鈹€ research.md          # Phase 0 output (/speckit.plan command)
鈹溾攢鈹€ data-model.md        # Phase 1 output (/speckit.plan command)
鈹溾攢鈹€ quickstart.md        # Phase 1 output (/speckit.plan command)
鈹溾攢鈹€ contracts/           # Phase 1 output (/speckit.plan command)
鈹斺攢鈹€ tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
鈹溾攢鈹€ models/
鈹溾攢鈹€ services/
鈹溾攢鈹€ cli/
鈹斺攢鈹€ lib/

tests/
鈹溾攢鈹€ contract/
鈹溾攢鈹€ integration/
鈹斺攢鈹€ unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
鈹溾攢鈹€ src/
鈹?  鈹溾攢鈹€ models/
鈹?  鈹溾攢鈹€ services/
鈹?  鈹斺攢鈹€ api/
鈹斺攢鈹€ tests/

frontend/
鈹溾攢鈹€ src/
鈹?  鈹溾攢鈹€ components/
鈹?  鈹溾攢鈹€ pages/
鈹?  鈹斺攢鈹€ services/
鈹斺攢鈹€ tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
鈹斺攢鈹€ [same as backend above]

ios/ or android/
鈹斺攢鈹€ [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |

