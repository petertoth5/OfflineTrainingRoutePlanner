# Orchestrator Workflow Guide

This document provides detailed workflow diagrams and real-world examples of how the Orchestrator Agent coordinates work across the specialist team.

## Core Workflow Diagram

The fundamental workflow pattern is:

```
┌────────────────────────────────────────────────────────────┐
│ 1. USER INPUT                                              │
│    - Feature request: "Add elevation support"              │
│    - Bug report: "App crashes when exporting GPX"          │
│    - Feedback: "The route looks weird"                     │
│    - Status: "What's done?"                                │
└────────────────────┬─────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 2. ORCHESTRATOR: LISTEN & CLARIFY                          │
│    - What exactly is the problem/goal?                     │
│    - When did it start (if bug)?                           │
│    - What device/region/profile?                           │
│    → May ask clarifying questions before proceeding        │
└────────────────────┬─────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 3. ORCHESTRATOR: ASSESS SCOPE                              │
│    Is this:                                                │
│    ✓ Algorithmic (route generation, waypoints)             │
│    ✓ UI/Visual (buttons, layout, colors)                   │
│    ✓ Code/Bug (crash, null pointer, logic)                 │
│    ✓ Build/Deploy (APK, signing, device)                   │
│    ✓ Documentation (guide update, comments)                │
│    → Multi-layered? Sequence is important!                 │
└────────────────────┬─────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 4. ORCHESTRATOR: ROUTE TO SPECIALIST(S)                    │
│    → Single agent if focused scope                         │
│    → Multiple agents in sequence if multi-layer            │
│    → Parallel where dependencies allow                     │
└────────────────────┬─────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 5. SPECIALIST AGENTS: EXECUTE                              │
│    Each agent:                                             │
│    - Receives context from Orchestrator                    │
│    - Executes in its domain (design, code, build, etc.)    │
│    - Reports completion + output to Orchestrator           │
│    - Takes no action outside its scope                     │
└────────────────────┬─────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 6. ORCHESTRATOR: GATHER FEEDBACK                           │
│    - Ask user to test/verify work                          │
│    - If issues: loop back to specialist or route to new    │
│    - If satisfied: move to documentation                   │
└────────────────────┬─────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 7. DOCUMENTATION: UPDATE GUIDES                            │
│    - AGENT_GUIDE.md updated with changes                   │
│    - Recent changes logged                                 │
│    - Ready for next iteration                              │
└────────────────────┬─────────────────────────────────────┘
                     ↓
┌────────────────────────────────────────────────────────────┐
│ 8. CLOSURE                                                 │
│    - User informed of completion                           │
│    - Ready for next request                                │
└────────────────────────────────────────────────────────────┘
```

---

## Example Workflows

### Workflow 1: Simple Bug Fix

**User Request**: "The app crashes when I try to export a GPX file with a really long route (5000+ points)"

```
Step 1: Orchestrator listens
  - When did you notice? (after recent changes?)
  - Which profile? (Running or Biking?)
  - Which device/Android version?

Step 2: Orchestrator assesses
  - This is a CODE/BUG issue
  - Likely: large string concatenation in Route.toGpx() exceeds heap
  - Single-layer: needs Software Developer only

Step 3: Orchestrator routes
  "I'm routing this to Software Developer because:
   - This is a code-level crash in the export flow
   - Root cause is likely memory/string handling in Route.toGpx()
   They will:
   1. Reproduce the crash with a large route
   2. Trace through Route.toGpx() to find the issue
   3. Implement a fix (streaming output instead of string concat)
   4. Test the fix
   5. Report back"

Step 4: Software Developer executes
  - Reproduces: creates a 5000-point route, exports → crash
  - Debugs: finds Route.toGpx() building one huge string
  - Fixes: uses streaming/StringBuilder + chunked writes instead
  - Tests: exports 5000-point route successfully
  - Commits: clear commit message with root cause

Step 5: Orchestrator gathers feedback
  "The Software Developer has fixed the crash by switching to 
   streaming output. Let's test it on your device:
   - Generate a large route (5000+ points)
   - Export as GPX
   - Let me know if it succeeds or if there are still issues"

Step 6: User tests
  "Tested on my device. Export works now, even for 10k-point routes!"

Step 7: Documentation Agent updates AGENT_GUIDE.md
  - Known Limitations table: remove "Large GPX exports crash"
  - Core Components → Route: note streaming output implementation
  - Recent Changes: "2026-05-28 | Bug Fix | Streaming GPX export for large routes"

Step 8: Closure
  "Crash fixed and documented. Ready for next work."
```

---

### Workflow 2: Multi-Layer Feature

**User Request**: "Can we add a 'scenic' route profile that prefers quiet roads over highways?"

```
Step 1: Orchestrator listens
  - What's the difference from Running/Biking? (weighting, UI representation?)
  - Should it be selectable in the UI? (yes)
  - Should it remember the choice? (yes, like Running/Biking)

Step 2: Orchestrator assesses
  - ALGORITHM: design the scenic profile weights
  - UI: add a third profile selector button/option
  - CODE: integrate profile into RouteService, profile selection logic
  - This is MULTI-LAYER → sequence matters

Step 3: Orchestrator routes (in sequence)
  "This requires three steps:
  
  STEP 1 (Algorithm Developer):
  - Design scenic profile weights: favor quiet roads, lower speed limits
  - Output: profile specification + weight/penality adjustments
  
  STEP 2 (UI Designer):
  - Add scenic to profile toggle (3-way toggle or radio buttons)
  - Output: layout specifications + accessibility notes
  
  STEP 3 (Software Developer):
  - Integrate profile into RouteService
  - Update profile selection in MainActivity
  - Output: code implementation + tests
  
  STEP 4 (Build/Integrator):
  - Build debug APK
  - Output: APK ready for testing
  
  STEP 5 (Deploy Agent):
  - Install to your device
  - Output: ready for manual testing

  Let me start with Algorithm Developer..."

Step 4a: Algorithm Developer
  - Design scenic profile: prefer quieter roads (tag penalties)
  - Output pseudocode for profile selection + weighting
  - Hands off to Orchestrator

Step 4b: Orchestrator → UI Designer
  - Receive algorithm spec
  - Route to UI Designer with profile name/purpose

Step 4b: UI Designer
  - Design 3-way profile selector (Running/Biking/Scenic)
  - Output layout spec + accessibility notes
  - Hands off to Orchestrator

Step 4c: Orchestrator → Software Developer
  - Receive algorithm spec + UI spec
  - Route to Software Developer

Step 4c: Software Developer
  - Integrate profile into RouteService.initializeGraphHopperSync()
  - Add UI toggle for profile selection in MainActivity
  - Update SharedPreferences to store profile choice
  - Test end-to-end
  - Commits with clear messages
  - Hands off to Orchestrator

Step 4d: Orchestrator → Build/Integrator
  - Route to Build/Integrator

Step 4d: Build/Integrator
  - Build debug APK: gradlew assembleDebug
  - Outputs: APK ready at build/outputs/apk/debug/

Step 4e: Orchestrator → Deploy Agent
  - Route to Deploy Agent

Step 4e: Deploy Agent
  - Install APK to device
  - Outputs: "Installation successful"

Step 5: Orchestrator gathers feedback
  "The scenic profile is now live on your device:
   - Profile selector has 3 buttons: Running, Biking, Scenic
   - Scenic routes will prefer quiet roads
   
   Please test:
   1. Select Scenic profile
   2. Generate a route in an area you know
   3. Does the route avoid highways? (compare to Biking route)
   4. Does it feel more natural/quiet?
   
   Let me know if it works as expected or needs adjustments"

Step 6: User tests
  "Tested on my device. The scenic routes do look quieter. 
   But I notice it's choosing very small roads sometimes—maybe 
   the penalty is too aggressive?"

Step 7: Orchestrator assesses feedback
  "That's good feedback. The scenic profile may be too strict on 
   small roads. Let me route back to Algorithm Developer to 
   adjust the weights..."
  [Loop back: Algorithm Dev → UI Designer (if needed) → Software Dev → 
   Build → Deploy → test again]

[OR, if user says "Perfect!"]

Step 7: Orchestrator → Documentation Agent
  - Route to Documentation Agent

Step 7: Documentation Agent
  - Update AGENT_GUIDE.md
  - Core Components → RouteService: note scenic profile added
  - Common Modifications: add "Add a new profile" pattern with scenic example
  - Dependencies: update profileFingerprint if profiles changed
  - Recent Changes: add entry
  - Hands off to Orchestrator

Step 8: Closure
  "Scenic profile implemented, tested, and documented. Ready 
   for the next iteration. The feature is live in the test APK."
```

---

### Workflow 3: Unclear/Multi-Part Bug Report

**User Request**: "The app feels slow when I have a lot of routes loaded. I think it might be crashing too, but I'm not sure."

```
Step 1: Orchestrator listens & clarifies
  "Let me clarify a couple things:
  
  1. When you say 'slow', what do you mean?
     - Does it take a long time to generate routes (seconds waiting)?
     - Does the map pan/zoom feel laggy?
     - Does exporting take forever?
  
  2. For the crash: when exactly does it happen?
     - Is there an error message on screen?
     - Does the app just close?
     - Can you reproduce it with specific steps?
  
  3. How many routes? (dozens, hundreds?)
  
  4. Which device are you on?"

Step 2: User clarifies
  "It takes about 10-15 seconds to generate a route. And yes, 
   the app closed once when I tried to export. I have maybe 
   20 routes saved. Samsung Galaxy S21."

Step 3: Orchestrator assesses
  - ALGORITHM: Route generation taking 10-15s is slow (should be <5s)
    → Possible: scaling loop running too many iterations
  - CODE/BUG: App crashed on export
    → Same root cause as Workflow 1 (memory), or something else
  - This is MULTI-PART:
    → Route performance issue (Algorithm Dev + Software Dev)
    → Export crash (Software Dev + possibly Build if heap settings)

Step 4: Orchestrator routes (parallel assessment)
  "I see two potential issues here:
  
  FIRST: Route generation is too slow (10-15s vs. expected <5s)
  - This might be algorithmic (too many scaling iterations)
  - Or it might be code efficiency (suboptimal loop logic)
  
  SECOND: Export crash (possible memory issue with 20 routes)
  - Same as the GPX streaming issue, or different
  
  Let me start by routing to Algorithm Developer to check if 
  the scaling logic is running too many iterations..."

[Continue as multi-part workflow, with clear handoffs between 
 specialists to diagnose and fix both issues]

Step 5+: Execution, feedback, documentation
  (Follows same pattern as Workflow 2)
```

---

## Orchestrator Decision Tree

The Orchestrator uses this mental model when receiving a request:

```
Is this a QUESTION about how the app works?
  ├─ YES: Answer from AGENT_GUIDE.md, don't route
  └─ NO: Proceed

Is this requesting changes (feature, bug fix, enhancement)?
  ├─ YES: Proceed
  └─ NO: Answer from documentation, don't route

Is the request CLEAR and DETAILED?
  ├─ YES: Proceed to assessment
  └─ NO: Ask clarifying questions first

Assessment: WHAT NEEDS TO CHANGE?
  ├─ Algorithm (route generation, waypoints)? → Algorithm Developer
  ├─ UI (buttons, layout, colors)? → UI Designer
  ├─ Code (implementation, bug fix)? → Software Developer
  ├─ Build (APK, compile error)? → Build/Integrator
  ├─ Device (testing, installation)? → Deploy Agent
  └─ Docs (guide, comments)? → Documentation Agent

Are MULTIPLE areas affected?
  ├─ YES: Sequence the agents (Algorithm → UI → Code → Build → Deploy → Docs)
  └─ NO: Route to single specialist

Can any agents work IN PARALLEL?
  ├─ YES: Route in parallel (e.g., Algorithm & UI both start, Code waits for specs)
  └─ NO: Sequence strictly

Route to specialist(s) with clear context, wait for completion, gather user feedback.
```

---

## Feedback Loop: How Issues Are Fixed

When a user reports an issue with completed work:

```
User: "The scenic routes are avoiding small roads too aggressively."

Orchestrator assessment:
  - Is this a misunderstanding of expected behavior? (clarify)
    → No, user understands what scenic should do
  - Is this a regression from recent work? (check timing)
    → Yes, scenic profile was just added
  - How urgent? (does it block the user?)
    → Not urgent, feature still works but sub-optimal
  - How isolated? (which agent to fix?)
    → Algorithm Developer (adjust weights) + possibly Software Dev (integrate)
  - Can we skip work? (is it minor?)
    → No, needs investigation and possible reweight

Orchestrator decision:
  "The scenic profile weights are too aggressive on small roads. 
   I'm routing back to Algorithm Developer to adjust the penalties. 
   They'll provide new weights, Software Developer will integrate, 
   and we'll test again on your device."

[Loop: Algorithm Dev → Software Dev → Build → Deploy → Test]

User tests again: "Much better! Small roads are included now."

Orchestrator: "Excellent. Documentation Agent will update the 
guides, and we're done with this iteration."

[Final step: Documentation Agent updates AGENT_GUIDE.md]
```

---

## Parallel Execution: When Agents Can Work Together

Some tasks can happen in parallel if they're independent:

```
Task: Add region filtering (avoid parks/forests in routes)

Algorithm layer:
  - Algorithm Dev designs penalty logic for forest tags
  - Can work in PARALLEL with UI Designer

UI layer:
  - UI Designer adds region/terrain filter toggle
  - Can work in PARALLEL with Algorithm Dev
  - But must WAIT for Algorithm spec if algorithm affects what filters are available

Code integration:
  - Software Dev integrates BOTH specs
  - Must WAIT for both Algorithm & UI to finish specs

Build/Deploy:
  - Build/Integrator builds APK after Software Dev finishes
  - Deploy installs after Build finishes

Timeline (with parallelism):
  Time 0-2 min:   Orchestrator assesses, starts Algorithm & UI in parallel
  Time 2-8 min:   Algorithm Dev + UI Designer work simultaneously
  Time 8-20 min:  Software Dev integrates both specs (sequential step, can't parallelize)
  Time 20-25 min: Build/Integrator builds APK
  Time 25-30 min: Deploy installs to device
  
  Total: ~30 minutes instead of 35+ if all serial

But NOT all tasks can be parallel:
  - Code always depends on Algorithm & UI specs
  - Build depends on Code
  - Deploy depends on Build
  - Docs depend on everything else
```

---

## Error Handling: When Something Goes Wrong

```
Specialist reports blocker:
  "I can't build the APK—ProGuard is stripping GraphHopper classes"

Orchestrator assessment:
  - Is this a new issue or known limitation? (check AGENT_GUIDE.md)
  - What's the immediate impact? (blocks testing? blocks release?)
  - Can the blocker be unblocked? (yes, fix ProGuard rules)

Orchestrator action:
  "Build/Integrator, I'm giving you this ProGuard blocker. 
   Can you diagnose and fix the stripping issue?"

Build/Integrator resolves:
  - Adds missing -keep rule for GraphHopper
  - Rebuilds APK
  - "Fixed. APK now builds successfully."

Orchestrator continues:
  - Routes to Deploy Agent for testing
  - [Normal workflow resumes]

If blocker can't be unblocked:
  "Unfortunately, the ProGuard rules conflict with the current 
   GraphHopper integration. This may require architecture changes. 
   Let me escalate to the team for discussion."

[Escalation: Orchestrator may need human input on architectural decisions]
```

---

## Status Tracking: Keeping User Informed

The Orchestrator maintains awareness of:

```
What's been done?
  ✓ Algorithm design complete
  ✓ UI spec complete
  → Code integration in progress
  → Build pending (waiting for code)
  → Deploy pending (waiting for build)
  → Docs pending (waiting for everything)

What's in progress?
  Software Developer: integrating algorithm + UI specs
  Time estimate: 15 minutes remaining

What's pending?
  - Build (waiting for code)
  - Deploy (waiting for build)
  - Docs (waiting for deploy + testing)

What feedback is outstanding?
  User needs to: test on device and confirm scenic routes avoid highways

Communication to user:
  "Algorithm and UI specs are done. Software Developer is now 
   integrating the code (~15 min remaining). After that, we'll 
   build an APK and get it on your device for testing. ETA: 30 
   minutes total. I'll follow up once it's ready to test."
```

---

## Escalation: When to Involve Humans

Orchestrator escalates (routes to human team) when:

```
Request is outside agent scope:
  "I want to integrate with Google Maps API"
  → Requires architectural decision and dependency review
  → Escalate to team for feasibility assessment

Constraint conflict:
  "Can we upgrade to GraphHopper 7?"
  → Build/Integrator reports: 7+ incompatible with Android/ART
  → Escalate to team: is alternative routing engine acceptable?

Blocker can't be unblocked:
  "ProGuard rules cannot satisfy both GraphHopper and APK size limits"
  → Team needs to decide: minify something else? Accept larger APK?

Ambiguous requirement:
  "Make the app faster"
  → Too vague. Humans need to clarify:
     - Faster route generation? Faster UI? Faster export?
     - How much faster (10% vs 50%)?
     - Which devices are priority?

Timeline/resource constraints:
  "We need this done in 2 hours"
  → Orchestrator assesses feasibility
  → If impossible, escalate to team for discussion
```

---

## Last Updated

2026-05-29

**Version**: 1.0 (Initial orchestrator workflow documentation)
