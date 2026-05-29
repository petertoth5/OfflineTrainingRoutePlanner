# Agent Capabilities Reference

This document provides a detailed capability matrix and summary for each agent in the OfflineTrainingRoutePlanner ecosystem.

---

## Quick Capability Matrix

| Capability | Orchestrator | Algorithm Dev | UI Designer | Software Dev | Build/Int. | Deploy | Documentation |
|------------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Assess scope | ✓ | — | — | — | — | — | — |
| Route work | ✓ | — | — | — | — | — | — |
| Design algorithms | — | ✓ | — | — | — | — | — |
| Write Kotlin code | — | — | — | ✓ | — | — | — |
| Design UI layouts | — | — | ✓ | — | — | — | — |
| Build APKs | — | — | — | — | ✓ | — | — |
| Install APKs | — | — | — | — | — | ✓ | — |
| Test on device | — | — | — | — | — | ✓ | — |
| Update docs | — | — | — | — | — | — | ✓ |
| Manage dependencies | — | — | — | — | ✓ | — | — |
| Ask clarifying questions | ✓ | — | — | — | — | — | — |
| Make decisions alone | ✓ | — | — | — | — | — | — |

---

## 1. Orchestrator Agent

**Model**: Claude Opus 4.7  
**Role**: Entry point, workflow coordinator, decision maker  
**Status**: Primary interface for all development work

### Capabilities

#### What It CAN Do

- **Listen & clarify** — Ask questions to understand user intent, detect ambiguity
- **Assess scope** — Determine whether request is algorithmic, UI, code, build, deploy, or documentation
- **Route to specialists** — Delegate to appropriate agent(s) based on scope
- **Manage workflows** — Track progress, sequence specialists, handle handoffs
- **Make routing decisions** — Decide if task is single-layer or multi-layer
- **Identify blockers** — Recognize when an issue requires team escalation
- **Synthesize for user** — Explain decisions clearly, keep user informed of status
- **Reference AGENT_GUIDE.md** — Use project knowledge base for context and constraints
- **Gather feedback** — Ask user to test work, determine if fix is satisfactory
- **Loop back** — Re-engage specialist if user reports issues or requests changes

#### What It CANNOT Do

- **Design algorithms** — That's Algorithm Developer's expertise
- **Write Kotlin code** — That's Software Developer's responsibility
- **Design UI layouts** — That's UI Designer's work
- **Build APKs** — That's Build/Integrator's job
- **Test on device** — That's Deploy Agent's responsibility
- **Update documentation files** — That's Documentation Agent's role
- **Implement decisions** — Must route to specialists

### When Triggered

1. User starts conversation (feature request, bug report, question)
2. User provides feedback on completed work
3. User asks for project status
4. Any request that doesn't fit a specialist's scope

### Handoff Process

**Receives from user**: Natural language request or feedback

**Provides to specialists**: Clear routing with context
```
"I'm routing this to [Agent Name] because:
- [Reason 1]
- [Reason 2]

They will:
1. [Step 1]
2. [Step 2]
3. [Return with output: ...]

I'll check back with you once they've made progress."
```

**Receives from specialists**: Completed work, status, or blockers

**Provides to user**: Clear status update, next steps, or request for feedback

### Integration Points

- Communicates with: Algorithm Dev, UI Designer, Software Dev, Build/Int., Deploy, Documentation
- References: AGENT_GUIDE.md
- Manages: Task sequencing, feedback loops, escalations
- No direct code or design involvement

### Success Criteria

1. User clearly understands what's being done and why
2. Work is routed to correct specialist on first attempt
3. Multi-layer tasks flow smoothly through workflow
4. Feedback loops are closed (bugs identified, fixed, tested)
5. Project state is always clear to user

---

## 2. Algorithm Developer Agent

**Model**: Claude Sonnet 4.6  
**Role**: Design and specify routing algorithms  
**Status**: Algorithm architect for waypoint strategies, scaling, optimization

### Capabilities

#### What It CAN Do

- **Design algorithms** — Specify waypoint generation, scaling strategies, anti-backtracking logic
- **Validate feasibility** — Ensure algorithms respect GraphHopper 6.0 and Android constraints
- **Analyze complexity** — Calculate time/space complexity, convergence guarantees
- **Handle edge cases** — Identify and specify behavior for boundary conditions
- **Produce pseudocode** — Write detailed, implementable specifications
- **Test logic** — Trace through pseudocode to verify correctness
- **Explain trade-offs** — Clarify why certain design choices are made
- **Work with constraints** — Respect GraphHopper 6.0 limitations, Android performance budgets

#### What It CANNOT Do

- **Write Kotlin code** — Only pseudocode; Software Dev implements
- **Design UI** — That's UI Designer's scope
- **Build APKs** — That's Build/Integrator's job
- **Test on device** — That's Deploy Agent's responsibility
- **Make implementation decisions** — Software Dev translates spec
- **Decide which algorithm to use** — That comes from Orchestrator based on user needs

### When Triggered

1. User reports algorithmic issue ("Circular routes all look the same")
2. User requests algorithmic enhancement ("Add elevation weighting")
3. Performance optimization needed ("Route generation too slow")
4. Constraint change affects algorithm ("Need to support GraphHopper 7" — likely routes back to Orchestrator)

### Algorithmic Scope

**Current algorithms**:
- Circular waypoint generation (3 points at 120° intervals)
- Detour waypoint generation (perpendicular offset midpoint)
- Route scaling via proportional iteration (up to 10 iterations)
- Anti-backtracking via heading penalties (300.0 penalty value)

**Design areas**:
- Waypoint placement strategies (number, position, bearing)
- Scaling convergence (speed, precision, fallback behavior)
- Bearing/heading calculations (accuracy, penalty tuning)
- Loop detection (definitions, performance)
- Edge case handling (NaN, impossible constraints, degenerate inputs)

### Handoff Process

**Receives from Orchestrator**: Problem description, scope, context

**Produces specification**: 
```
Algorithm: [Name]
- Triggered by: [what user request led here]
- Complexity: O(...) time/space
- Pseudocode: [detailed steps]
- Validation & edge cases: [normal/edge/failure cases]
- Handoff to Software Dev: [inputs, outputs, files to modify, testing approach]
```

**Provides to Software Developer**: Specification ready for implementation
- Pseudocode (not Kotlin)
- Input/output contracts
- Edge case handling
- Test scenarios

### Integration Points

- Works with: Software Developer (code implementation)
- References: AGENT_GUIDE.md (current algorithms, constraints, GraphHopper 6.0 limitations)
- No direct involvement with UI, Build, or Deploy

### Success Criteria

1. Specification is unambiguous (Software Dev can implement without questions)
2. Edge cases are identified (every unusual input has defined behavior)
3. Complexity is justified (performance realistic for Android)
4. Constraints satisfied (GraphHopper 6.0 compatible, respects memory limits)
5. Correctness is proven (pseudocode + analysis show solution works)
6. Validation is clear (specific test cases, not "test thoroughly")

---

## 3. UI Designer Agent

**Model**: Claude Sonnet 4.6  
**Role**: Android UI/UX design and layout specification  
**Status**: Specialist for all visual and interactive aspects

### Capabilities

#### What It CAN Do

- **Design layouts** — Create Android XML layout specifications (not code, but detailed specs)
- **Design visual hierarchy** — Organize controls by importance (map dominant, controls secondary)
- **Define color schemes** — Create high-contrast palettes meeting WCAG AA standard
- **Specify typography** — Type scale, sizes, weights, fonts
- **Create drawable descriptions** — SVG specs for icons, app icon layers, vector drawables
- **Plan interactions** — Button placement, toggle/seekbar behaviors, feedback mechanisms
- **Ensure accessibility** — Touch targets (48dp), contrast (4.5:1), semantic naming, TalkBack support
- **Optimize for devices** — Responsive design for phone (4-6") and tablet (7-12") sizes
- **Apply design principles** — Minimalism, high contrast, portrait-lock compliance, Material Design 2

#### What It CANNOT Do

- **Write implementation code** — Layout XML specs are provided; Software Dev implements
- **Modify algorithms** — That's Algorithm Developer's scope
- **Handle build system** — That's Build/Integrator's job
- **Test on device** — That's Deploy Agent's responsibility
- **Make business logic decisions** — Software Dev decides when buttons are enabled/disabled
- **Change app architecture** — That decision comes from Orchestrator + Software Dev

### When Triggered

1. User reports visual issue ("Buttons too small", "Colors hard to read")
2. New feature requires UI changes ("Add profile selector toggle")
3. Design polish requested ("Improve visual hierarchy")
4. Accessibility feedback ("TalkBack navigation confusing")
5. Device optimization needed ("Layout issues on tablets")

### Design Scope

**Current layouts**:
- `activity_main.xml` — Map (70-80% of screen) + control panel (20-30%)
- `activity_splash.xml` — Centered title, download button, progress bar

**Design areas**:
- Layout hierarchy (which controls are where, relative importance)
- Visual hierarchy (what user's eye sees first)
- Touch target sizing (minimum 48dp x 48dp for accessibility)
- Contrast validation (text on backgrounds, button states)
- Responsive scaling (layouts adapt to phone/tablet/orientation)
- Material Design 2 compliance (buttons, toggles, text inputs)
- Samsung Galaxy optimization (curved edges, OneUI assumptions)

### Handoff Process

**Receives from Orchestrator**: Feature request or visual issue

**Produces specification**:
```
Layout XML Specification
- ROOT structure
  - Child 1: MapView (layout, constraints, notes)
  - Child 2: Control panel (layout, contents)
    - Profile selector (button IDs, styling)
    - Distance input (text size, hint)
    - [etc.]

Color & Typography Palette
- Colors: primary background, text, accents, disabled, error
- Typography: headline, body, label, button sizes

Drawable Specifications
- App icon (background, foreground, monochrome layers)
- In-app drawables (refresh, settings, export icons)

Accessibility Checklist
- [ ] All text >= 12sp
- [ ] Contrast >= 4.5:1
- [ ] Touch targets >= 48dp
- [etc.]

Visual Notes
- Design intent
- Trade-offs
- Important constraints
```

**Provides to Software Developer**: Layout specs, not code
- Detailed XML structure with styling notes
- Color/type palette with exact values
- Drawable descriptions
- Accessibility requirements
- Visual rationale

### Integration Points

- Works with: Software Developer (code implementation)
- References: AGENT_GUIDE.md (current UI, device constraints, accessibility requirements)
- No direct involvement with Algorithm, Build, or Deploy

### Constraints

- **Portrait-locked** (no landscape)
- **Material Design 2 baseline** (not custom)
- **High contrast required** (4.5:1 minimum)
- **Accessible by default** (touch targets, semantic naming)
- **Responsive** (phone 4-6", tablet 7-12")
- **Samsung Galaxy focus** (curved edges, OneUI compatibility)

### Success Criteria

1. Layouts are clear and actionable (Software Dev can implement without ambiguity)
2. Visual hierarchy is evident (map dominant, controls secondary)
3. High contrast guaranteed (all text/buttons meet WCAG AA)
4. Minimalist goals met (no clutter, only essential controls)
5. Accessibility built-in (touch targets, descriptions, semantic structure)
6. Portrait orientation locked (no landscape variants)
7. Responsive design works (scales gracefully across sizes)
8. User can complete task (select points → generate → export with minimal steps)

---

## 4. Software Developer Agent

**Model**: Claude Opus 4.7  
**Role**: Implement algorithms, UI, features, and bug fixes in Kotlin  
**Status**: Primary hands-on implementer for all code-level work

### Capabilities

#### What It CAN Do

- **Implement algorithm specifications** — Convert pseudocode into efficient Kotlin functions
- **Build UI layouts and logic** — Translate XML specs into Android code + behavior
- **Develop features end-to-end** — Models, logic, UI, testing in coordinated implementation
- **Maintain code quality** — Write clean, documented Kotlin following Android best practices
- **Fix bugs** — Diagnose crashes, logic errors, regressions with minimal fixes
- **Manage critical constraints** — GraphHopper 6.0, largeHeap, profileFingerprint, mapManager API
- **Write tests** — Unit and integration tests per specification
- **Commit regularly** — Clear, atomic commits documenting progress
- **Handle edge cases** — Implement fallback behavior defined in specs
- **Debug complex issues** — Trace through GH routing, osmdroid map, coroutines, lifecycle

#### What It CANNOT Do

- **Design algorithms** — That's Algorithm Developer's scope (implements specs only)
- **Design UI** — That's UI Designer's scope (implements specs only)
- **Build APKs** — That's Build/Integrator's job
- **Test on device** — That's Deploy Agent's responsibility
- **Make architectural decisions** — Orchestrator + team make high-level decisions
- **Upgrade dependencies** — Build/Integrator approves dependency changes

### When Triggered

1. Algorithm specification is complete and ready to implement
2. UI specification is complete and ready to implement
3. Bug report with reproduction steps
4. Feature development spanning multiple components
5. Code refactoring needed (tech debt, clarity)

### Implementation Scope

**Core components**:
- `RouteService.kt` — GraphHopper routing, waypoint generation, scaling
- `MainActivity.kt` — UI controls, user interaction, map display
- `MapManager.kt` — osmdroid wrapper, markers, polylines
- `DataManager.kt` — OSM data, SharedPreferences
- `Route.kt` — Route data class and GPX export

**Key constraints**:
- **GraphHopper 6.0 only** (7+ incompatible with Android)
- **largeHeap=true required** (OSM import needs 300–400MB)
- **profileFingerprint bumped on profile changes** (cache invalidation)
- **mapManager API safety** (never call `clear()` after route display; use `clearRoute()`)
- **Kotlin idioms** (sequences, scope functions, data classes, sealed classes)
- **Coroutine safety** (proper scope, no blocking on Main thread)
- **AGENT_GUIDE.md updated after changes** (source of truth stays current)

### Handoff Process

**Receives from specialists**:
- Algorithm spec (pseudocode, test cases, edge cases)
- UI spec (layout XML structure, styling, accessibility)
- Bug report (reproduction steps, expected vs. actual)

**Produces**: 
```
Implementation Complete: [Feature/Fix Name]

What was implemented:
- [File 1]: [changes]
- [File 2]: [changes]

Tests added:
- [test name]: [what it validates]

Commits created:
1. [commit message]
2. [commit message]

Status: Ready for testing on device

Notes:
- [Any caveats, workarounds, follow-ups]
```

**Provides to Build/Integrator**: Code ready for compilation
- Kotlin source files compiled without errors
- No lint warnings
- Tests passing
- AGENT_GUIDE.md updated if needed

### Integration Points

- Works with: Algorithm Dev (specs), UI Designer (specs), Build/Int. (build APKs)
- References: AGENT_GUIDE.md (current code state, constraints)
- Receives code review feedback from Orchestrator or peers if applicable

### Critical Safety Rules

1. **Never upgrade GraphHopper past 6.0** — Breaking change for Android
2. **Never call `mapManager.clear()` after route display** — Breaks map interactivity
3. **Always use `mapManager.clearRoute()`** to remove only route polylines
4. **Bump `profileFingerprint` when profiles change** — Triggers cache invalidation on next launch
5. **Always update AGENT_GUIDE.md** after implementation
6. **Test and commit frequently** — Logical, reviewable commits

### Success Criteria

1. Implementation matches spec (does what specs say, not developer interpretation)
2. Tests pass (unit, integration, edge cases)
3. No regressions (existing functionality unaffected)
4. Code is clean (readable, documented, no warnings)
5. Constraints met (GraphHopper 6.0, largeHeap, fingerprint, mapManager safety)
6. Commits clear (descriptive messages, logical scope)
7. Status reported (clear handoff with testing results and blockers)

---

## 5. Build/Integrator Agent

**Model**: Claude Haiku 4.5  
**Role**: Build APKs, resolve compile errors, manage dependencies  
**Status**: Mechanical build executor and integration specialist

### Capabilities

#### What It CAN Do

- **Build debug APKs** — Compile via Gradle, diagnose compile errors, output to build/
- **Build signed release APKs** — Handle keystore setup, ProGuard, signing
- **Manage dependencies** — Enforce GraphHopper 6.0 constraint, resolve conflicts
- **Resolve build errors** — Diagnose Gradle, ProGuard, resource, manifest issues
- **Handle Windows/PowerShell issues** — Know the CLASSPATH bug; use Java wrapper
- **Apply packaging rules** — META-INF exclusions, ProGuard keep rules
- **Validate APK quality** — Confirm build succeeds, no errors, APK ready
- **Report clear status** — "Build successful; APK ready" or detailed error diagnosis

#### What It CANNOT Do

- **Modify source code** — Except to fix build errors (rare)
- **Upgrade dependencies speculatively** — Tech stack is locked
- **Make architectural decisions** — That's Orchestrator + team
- **Test on device** — That's Deploy Agent's job
- **Design systems** — Build system is already defined; agent executes it

### When Triggered

1. Code implementation complete, ready for APK build
2. Build fails with error message
3. Dependency conflict detected
4. Ready for release APK (signed, minified)
5. ProGuard misconfiguration suspected

### Build Scope

**Debug build**:
- Task: `assembleDebug`
- Output: `build/outputs/apk/debug/routeplanner-debug.apk`
- No ProGuard, no signing, fast build, easy debugging

**Release build**:
- Task: `assembleRelease`
- Output: `build/outputs/apk/release/RoutePlanner-release.apk`
- Signed with keystore, ProGuard ready (currently disabled), distribution-ready

### Handoff Process

**Receives from Software Developer**: Code ready for build

**Produces**:
```
Build SUCCESSFUL / FAILED

[If successful]
Debug APK: build/outputs/apk/debug/routeplanner-debug.apk (87MB)
Time: 45 seconds
APK ready for Deploy Agent testing.

[If failed]
Error: [error type]
Cause: [root cause]
Solution: [steps to fix]
```

**Provides to Deploy Agent**: APK file path + readiness confirmation

### Integration Points

- Works with: Software Developer (code), Deploy Agent (APK distribution)
- References: AGENT_GUIDE.md (build procedures, dependencies, constraints)
- No direct involvement with Algorithm, UI, or code logic

### Constraints

- **GraphHopper 6.0 only** (never upgrade; 7+ need Janino, incompatible with Android)
- **No dependency upgrades without approval** (tech stack locked)
- **Windows/PowerShell always** (must support Java wrapper Gradle invocation)
- **ProGuard rules frozen** (minification disabled by default; rules in place for future)
- **All builds must succeed** (zero warnings tolerated in critical paths)

### Success Criteria

1. Build completes without errors (exit code 0)
2. APK generated at expected path (file size reasonable)
3. Errors are clear (if build fails, root cause + solution documented)
4. Version constraints respected (GraphHopper 6.0, no unauthorized upgrades)
5. Windows/PowerShell support (Java wrapper works reliably)
6. APK ready for use (debug installable, release signed + verified)
7. Status reported (clear "success" or "failure" message)

---

## 6. Deploy Agent

**Model**: Claude Haiku 4.5  
**Role**: Mechanical APK deployment executor and device tester  
**Status**: Reliable deployment to physical devices or emulators

### Capabilities

#### What It CAN Do

- **Detect ADB and devices** — Check `adb devices`, list connected phones/emulators
- **Install APKs** — Use `adb install -r` to deploy to target device
- **Validate APK** — Confirm file exists, size reasonable, not corrupted
- **Handle failures** — Identify installation errors, provide troubleshooting
- **Report status clearly** — "Installing...", "Success", or "Device not found"
- **Guide user setup** — Provide clear instructions if device isn't configured
- **Manage multi-device scenarios** — Ask which device if multiple connected
- **Handle device-specific issues** — Samsung, Pixel, emulator quirks documented

#### What It CANNOT Do

- **Build APKs** — That's Build/Integrator's job
- **Test functionality** — Installation only; manual testing by user
- **Configure ADB** — Assumes user has Android SDK tools installed
- **Launch emulators** — Assumes emulator is already running
- **Modify APK** — Never modifies the APK file

### When Triggered

1. APK is ready for testing on device
2. User requests device installation
3. Iteration loop: code changed, new APK ready for quick testing

### Deployment Scope

**Physical devices**:
- Android 7.0+ (API 24+)
- Requires: Developer Mode, USB Debugging enabled, USB cable
- ADB command: `adb install -r [APK_PATH]`

**Emulators**:
- Android Virtual Device (AVD)
- Requires: AVD running, fully booted
- ADB command: `adb install -r [APK_PATH]`

### Handoff Process

**Receives from Build/Integrator**: APK path + readiness confirmation

**Produces**:
```
Installation Status:

Result: Success / Failed / Device not found

[If success]
Device: emulator-5554 (Android 12, API 30)
APK: build/outputs/apk/debug/RoutePlanner-debug.apk (87MB)
Time: 8 seconds
App is now installed and ready to launch.

[If failed]
Error: [error code + message]
Troubleshooting: [steps to resolve]
```

**Provides to user**: Ready to test on device

### Integration Points

- Works with: Build/Integrator (APK delivery), User (feedback)
- References: AGENT_GUIDE.md (device setup, ADB commands)
- No direct involvement with Algorithm, UI, code logic, or Build decisions

### Constraints

- **APK must exist** (file path must be valid)
- **ADB must be available** (user must have Android SDK tools)
- **Device must be online** (adb devices shows "device" state)
- **Device must be unlocked** (some devices require screen unlock during install)
- **Developer Mode enabled** (physical devices require USB Debugging)
- **Signing must match** (cannot mix debug/release on same device without uninstall)

### Success Criteria

1. Device detected (ADB available, device online)
2. APK validated (file exists, reasonable size)
3. Installation succeeds (adb reports "Success")
4. Status reported (clear success/failure + next steps)
5. Device ready for testing (app icon visible, ready to launch)

---

## 7. Documentation Agent

**Model**: Claude Haiku 4.5  
**Role**: Maintain AGENT_GUIDE.md and documentation accuracy  
**Status**: Keeper of project knowledge and documentation truth

### Capabilities

#### What It CAN Do

- **Update AGENT_GUIDE.md** — The single source of truth for the codebase
- **Update README.md** — Project overview, setup, features, architecture
- **Track recent changes** — Log iterations: date, category, change, impact
- **Ensure accuracy** — Verify docs match actual code state
- **Update algorithms** — Pseudocode, complexity, constraints if algorithms change
- **Update components** — Core component descriptions if behavior changes
- **Update common patterns** — Add/modify "Common Modifications" section
- **Update limitations** — Remove fixed issues, add new constraints
- **Update dependencies** — Version numbers, transitive deps, conflicts
- **Maintain timestamps** — "Last Updated" dates, version numbers

#### What It CANNOT Do

- **Touch agent definitions** — `.claude/agents/*-agent.md` are sacred
- **Touch skill definitions** — `.claude/skills/*-skill.md` are sacred
- **Touch implementation plan files** — `.claude/implementation-plan*.md` immutable
- **Create new documentation files** — Only update existing .md files (except changelog archive for old entries)
- **Modify source code** — Never touch .kt, build.gradle, AndroidManifest.xml
- **Make design decisions** — Document decisions made by others, don't invent new ones

### When Triggered

1. After specialist agents complete significant work
2. After Deploy Agent confirms APK works on device
3. After any code change (algorithm, feature, bug fix)
4. After dependency or constraint change
5. Before releases (ensure docs match shipped code)

### Documentation Scope

**Primary file**: `AGENT_GUIDE.md` in repository root
- Tech stack (languages, versions)
- Core components (classes, roles)
- Routing algorithm (pseudocode, details)
- Common modifications (patterns for typical changes)
- Known limitations (current constraints, workarounds)
- Build/Release instructions (step-by-step)
- Mandatory rules (non-negotiable constraints)
- Recent Changes (changelog)

**Secondary file**: `README.md`
- Project overview
- Features summary
- Setup instructions
- Architecture overview
- Limitations (user-facing)

**Avoid**: Agent definitions, skill definitions, plan files, inline code comments

### Handoff Process

**Receives from Orchestrator**: Summary of changes made
```
"The Software Developer has completed randomized bearing offsets for 
circular routes. Changes in RouteService.generateCircularWaypoints():
- 3 waypoints at 120° ± random offset (±30° variance)
- Improves visual variety while maintaining distance accuracy
- No performance impact

Please update AGENT_GUIDE.md and provide summary when done."
```

**Produces**:
```
Documentation Updated: [Topic]

Changes made:
- Routing Algorithm section: pseudocode updated for ±30° offsets
- Known Limitations: removed "circular routes always use equilateral" row
- Recent Changes: added entry for randomized offset feature
- Dependencies: [if any dependency changes]
- Last Updated: 2026-05-28

AGENT_GUIDE.md is now current with implementation.
```

**Provides to Orchestrator**: Confirmation that docs are updated

### Integration Points

- Works with: All agents (receives summaries of their work)
- References: AGENT_GUIDE.md (primary target), code (verification)
- No involvement with code, algorithm design, UI design, building, or deployment

### Content Standards

- **Technical but accessible** — assume reader is developer; explain non-obvious concepts
- **Precise** — use exact terms (haversine, bearing, waypoint, tolerance)
- **Active voice** — "algorithm generates waypoints" not "waypoints are generated"
- **Current tense** — "app uses GraphHopper 6.0" not "app used"
- **Concise** — no unnecessary words
- **Copy-paste ready** — modification patterns are actionable
- **Current timestamps** — dates and versions are fresh
- **Accurate** — docs match actual code state

### Success Criteria

1. AGENT_GUIDE.md reflects current code state (someone reading it succeeds)
2. Algorithm descriptions are accurate (pseudocode traces correctly)
3. Recent Changes are logged (each iteration recorded with date, category, impact)
4. No scope creep (only existing .md files touched; agent/skill/plan files untouched)
5. Clear language (new developer understands how to modify app)
6. Timestamps current ("Last Updated" matches recent changes)
7. Mandatory rules preserved (constraints section always present)
8. No circular references (guide doesn't reference agent definitions)

---

## Capability Summary Table

### By Capability Type

| Category | Orchestrator | Algorithm | UI | Software | Build | Deploy | Documentation |
|----------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Coordination & Routing** | ✓ | — | — | — | — | — | — |
| **Algorithm Design** | — | ✓ | — | — | — | — | — |
| **UI/UX Design** | — | — | ✓ | — | — | — | — |
| **Code Implementation** | — | — | — | ✓ | — | — | — |
| **Build & Compile** | — | — | — | — | ✓ | — | — |
| **Device Deployment** | — | — | — | — | — | ✓ | — |
| **Documentation** | — | — | — | — | — | — | ✓ |
| **Decision Making** | ✓ | Spec only | Spec only | Implements | No | No | No |
| **Testing** | — | Logical proof | Spec review | Unit/integration | Build verify | Device | Accuracy verify |

### By Model (and why)

| Model | Agents | Why This Model |
|-------|--------|---|
| **Opus 4.7** | Orchestrator, Software Dev | Complex coordination, deep reasoning, large context window |
| **Sonnet 4.6** | Algorithm Dev, UI Designer | Strong algorithmic/visual reasoning, balance of depth and speed |
| **Haiku 4.5** | Build/Integrator, Deploy, Documentation | Mechanical/deterministic tasks, speed, efficiency, pattern matching |

---

## Last Updated

2026-05-29

**Version**: 1.0 (Initial agent capabilities documentation)
