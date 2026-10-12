# DenHub — AI Agent Instructions

> **MANDATORY RULES FOR ALL AI CODING AGENTS.**  
> These rules apply to every AI agent working in the DenHub repository.  
> Violating these rules means the task is **NOT** considered complete.

---

# 1. MANDATORY WORKFLOW

Every task **MUST** follow this workflow:

```
READ DOCS
    ↓
INSPECT CURRENT PROJECT
    ↓
UNDERSTAND CURRENT PROGRESS
    ↓
CREATE IMPLEMENTATION PLAN
    ↓
WAIT FOR USER APPROVAL
    ↓
IMPLEMENT
    ↓
WRITE / UPDATE TESTS
    ↓
RUN TESTS
    ↓
FIX FAILURES
    ↓
RUN TESTS AGAIN
    ↓
UPDATE DOCUMENTATION
    ↓
UPDATE PROJECT PROGRESS
    ↓
REVIEW GIT DIFF
    ↓
COMMIT ONLY IF AUTHORIZED
    ↓
STOP
```

> [!CAUTION]
> **PUSH IS NOT ALLOWED.**  
> The agent **MUST NOT** skip the planning and approval stage.

---

# 2. MANDATORY READING

Before planning or modifying application code, the agent **MUST** read:

1. [`AGENTS.md`](./AGENTS.md)
2. [`docs/00-DEVELOPMENT-PROCESS.md`](./docs/00-DEVELOPMENT-PROCESS.md)
3. [`docs/01-PROJECT-OVERVIEW.md`](./docs/01-PROJECT-OVERVIEW.md)
4. [`docs/02-SYSTEM-ARCHITECTURE.md`](./docs/02-SYSTEM-ARCHITECTURE.md)
5. [`docs/03-DATABASE-DESIGN.md`](./docs/03-DATABASE-DESIGN.md)

The agent must then inspect the existing source code related to the requested task.

**Do NOT start implementation based only on the user's prompt.**

---

# 3. UNDERSTAND CURRENT PROJECT STATE

Before creating a plan, inspect:

- Current Project Progress
- Current Work / Handoff
- Completed Work Log
- Existing implementation
- Existing tests
- Relevant Git changes if available

Determine:

- What has already been implemented?
- What is currently being worked on?
- What remains TODO?
- What contracts are CONFIRMED?
- What is still TBD?
- What tests already exist?
- Are there existing issues?
- Are there uncommitted changes from another developer?

**Never assume something is missing before checking the project.**

---

# 4. PLAN BEFORE CODE

Before modifying code, the agent **MUST** create an implementation plan.

The plan must contain at minimum:

## Task
What needs to be implemented.

## Current State
What currently exists in the project related to the task.

## Related Documentation
Relevant sections from:
- Project Overview
- System Architecture
- Database Design

## Files Expected to Change
List files/modules expected to be created or modified.

## Backend Changes
If applicable.

## Frontend Changes
If applicable.

## Database Changes
If applicable.

## API / WebSocket Changes
If applicable.

## Security / Permission Impact
If applicable.

## Test Plan
Specify:
- what tests will be written
- what existing tests may need updating
- what commands will be executed

## Documentation Impact
Which `/docs` files may need updating.

## Risks / Open Questions
Anything unclear, TBD, conflicting, or potentially outside scope.

---

# 5. APPROVAL GATE

After presenting the plan:

**STOP.**  
**WAIT FOR USER APPROVAL.**

The agent **MUST NOT**:
- create application code
- modify application code
- modify database schema
- create migrations
- change API contracts
- install dependencies
- refactor code
- create a commit

until the user explicitly approves the plan.

Examples of approval:
- "OK"
- "Làm đi"
- "Duyệt"
- "Proceed"
- "Implement"

If the user requests changes to the plan:
```
UPDATE PLAN ──> PRESENT AGAIN ──> WAIT FOR APPROVAL
```

**Do not interpret silence as approval.**

---

# 6. SOURCE OF TRUTH

Use:

- [`docs/01-PROJECT-OVERVIEW.md`](./docs/01-PROJECT-OVERVIEW.md)  
  $\longrightarrow$ Business, scope, features, business rules
- [`docs/02-SYSTEM-ARCHITECTURE.md`](./docs/02-SYSTEM-ARCHITECTURE.md)  
  $\longrightarrow$ FE, BE, REST API, DTO, Security, JWT, WebSocket
- [`docs/03-DATABASE-DESIGN.md`](./docs/03-DATABASE-DESIGN.md)  
  $\longrightarrow$ Entity, field, relationship, SQL Server, MongoDB, constraint, index
- [`docs/00-DEVELOPMENT-PROCESS.md`](./docs/00-DEVELOPMENT-PROCESS.md)  
  $\longrightarrow$ Development workflow, progress, handoff, testing rules

Do not redefine these contracts inside `AGENTS.md`.

---

# 7. NO GUESSING

The agent **MUST NOT** invent:
- features
- business rules
- roles
- permissions
- API endpoints
- request DTOs
- response DTOs
- entities
- database fields
- relationships
- enums
- statuses
- WebSocket destinations
- WebSocket payloads

If required information is missing or:
`Status: TBD`

the agent must identify it in the plan.

If implementation depends on that decision:  
**STOP and ask for a decision.**  
**Do NOT silently make the decision for the team.**

---

# 8. SCOPE CONTROL

**DenHub is NOT a Discord clone.**

Discord is only a reference for selected group communication concepts.

Do **NOT** assume DenHub has:
- Discord Server
- Category
- Channel
- Direct Message
- Friend System
- Bot
- Thread
- Reaction
- Voice Call
- Video Call
- Screen Sharing
- Discord-style permission hierarchy

unless DenHub documentation explicitly confirms the feature.

**Meeting is NOT part of the current scope.**  
Do not add functionality merely because Discord has it.

---

# 9. IMPLEMENT ONLY THE APPROVED PLAN

After approval, implement **only** what was approved.

Do **NOT** perform unrelated:
- refactoring
- renaming
- architecture changes
- dependency upgrades
- formatting across unrelated files
- API changes
- database changes
- permission changes
- feature additions

If implementation reveals that the approved plan must materially change:
1. **STOP.**
2. Explain the required change.
3. **CREATE AN UPDATED PLAN.**
4. **WAIT FOR APPROVAL AGAIN.**

---

# 10. TESTS ARE PART OF IMPLEMENTATION

Writing code without appropriate tests is **NOT** considered complete.

For every implementation task, the agent **MUST** determine whether new tests or updates to existing tests are required.

When behavior is added or changed, tests **MUST** normally be added or updated.

Tests must verify behavior, not merely increase test count.  
**Do NOT write meaningless tests only to satisfy a number.**

---

# 11. BACKEND TEST REQUIREMENTS

Use the appropriate testing tools already selected by DenHub:
- JUnit 5
- Mockito
- MockMvc

Depending on the task, tests may include:
- Service tests
- Controller tests
- Validation tests
- Security/authorization tests
- Exception/error tests
- Repository tests where meaningful
- WebSocket-related tests where meaningful

The test scope must correspond to the behavior being changed.

---

# 12. FRONTEND VERIFICATION

For frontend changes, the agent must verify the affected behavior.

At minimum, use the project's applicable commands.

Example:
```bash
npm run build
```

If configured:
```bash
npm run lint
```

If automated frontend tests exist:
```bash
npm test
```
(or the relevant test command).

Also inspect relevant:
- routes
- API integration
- loading states
- error states
- authorization behavior
- forms/validation
- console/build errors

**Do not claim frontend verification that was not actually performed.**

---

# 13. TEST BEFORE COMMIT

The following workflow is **FORBIDDEN**:
```
CODE ──> COMMIT ──> TEST
```

**Required workflow:**
```
CODE
  ↓
WRITE / UPDATE TESTS
  ↓
RUN TESTS
  ↓
FIX
  ↓
RUN TESTS AGAIN
  ↓
BUILD / VERIFY
  ↓
UPDATE DOCS
  ↓
REVIEW DIFF
  ↓
COMMIT
```

Tests **MUST** run before a commit is created.

---

# 14. TEST COMMANDS

For backend, run the actual project test command.

Examples:

**Gradle (Project Default):**
- Windows: `.\gradlew.bat test`
- Unix/macOS: `./gradlew test`
- Full check: `.\gradlew.bat check`

**Maven (if applicable):**
- Windows: `mvnw.cmd test`
- Unix/macOS: `./mvnw test`
- When appropriate: `./mvnw clean verify`

**Frontend:**
- Run applicable commands such as: `npm run build`
- And, when configured: `npm run lint`, `npm test`

Use commands that actually exist in the repository.  
**Do not invent commands.**

---

# 15. TEST FAILURE

If tests fail:  
**DO NOT COMMIT THE TASK AS COMPLETE.**

Required process:
```
TEST ──> FAIL ──> INVESTIGATE ──> FIX ──> TEST AGAIN
```

Continue until relevant tests pass.

If the failure existed before the current task:
Document:
`PRE-EXISTING ISSUE`

Include:
- failing test
- observed error
- why it appears unrelated to the current task

Do not silently modify unrelated modules to make all tests green.  
**Do not claim PASS when tests failed.**

---

# 16. DOCUMENTATION UPDATE

After implementation and successful testing, check documentation impact:

- **Business / Feature / Business Rule:**  
  $\longrightarrow$ [`docs/01-PROJECT-OVERVIEW.md`](./docs/01-PROJECT-OVERVIEW.md)
- **Architecture / API / DTO / Security / WebSocket:**  
  $\longrightarrow$ [`docs/02-SYSTEM-ARCHITECTURE.md`](./docs/02-SYSTEM-ARCHITECTURE.md)
- **Entity / Field / Relationship / Constraint / Index / Status:**  
  $\longrightarrow$ [`docs/03-DATABASE-DESIGN.md`](./docs/03-DATABASE-DESIGN.md)
- **Progress / Completed Work / Handoff:**  
  $\longrightarrow$ [`docs/00-DEVELOPMENT-PROCESS.md`](./docs/00-DEVELOPMENT-PROCESS.md)

Not every task must modify all documents.  
However, every task **MUST** check whether each document is affected.  
**Contract-changing code without synchronized documentation is NOT DONE.**

---

# 17. UPDATE PROJECT PROGRESS

Before considering the task complete, update:
- Current Project Progress
- Completed Work Log
- Current Work / Handoff if necessary

in:  
[`docs/00-DEVELOPMENT-PROCESS.md`](./docs/00-DEVELOPMENT-PROCESS.md)

The next developer or AI agent should be able to determine what has been completed without reconstructing the entire project from source code.

---

# 18. REVIEW BEFORE COMMIT

Before committing, inspect:
```bash
git status
git diff
```
If files are staged:
```bash
git diff --staged
```

Check for:
- unintended files
- unrelated modifications
- temporary code
- debug logs
- commented-out experiments
- secrets
- tokens
- passwords
- `.env`
- generated files
- accidental contract changes

Do not blindly stage everything.  
Avoid `git add .` when unrelated changes exist.  
**Stage only files belonging to the approved task.**

---

# 19. COMMIT PERMISSION

The agent **MUST NOT** automatically create commits merely because implementation is complete.

A commit may only be created when:
1. The implementation plan was approved.
2. Implementation is complete.
3. Required tests were written/updated.
4. Relevant tests pass.
5. Required build/verification passes.
6. Documentation is synchronized.
7. Project progress is updated.
8. Git diff was reviewed.
9. **The user authorized the agent to commit.**

If the user did **NOT** authorize committing:  
**STOP after reporting that the work is ready for commit.**

---

# 20. COMMIT QUALITY

Each commit should represent **one logical change**.

### Good examples:
- `feat(auth): implement login endpoint`
- `feat(room): add room creation`
- `feat(message): add realtime messaging`
- `test(room): add room service tests`
- `fix(auth): handle expired JWT`
- `docs(api): update room API contract`

### Bad examples:
- `update`
- `fix`
- `done`
- `final`
- `code`
- `project update`

Do not create fake or meaningless commits to increase commit count.

---

# 21. PUSH IS FORBIDDEN

> [!CAUTION]
> **AI AGENTS ARE NOT ALLOWED TO PUSH.**

The agent **MUST NOT** execute:
```bash
git push
```
or any equivalent command that uploads commits to a remote repository.

This applies even if:
- tests pass
- implementation is complete
- the user asked to commit
- the branch is a feature branch
- the remote appears correctly configured

**The agent's responsibility ends locally.**  
**The human developer is responsible for pushing.**

---

# 22. MAIN / MASTER PROTECTION

Direct modification of remote `main` or `master` is strictly prohibited.

The agent **MUST NEVER**:
- push to `main`
- push to `master`
- force push
- force push with lease
- delete remote branches
- merge directly into remote main/master
- rewrite shared Git history

Forbidden commands include:
```bash
git push origin main
git push origin master
git push --force
git push -f
```
and equivalent operations.

The agent must **NEVER** bypass branch protection.

---

# 23. DESTRUCTIVE GIT OPERATIONS

Without explicit user approval, the agent **MUST NOT** execute destructive operations such as:
- `git reset --hard`
- `git clean -fd`
- `git checkout -- .`
- `git restore .`
- `git rebase`
- history rewriting
- force operations
- branch deletion

Do not discard another developer's uncommitted work.  
If unexpected changes exist:  
**STOP and report them.**

---

# 24. BRANCH SAFETY

Before implementation, inspect the current Git branch.

If currently on `main` or `master`:
- Do NOT push.
- Do NOT perform destructive Git operations.

If the team's workflow requires a feature branch, propose the appropriate branch as part of the implementation plan.

Do not silently create/switch branches if doing so could interfere with existing work.

---

# 25. SECURITY

Never commit:
- passwords
- JWT secrets
- API keys
- database credentials
- private keys
- access tokens
- refresh tokens
- production `.env` files

Use environment variables according to project documentation.

If a secret is discovered in tracked files:  
**STOP and report it.**  
Do not expose the secret in the final response.

---

# 26. DEFINITION OF DONE

A task is **DONE** only when:
- [ ] Required docs were read
- [ ] Existing implementation was inspected
- [ ] Current project progress was understood
- [ ] Implementation plan was created
- [ ] User approved the plan
- [ ] Only approved scope was implemented
- [ ] Appropriate tests were written/updated
- [ ] Relevant tests passed
- [ ] Build/verification passed
- [ ] FE/BE contract remains synchronized
- [ ] Documentation was updated where required
- [ ] Project Progress was updated
- [ ] Git diff was reviewed
- [ ] No unintended changes exist
- [ ] No secrets/debug artifacts exist

*Commit requires separate authorization.*  
*Push is NEVER performed by the agent.*

---

# 27. AGENT STOP CONDITIONS

**STOP and ask/report when:**
- required business rule is `TBD`
- API contract is missing
- database relationship is unclear
- permission is unclear
- docs conflict with each other
- docs conflict with code
- FE and BE contracts conflict
- requested work exceeds approved scope
- implementation requires a material plan change
- destructive database migration is required
- architecture needs a major change
- unexpected uncommitted changes exist
- tests fail for an unrelated or unknown reason
- secrets are discovered

**Never resolve these situations by guessing.**

---

# 28. FINAL REPORT

At the end of implementation, report:

## Implemented
What was actually changed.

## Tests Added / Updated
What tests were created or modified.

## Verification
Exact commands actually executed and whether they passed.

## Documentation
Which documentation files were updated.

## Remaining Issues
TBD, blockers, pre-existing failures, or unresolved items.

## Git Status
Whether changes are uncommitted or committed.

## Push
Always state:
`Not pushed — AI agents are not allowed to push DenHub.`

Never claim:
- a test was executed when it was not
- a commit exists when it does not
- documentation was updated when it was not
- a push occurred when it did not
