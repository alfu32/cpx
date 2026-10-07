# Goal: Implement the C+ Language Toolchain from the Language and Technical Specifications

Implement the C+ compiler, transcoder, compile-time system, C backend, and language tooling in Kotlin/JVM according to the provided:

- **C+ Language Specification**
- **C+ Technical Architecture**

The implementation must be driven by a **hierarchical, traceable, continuously updated execution plan**.

Do not treat the specifications as informal guidance. They are the authoritative requirements.

The work must be decomposed using **divide et impera** until every terminal task is small enough to be independently understood, implemented, tested, and completed.

---

# 1. Planning objective

Before substantial implementation, construct a complete work decomposition.

The hierarchy must have several levels:

```text
PROJECT
  ├── major subsystem
  │     ├── component
  │     │     ├── feature
  │     │     │     ├── implementation task
  │     │     │     └── test task
  │     │     └── ...
  │     └── ...
  └── ...
```

Continue decomposition recursively until every terminal task is:

- independently implementable;
- independently verifiable;
- small enough to complete without requiring another planning exercise;
- associated with explicit acceptance criteria;
- traceable to the specifications.

Do not stop decomposition merely because a task has a descriptive name.

For example:

```text
Implement CPX engine
```

is not a leaf task.

Neither is:

```text
Implement CPX evaluator
```

A valid decomposition would continue toward tasks such as:

```text
Implement CtType compile-time value representation
Implement CPX identifier interpolation
Implement expansion-key canonicalization
Implement CPX recursive-expansion cycle detection
Add tests for repeated specialization reuse
```

---

# 2. Rule of bounded decomposition

Use a bounded branching factor when decomposing work.

The preferred rule is:

```text
2–4 children per task
```

A task SHOULD NOT normally have more than **4 immediate children**.

This is the default design rule.

When a component genuinely contains several strongly independent concerns, the branching factor MAY increase, but SHOULD NOT normally exceed:

```text
7 children
```

If more than 4 immediate children are necessary, explicitly state why grouping them into intermediate concepts would make the plan less clear.

Never create flat lists containing dozens of sibling tasks.

Instead of:

```text
Parser
  ├── task 1
  ├── task 2
  ├── task 3
  ├── task 4
  ├── task 5
  ├── task 6
  ├── task 7
  ├── task 8
  ├── task 9
  └── task 10
```

prefer:

```text
Parser
  ├── lexical integration
  │     ├── ...
  │     └── ...
  │
  ├── declaration grammar
  │     ├── ...
  │     └── ...
  │
  ├── expression grammar
  │     ├── ...
  │     └── ...
  │
  └── recovery and diagnostics
        ├── ...
        └── ...
```

The purpose is to keep every reasoning scope cognitively manageable for both humans and the model.

---

# 3. Task identity

Every task must have a stable hierarchical ID.

Example:

```text
1
1.1
1.1.1
1.1.1.1
```

Task IDs MUST NOT change merely because implementation progresses.

If new work is discovered, insert it underneath the appropriate existing branch.

Example:

```text
3.2.4
```

may gain:

```text
3.2.4.1
3.2.4.2
```

without renumbering unrelated parts of the plan where reasonably avoidable.

Stable IDs are important because discussions, commits, diagnostics, and specification references may refer to them.

---

# 4. Task status

Every task, including composite tasks, must have exactly one execution status:

```text
TODO
DOING
DONE
```

Meaning:

### TODO

No implementation work has been completed for the task.

### DOING

At least one part of the task has started, but the task is not completely satisfied.

### DONE

All requirements, implementation work, tests, and acceptance criteria belonging to the task are satisfied.

Do not mark something `DONE` merely because code exists.

`DONE` means:

```text
implemented
+
integrated
+
tested
+
acceptance criteria satisfied
+
no known unresolved work belonging to this task
```

---

# 5. Composite progress

Every task containing descendants must display progress as:

```text
[completed/total]
```

Progress is measured using **terminal leaf tasks in that task's subtree**.

Example:

```text
[DOING] [7/12] 3. CPX subsystem
```

means:

```text
7 terminal tasks DONE
12 terminal tasks total
```

This rule applies recursively.

Example:

```text
[DOING] [18/31] 3. Compile-time system
    [DONE]  [8/8]   3.1 Compile-time value model
    [DOING] [6/11]  3.2 CPX templates
    [DOING] [4/12]  3.3 Expansion engine
```

Composite status is derived as follows:

```text
all descendant leaves TODO
    → TODO

all descendant leaves DONE
    → DONE

otherwise
    → DOING
```

The progress counters must always agree with the actual descendant statuses.

---

# 6. Specification traceability

Every task—both composite and terminal—must explicitly reference the requirements it satisfies.

Use:

```text
Language:
Technical:
```

Example:

```text
[TODO] 3.2.3 Implement CPX identifier interpolation

Language:
- LS §8.3 Identifier Composition
- LS §33 CPX Name Conflicts

Technical:
- TS §16 CPX Template Representation
- TS §17 CPX Evaluator
```

Use the actual section numbers and titles from the specifications.

Do not write vague references such as:

```text
Language: CPX stuff
Technical: parser section
```

References must be precise enough that someone can open the specification and verify the implementation requirement.

Composite tasks must reference the broader specification sections covered by their subtree.

Leaf tasks must reference the specific requirements they implement.

---

# 7. Acceptance criteria

Every terminal task must contain explicit completion criteria.

Example:

```text
[TODO] 3.2.3 Implement CPX identifier interpolation

Language:
- LS §8.3 Identifier Composition

Technical:
- TS §16 CPX Template Representation

Deliverable:
- IdentifierTemplate representation
- literal/binding/literal segments
- rendering through semantic compile-time values

Acceptance:
- optional_{T}_t with T=int resolves to optional_int_t
- plain identifier Temporary is never interpreted as interpolation
- invalid interpolation type produces a diagnostic
- unit tests cover all three cases
```

A leaf cannot become `DONE` until its acceptance criteria pass.

---

# 8. Work hierarchy

At the highest level, organize the project into a small number of major workstreams.

A reasonable initial decomposition should resemble:

```text
1. Language front-end
2. Semantic model
3. Compile-time / CPX system
4. C transcoding backend
```

Then introduce another top-level grouping where required for areas such as tooling/build integration rather than flattening everything into the first four.

Do not assume this decomposition is automatically correct.

Validate it against both specifications and adjust it where necessary.

The important requirement is bounded hierarchical decomposition, not these exact labels.

---

# 9. Coverage analysis

Before implementation begins, build a specification coverage matrix.

For every normative language-specification section, identify at least one task responsible for satisfying it.

For every technical-architecture section containing an implementation requirement, identify at least one task responsible for implementing it.

Use a form equivalent to:

```text
LS §8.3 → tasks 3.2.3, 3.2.4
LS §10   → tasks 3.3.*
LS §17   → tasks 3.4.*
TS §16   → tasks 3.2.*
TS §19   → tasks 3.3.2, 3.3.3
```

At the end of planning, report any specification section that has no implementation task.

The desired result is:

```text
Uncovered normative requirements: 0
```

Do not begin broad implementation while obvious specification gaps remain in the plan.

---

# 10. Dependency modelling

Tasks must record meaningful dependencies.

Example:

```text
Depends:
- 1.3.2 AstArena
- 2.1.4 SymbolId registry
```

Do not create dependencies merely because one task appears earlier in the document.

Record only actual technical dependencies.

Use dependencies to determine execution order.

Prefer implementing a vertical path that becomes testable over completing unrelated low-level pieces simply because they are adjacent in the plan.

---

# 11. Vertical implementation strategy

Avoid implementing the entire compiler as disconnected horizontal layers before anything works end-to-end.

Prefer incremental vertical milestones.

For example:

```text
parse simple struct
    ↓
build AST
    ↓
resolve struct symbol
    ↓
lower struct
    ↓
emit C
    ↓
compile emitted C
```

Then expand this path.

Another milestone could be:

```text
parse instance method
    ↓
resolve self
    ↓
lower method
    ↓
rewrite invocation
    ↓
emit valid C
    ↓
execute test
```

Then:

```text
parse comptime function
    ↓
evaluate simple CPX
    ↓
inject declaration
    ↓
emit C
```

Each milestone should increase the set of valid C+ programs that can pass completely through the compiler.

---

# 12. Prototypes before generalisation

For complex subsystems, first implement the smallest useful end-to-end prototype.

Examples:

### CPX

Start with:

```text
comptime function
    ↓
one type argument
    ↓
one declaration CPX
    ↓
one interpolation
    ↓
generated struct
```

before implementing:

```text
nested CPX
reflection
dynamic expansion graphs
multiple insertion channels
```

### C backend

Start with:

```text
struct
function
variable
call
return
```

before implementing all C syntax.

### Imports

Start with:

```text
one C+ file imports another
```

before selective imports, aliases, package cycles, and C headers.

A prototype must conform to the eventual architecture; it must not be disposable code that bypasses architectural boundaries.

---

# 13. Do not create parallel language models

The implementation must maintain one authoritative compiler model.

Do not independently implement:

```text
compiler parser
LSP parser
CPX parser
VS Code semantic parser
```

unless the specifications explicitly require different syntax domains.

The architecture must preserve:

```text
Source
  ↓
Parser
  ↓
AST
  ↓
SemanticModel
```

and reuse those representations across:

```text
compiler
transcoder
LSP
CPX
IDE tooling
```

TextMate is lexical highlighting only and is not authoritative.

---

# 14. Source provenance is not optional

Every parser and transformation task must consider origin tracking from the beginning.

Do not implement AST transformations first and attempt to retrofit source maps later.

For every generated node, preserve or construct:

```text
Origin
```

according to the technical specification.

Tests for transformations should include origin assertions where appropriate.

For example:

```text
method declaration
    ↓ lowering
generated top-level C function
```

must retain provenance back to the original method.

---

# 15. Compiler passes must have explicit invariants

Every pass task must specify:

```text
Preconditions:
Postconditions:
Invalid states:
```

Example:

```text
MethodLoweringPass

Preconditions:
- method symbols resolved
- receiver type known

Postconditions:
- no runtime method remains nested inside a struct
- every method has a corresponding top-level callable representation
- all method-call references still point to the same semantic method identity

Invalid states:
- unresolved receiver
- ambiguous member call
```

This requirement applies especially to:

```text
CPX expansion
type stabilization
method lowering
closure lowering
defer lowering
hoisting
C-subset validation
```

---

# 16. Testing requirements

Every significant component must include tests as part of the same task hierarchy.

Tests are not a final project phase.

Each implementation branch should contain its own verification work.

Use several test levels:

```text
unit tests
component tests
compiler pipeline tests
golden tests
negative/error tests
source-map tests
```

End-to-end compiler features should eventually have fixtures equivalent to:

```text
input.cp
expected.expanded.cp
expected.c
expected.h
expected.map
expected.diagnostics
```

A feature is not `DONE` without its required tests.

---

# 17. Long-running work feedback

Do not work silently for long periods.

While executing implementation work, periodically report progress.

A progress report should occur when one of these conditions applies:

- several implementation operations have been performed;
- a meaningful subtask has completed;
- a blocking issue has been discovered;
- the implementation direction changes;
- work on a task is taking materially longer than expected.

Do not report every edit or command.

Report useful state changes.

Use concise updates such as:

```text
Working on 3.2 CPX templates.

Completed:
- 3.2.1 template node representation
- 3.2.2 direct compile-time bindings

Current:
- 3.2.3 identifier interpolation

CPX templates: 5/9
Compile-time system: 11/27
Overall: 34/118
```

If a problem is discovered:

```text
3.3.4 cannot be completed as originally planned because expansion identity
currently depends on unstable NodeId values.

I am adjusting 1.3.2 so NodeId remains stable across rewrites.
Affected tasks: 1.3.2, 3.3.4, 3.4.2.
```

Keep the plan synchronized with such discoveries.

---

# 18. Plan mutation

The plan is a living engineering artifact.

When implementation reveals missing work:

1. add the missing task beneath the smallest relevant parent;
2. add specification references;
3. add acceptance criteria;
4. update dependency relationships;
5. recompute progress counters;
6. explain briefly why the task was introduced.

Do not silently perform substantial unplanned work.

Do not mark a parent `DONE` and later hide newly discovered required work outside it.

Reopen the parent to `DOING` if necessary.

---

# 19. Task selection

When selecting the next task:

1. prefer tasks whose dependencies are complete;
2. prefer tasks that unlock multiple dependent tasks;
3. prefer tasks contributing to an executable vertical milestone;
4. avoid starting many unrelated tasks simultaneously.

Normally there should be only one principal `DOING` leaf task per implementation thread.

Keep work-in-progress small.

---

# 20. Definition of a manageable leaf

A task is sufficiently decomposed when all of these are true:

- one principal technical concern;
- clear inputs and outputs;
- implementation location is reasonably identifiable;
- acceptance can be objectively tested;
- it does not contain hidden independent features;
- completing it does not require redesigning an entire subsystem.

If a task contains several independent verbs such as:

```text
design, implement, integrate, optimize and test...
```

it probably needs further decomposition.

---

# 21. Required plan representation

Maintain the plan in a repository file such as:

```text
docs/implementation.plan.md
```

Use a machine- and human-readable Markdown format.

Example:

```markdown
## [DOING] [5/8] 3.2 CPX Template Model

**Language**
- LS §8 CPX Source Templates
- LS §32 Name Lookup
- LS §33 CPX Name Conflicts

**Technical**
- TS §16 CPX Template Representation

**Depends**
- 1.3 AST Model
- 2.2 Scope Model

### [DONE] 3.2.1 Define CpxCategory

**Language**
- LS §8.5 CPX Categories

**Technical**
- TS §16 CPX Template Representation

**Acceptance**
- UNIT, DECLARATION, MEMBER, STATEMENT, EXPRESSION and TYPE represented.
- Parser can assign expected CPX category.
- Tests pass.

### [DOING] 3.2.2 Implement template binding nodes
...

### [TODO] 3.2.3 Implement identifier composition
...
```

The plan file is authoritative for implementation progress.

---

# 22. Summary dashboard

At the beginning of the plan maintain a concise dashboard:

```text
C+ implementation

Overall: 27/146

[DOING] 1. Front-end             14/28
[DOING] 2. Semantic model         8/31
[DOING] 3. Compile-time / CPX     5/42
[TODO]  4. C backend              0/45

Current task:
3.2.3 — CPX identifier composition

Current milestone:
Generic struct specialization → emitted compilable C
```

Do not substitute this summary for the hierarchical plan.

It exists to make the current project state immediately visible.

---

# 23. Completion audits

When a composite component appears complete, perform an audit before marking it `DONE`.

Audit:

```text
Are all descendant tasks DONE?
Are all referenced specification requirements satisfied?
Do all relevant tests pass?
Are there TODO/FIXME placeholders belonging to this component?
Are source origins preserved?
Does the implementation respect architectural boundaries?
Are known limitations documented as deliberate specification limitations rather than unfinished work?
```

Only then mark the component `DONE`.

---

# 24. Final project completion

The project is complete only when:

```text
all terminal tasks are DONE

AND

all composite tasks are DONE

AND

specification coverage has no unresolved requirements

AND

all mandatory tests pass

AND

the compiler can perform the specified pipeline:

C+ source
  ↓
parse
  ↓
catalogue
  ↓
structural CPX
  ↓
type stabilization
  ↓
reflection
  ↓
semantic resolution
  ↓
lowering
  ↓
C AST
  ↓
C source + source mapping

AND

the LSP consumes the shared compiler semantic model

AND

no required functionality remains represented only by placeholders,
TODOs, mock implementations, or hard-coded prototype special cases.
```

---

# 25. Initial task

Start by reading both specifications completely.

Then perform only these activities:

```text
1. Build the specification coverage inventory.
2. Construct the hierarchical implementation plan.
3. Validate the decomposition and dependency graph.
4. Present the initial plan/dashboard for review.
```

Do not begin broad implementation until this initial plan exists.

Small investigative prototypes MAY be used where necessary to validate an architectural assumption, but they must be explicitly identified as such.

The first deliverable is therefore:

```text
docs/implementation.plan.md
```

containing:

- the dashboard;
- the complete hierarchical task tree;
- TODO/DOING/DONE state;
- completed/total counters;
- specification references;
- dependencies;
- acceptance criteria for every leaf;
- specification coverage;
- proposed vertical milestones.

After the plan has been reviewed, execute it incrementally while keeping it synchronized with reality.

---

# 26. Planning principle

Apply the following rule throughout the project:

> Decompose until each reasoning scope contains only a small number of coherent concepts, and continue recursively rather than increasing the number of siblings.

Prefer approximately four concepts per scope.

Use up to seven only where the concepts are sufficiently cohesive and splitting them would make the architecture less understandable.

The purpose is not an arbitrary numeric restriction. It is to limit the active reasoning surface at every level of the hierarchy so that both a human reviewer and the implementation model can understand the complete local problem at once.

Therefore:

```text
wide plan
    → regroup

complex task
    → decompose

large leaf
    → decompose

too many simultaneous concerns
    → introduce another hierarchical level
```

The resulting plan should be navigable as a tree in which any node provides a manageable local context while the complete hierarchy still covers the entire specification.

---

# 27. Execution principle

At any point during implementation it must be possible to answer these questions from the plan alone:

```text
What are we implementing now?

Why is it required?

Which specification sections require it?

What does it depend on?

What remains beneath this component?

How much of the component is complete?

How will we know this particular task is finished?

Which higher-level requirement does this task contribute to?
```

If the plan cannot answer one of those questions, improve the plan before allowing the implementation to drift away from it.