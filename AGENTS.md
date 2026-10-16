# AGENTS.md

How agents work in this repo. The build enforces most of what matters; this file
covers the few things it can't, plus the loop you run every cycle.

Read this before starting a card. The single source of truth for "done" is
`mvn verify` going green — not your own judgment that the code looks finished.

---

## The per-cycle protocol

Run this on every iteration. Step 2 is non-negotiable: a red-then-green
transition is your only ground truth that a test actually tests something.

1. **Restate the card's acceptance criteria as a test checklist.**
   If anything is ambiguous or contradicts the current API, STOP and ask
   before writing code.
2. **Write ONE failing test** for the next criterion. Run it. Show the red
   output. (No red, no green — a test that has never failed proves nothing.)
3. **Write the minimum code** to pass it. Run it. Show green.
4. **Refactor.** Re-run the fast suite (`mvn test`).
5. **Run `mvn verify`.** If a gate fails, FIX THE CODE OR THE TEST.
   Never weaken, `@Disabled`, delete, or lower the threshold of a gate to
   reach green. If you believe a gate is genuinely wrong, STOP and say so.
6. Next criterion. Repeat.
7. All criteria green → stop and hand back for review. Do NOT pick up
   adjacent work, refactors, or "while I'm here" improvements.

---

## The gates (what `mvn verify` runs, and why)

| Gate | Catches | Gameable? |
|------|---------|-----------|
| Surefire | unit + slice tests (`*Test`) | — |
| Failsafe | integration tests (`*IT`) | — |
| Spotless | formatting / imports | no — auto-fixable with `mvn spotless:apply` |
| ArchUnit | layering, **field injection** | no — structural facts |
| JaCoCo | line-coverage floor | **YES** — can be hit with assertion-free tests |
| PITest | tests that don't actually test | no — this is the anti-cheat |

JaCoCo tells you a line was *executed*. PITest tells you a test would
*notice if that line were wrong*. When they disagree, PITest is right and your
tests are hollow. Fix the tests, never the threshold.

---

## Testing conventions

These are the house style for tests. ArchUnit and PITest enforce the parts
that can be enforced; the rest is on you and the reviewer.

- **Subject-under-test pattern.** Name the subject `sut`. Construct it by hand
  (`sut = new Thing(mockA, mockB)`), don't reach for `@InjectMocks` — explicit
  construction means adding a dependency breaks the test at compile time
  instead of silently injecting a fresh mock.
- **`@ExtendWith(MockitoExtension.class)`** with strict stubs (the default).
  An unused stub fails the test. Do not relax to lenient mode to make an
  over-stubbed test pass — delete the unnecessary stub instead.
- **Stub only what the scenario exercises.** Do not mirror the class by
  stubbing every collaborator method. If a path shouldn't touch a collaborator,
  prove it with `verifyNoInteractions`.
- **Assert on output or state, not on `verify`.** `verify(...)` is for genuine
  outbound side effects (a save, a publish), never a substitute for asserting
  what the `sut` returned. "All verify, no assert" is the exact shape PITest
  will fail you on.
- **Use the right slice, lightest first:**
  - domain / service logic → plain JUnit + Mockito, no Spring context
  - controllers → `@WebMvcTest`, collaborators mocked with `@MockitoBean`
  - repositories / mapping → `@DataJpaTest`
  - `@SpringBootTest` → genuine end-to-end wiring ONLY, named `*IT`
- **Never silently escalate** a slice test to `@SpringBootTest` to make
  something pass. If you're stuck at the slice level, say so.

---

## Production-code conventions

- **Constructor injection only.** Use `@RequiredArgsConstructor` + `private
  final` fields. Never `@Autowired` on a field — ArchUnit fails the build on it.
  (This is what keeps the `sut` constructible without a Spring context.)
- **Respect the layers:** `controller → service → repository → modle -> config`, one
  direction. A controller must not touch a repository. ArchUnit enforces this.

---

## Stop and ask — don't guess — if

- the acceptance criteria are ambiguous or contradict the current API shape
- you need a **new dependency**
- you're adding a **constructor parameter** to an existing class (it ripples to
  every test that constructs it — flag it, don't quietly absorb it)
- you're tempted to change a method signature used elsewhere
- a gate looks wrong and your instinct is to route around it
- the card seems to require work that's listed as out of scope

---

## Never

- Disable, delete, `@Disabled`, or weaken a test or gate to get to green.
- Lower a coverage or mutation threshold to pass.
- Add a dependency without asking.
- Mock the type under test.
- Expand scope beyond the card.
- Claim green without pasting the actual `mvn verify` output.
