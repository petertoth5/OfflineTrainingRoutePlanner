# Agent Documentation

Welcome to the OfflineTrainingRoutePlanner agent ecosystem documentation. This directory provides comprehensive guides for understanding and working with the specialized agents that power the project's development workflow.

## Overview

The OfflineTrainingRoutePlanner project uses a **7-agent + 5-skill ecosystem** to coordinate specialized work across algorithm design, UI/UX, code implementation, build processes, deployment, and documentation. Each agent is a Claude model variant optimized for its specific role.

### Agent Ecosystem at a Glance

| Agent | Model | Role | Trigger |
|-------|-------|------|---------|
| **Orchestrator** | Claude Opus 4.7 | Entry point & workflow coordinator | User starts conversation or reports issue |
| **Algorithm Developer** | Claude Sonnet 4.6 | Design & specify routing algorithms | Algorithm idea or route generation issue |
| **UI Designer** | Claude Sonnet 4.6 | Android UI/UX and layout specifications | Visual issue or new UI feature needed |
| **Software Developer** | Claude Opus 4.7 | Implement specs in Kotlin/Android | Algorithm/UI spec ready or bug needs fixing |
| **Build/Integrator** | Claude Haiku 4.5 | Build APKs, resolve compile errors | Implementation complete or build fails |
| **Deploy Agent** | Claude Haiku 4.5 | Install APKs to device, verify readiness | APK ready for testing or device validation |
| **Documentation Agent** | Claude Haiku 4.5 | Maintain AGENT_GUIDE.md and docs | Development iteration complete |

### Associated Skills

Five specialized skills support specific domains:

| Skill | Purpose |
|-------|---------|
| **Kotlin Development** | Kotlin code patterns, Android API usage, best practices |
| **Android Build** | Gradle setup, ProGuard config, dependency management |
| **Algorithm Design** | Algorithm specification, pseudocode, complexity analysis |
| **Android UI Design** | Layout patterns, accessibility, Material Design 2 |
| **Deployment Checklist** | APK signing, device testing procedures, release steps |

---

## When to Invoke the Orchestrator

The **Orchestrator Agent** is your entry point for all work. Invoke it when:

1. **You have a feature request** — "I want to add elevation weighting to the route algorithm"
2. **You report a bug** — "The app crashes when exporting a GPX file with 1000+ points"
3. **You request an enhancement** — "Can we support more regions?"
4. **You ask for status** — "What's the current state of the project?"
5. **You provide feedback on completed work** — "The new UI looks good, but I have a suggestion"

The Orchestrator will:
- Listen to your request
- Ask clarifying questions if needed
- Assess scope (algorithm? UI? code? build? documentation?)
- Route to the appropriate specialist agent(s)
- Manage the workflow and keep you informed
- Gather feedback and loop back to specialists if needed

### Example Conversation Flow

```
User: "I want to add a scenic route profile that avoids highways."
           ↓
Orchestrator: "That requires three areas: algorithm (profile weights), UI (profile selector), 
             and code integration. Let me route this to the Algorithm Developer first."
           ↓
[Algorithm Developer designs scenic profile weights and provides specification]
           ↓
Orchestrator: "Great! Now routing to UI Designer to add the profile selector."
           ↓
[UI Designer creates layout spec for profile toggle]
           ↓
Orchestrator: "Now routing to Software Developer to implement both pieces."
           ↓
[Software Developer integrates algorithm + UI, commits code]
           ↓
Orchestrator: "Ready to test on your device. Build/Integrator will create an APK..."
           ↓
[Specialist agents complete their work]
           ↓
User gets the feature and clear documentation of what was done.
```

---

## Directory Structure

```
docs/agents/
  ├── README.md                    # This file: overview & quick reference
  ├── orchestrator-workflow.md     # Detailed workflow diagrams and examples
  ├── agent-capabilities.md        # Detailed capability list for each agent
  └── [future: agent-integration-guide.md, troubleshooting.md, etc.]
```

---

## Agent Definitions

The agents are defined in `.claude/agents/`:

```
.claude/agents/
  ├── orchestrator-agent.md           # Master coordinator
  ├── algorithm-developer-agent.md    # Algorithm design specialist
  ├── ui-designer-agent.md            # UI/UX specialist
  ├── software-developer-agent.md     # Kotlin implementation specialist
  ├── build-integrator-agent.md       # Build system specialist
  ├── deploy-agent.md                 # Deployment specialist
  └── documentation-agent.md          # Documentation specialist
```

Each agent definition includes:
- **Role & Purpose** — what the agent does
- **Model & Rationale** — why that model was chosen
- **Responsibilities** — detailed scope and tasks
- **When Triggered** — conditions that invoke the agent
- **Handoff Process** — how work passes between agents
- **Success Criteria** — how to verify the agent succeeded

---

## Skills

Skills are specialized knowledge bases in `.claude/skills/`:

```
.claude/skills/
  ├── kotlin-development-skill.md     # Kotlin/Android patterns
  ├── android-build-skill.md          # Gradle, ProGuard, APK building
  ├── algorithm-design-skill.md       # Algorithm specification patterns
  ├── android-ui-design-skill.md      # UI/accessibility patterns
  └── deployment-checklist-skill.md   # Deployment verification steps
```

Skills are referenced by agents when needed. They provide domain expertise and common patterns.

---

## Knowledge Base: AGENT_GUIDE.md

The single source of truth for the entire project is **AGENT_GUIDE.md** in the repository root. It contains:

- **Tech stack** — languages, frameworks, versions
- **Core components** — key classes and their roles
- **Routing algorithm** — how routes are generated (pseudocode + details)
- **Common modifications** — patterns for typical changes
- **Known limitations** — current constraints and workarounds
- **Build/Release instructions** — step-by-step procedures
- **Mandatory rules for all agents** — non-negotiable constraints

All agents reference AGENT_GUIDE.md for context and constraints. It is maintained by the Documentation Agent after each development iteration.

---

## Workflow: The 7-Step Process

A typical task flows through the agent ecosystem like this:

```
┌─────────────────────────────────────────┐
│ USER: Request, Bug Report, or Feedback  │
└──────────────────┬──────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│ ORCHESTRATOR: Assess & Route            │
│ - Understand request                    │
│ - Determine scope                       │
│ - Route to specialist(s)                │
└──────────────────┬──────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│ SPECIALIST AGENTS (as needed):          │
│ 1. Algorithm Developer (if algorithm)   │
│ 2. UI Designer (if UI/layout)           │
│ 3. Software Developer (if code)         │
│ 4. Build/Integrator (if compile)       │
│ 5. Deploy Agent (if device test)       │
│ 6. Documentation Agent (if complete)   │
└──────────────────┬──────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│ ORCHESTRATOR: Gather Feedback           │
│ - Ask user to verify work               │
│ - Loop back if changes needed           │
│ - Finalize and document                 │
└──────────────────┬──────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│ USER: Satisfied / New Request           │
└─────────────────────────────────────────┘
```

**Not all steps are always needed.** A simple bug fix might be: Orchestrator → Software Dev → Documentation → done. A new feature might be: Orchestrator → Algorithm Dev → UI Designer → Software Dev → Build/Integrator → Deploy → Documentation → done.

---

## Key Principles

### 1. Specialization

Each agent has a focused domain:
- **Algorithm Developer** does not write Kotlin code
- **UI Designer** does not implement algorithms
- **Software Developer** does not make UI decisions
- **Build/Integrator** does not modify source code (unless fixing build errors)
- **Deploy Agent** does not build APKs

This specialization ensures quality and clear responsibility boundaries.

### 2. Orchestration

The **Orchestrator Agent** is the hub. All work flows through the Orchestrator:
- User talks to Orchestrator (not directly to specialists)
- Orchestrator routes to specialists
- Specialists report back to Orchestrator
- Orchestrator synthesizes for user

This ensures coherent workflows and prevents specialist agents from working at cross-purposes.

### 3. Documentation as First-Class

After each iteration, the **Documentation Agent** updates AGENT_GUIDE.md. This ensures:
- The guide stays current as code evolves
- Agents always have accurate reference material
- Future agents (and humans) understand the codebase

Documentation is not an afterthought; it's a mandatory workflow step.

### 4. Constraints as Boundaries

The project has hard constraints:
- **GraphHopper 6.0 only** (7+ incompatible with Android)
- **largeHeap=true required** (OSM import needs 300–400MB)
- **Portrait orientation locked** (no landscape)
- **Material Design 2 baseline** (no custom design language)

These constraints are non-negotiable and are enforced by all agents.

---

## Quick Reference

### For Users Requesting Work

**Tell the Orchestrator what you want:**
- What is the goal? (e.g., "Improve route variety", "Fix crash on export", "Add elevation data")
- What happens now vs. expected? (e.g., "All circular routes look the same" vs. "Each route should look different")
- What device/region are you testing on?

The Orchestrator will handle the rest.

### For Developers Looking to Extend

**Read these files in order:**
1. `AGENT_GUIDE.md` — understand the tech stack and current state
2. `docs/agents/README.md` — understand the agent ecosystem (this file)
3. `docs/agents/orchestrator-workflow.md` — see example workflows
4. `docs/agents/agent-capabilities.md` — understand what each agent can do
5. `.claude/agents/[agent-name]-agent.md` — read the specific agent(s) you'll interact with

### For Build/Deployment Teams

**Key files:**
- `AGENT_GUIDE.md` → "Build — Debug" and "Build — Release APK" sections
- `.claude/agents/build-integrator-agent.md` → full Gradle/build knowledge
- `.claude/agents/deploy-agent.md` → ADB and device testing procedures

---

## Troubleshooting

**"I have a request but don't know who to ask"**
→ Always start with the Orchestrator. It will route correctly.

**"I'm a specialist agent and don't know what to do"**
→ Read your agent definition in `.claude/agents/`. It describes your role and responsibilities.

**"The code doesn't match the documentation"**
→ The Documentation Agent should have kept them in sync. Read the code to determine current state, then report the discrepancy.

**"I need domain knowledge about Kotlin/algorithms/build"**
→ Check the relevant skill in `.claude/skills/`. Skills provide patterns and best practices.

**"A constraint seems outdated"**
→ Check AGENT_GUIDE.md's "Last Updated" date. If it's old, the Documentation Agent should update it. If you find an actual issue, inform the Orchestrator.

---

## Next Steps

- **Developers**: Read `docs/agents/orchestrator-workflow.md` to see real workflow examples
- **Project Leads**: Review `docs/agents/agent-capabilities.md` to understand each agent's scope
- **New Team Members**: Start with AGENT_GUIDE.md, then this README, then the workflow guide

---

## Document Navigation

| Document | Purpose | Audience |
|----------|---------|----------|
| **README.md** (this file) | Overview & quick reference | Everyone |
| **orchestrator-workflow.md** | Workflow diagrams & examples | Developers, Project Leads |
| **agent-capabilities.md** | Detailed capability list | Developers, System Designers |
| **AGENT_GUIDE.md** (project root) | Codebase truth source | All agents, developers |
| **.claude/agents/\*-agent.md** | Individual agent definition | Specific agent, Orchestrator |
| **.claude/skills/\*-skill.md** | Domain expertise & patterns | Agents, developers |

---

## Last Updated

2026-05-29

**Version**: 1.0 (Initial agent documentation)
