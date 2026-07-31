# Project Guidelines & Rules (Ponytail Philosophy & Graphify Integration)

Adhere strictly to **Ponytail** principles for all coding, refactoring, architecture, and code review tasks in this codebase.

## Core Philosophy
The best code is code never written. Lazy means efficient, not careless. Avoid over-engineering, bloat, boilerplate, and unnecessary dependencies.

## Graphify Navigation Rules (Prevent Unnecessary File Reading)
Before asking or searching codebase questions, check if `graphify-out/graph.json` exists.
- Query graph first: Use `/graphify query "<question>"` for architecture, component relationships, or flow questions.
- Trace paths: Use `/graphify path "<node1>" "<node2>"` to find structural dependencies before modifying code.
- Update graph: Run `graphify --update` (or `/graphify --update`) after structural code changes.

## The Decision Ladder
Stop at the first rung that holds:
1. **Does this need to exist at all?** Speculative need = skip it (YAGNI).
2. **Already in this codebase?** Reuse existing helpers, utilities, entities, or patterns.
3. **Stdlib does it?** Use standard library features.
4. **Native platform feature covers it?** Use database constraints, native framework annotations, or built-in capabilities over custom logic.
5. **Already-installed dependency solves it?** Use existing dependencies. Do not add new libraries for what a few lines of code can solve.
6. **Can it be one line?** Make it one line.
7. **Only then:** Write the absolute minimum working code.

## Strict Rules
- **No Unrequested Abstractions:** No single-implementation interfaces, single-product factories, or premature abstractions.
- **Deletion > Addition:** Prefer deleting redundant code or replacing complex logic with simpler built-in primitives.
- **Fix Root Causes:** Fix issues where all callers route through, rather than patching single symptoms.
- **Concise Output:** Provide code first, followed by at most 3 short lines detailing what was skipped and when to add it (`[code] → skipped: [X], add when [Y].`).
- **Mark Simplifications:** Flag intentional trade-offs using `// ponytail: [rationale & upgrade path]`.
