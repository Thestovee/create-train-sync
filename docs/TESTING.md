# Validation and reproduction

**AI disclosure:** tests and analysis are AI-generated. Human participants ran the
gameplay sessions; no independent human code audit has been performed.

## Automated checks

`python build.py` compiles for Java 21 and runs:

- interpolation-budget checks for delayed snapshots and invalid fractions;
- coherent recovery of two- and four-point carriages;
- rejection of stale, missing and mismatched-node targets before recovery writes;
- unchanged normal paths, non-track fallback and disabled intervention;
- ASM inspection of the actual Create 6.0.10 JAR: method descriptors and exactly
  one expected final-fraction store, including its float-local ordinal.

These tests are not an automated Minecraft client launch. The mixin targets a
version-specific local; dependency bounds intentionally restrict it to 6.0.10.

## Human playtests

Minecraft 1.21.1 / NeoForge 21.1.265 / Create 6.0.10, a modded world containing
RailX 0.2.0-build.15, Steam 'n' Rails 0.3.0-beta.2 and Threaded Trains 1.0.0.
Two clients connected using e4mc. Diagnostic records include per-point state,
snapshot application, client ticks and server state where capture overlapped.

Single-carriage sessions: both clients observed a large improvement. A six-carriage
ICE-3 session: no internal flips in roughly 2,000 observations per carriage.
See [the research report](../research/REPORT.md) for metrics and limitations.

## Open test matrix

Not yet specifically validated: dimension/portal transitions, derailed trains,
upside-down bogeys, one-bogey carriages in-game, abrupt graph replacement,
every other train addon and disabling individual protections during a paired run.
Two-point recovery has a synthetic test, which is not an in-game compatibility claim.

## Report a bug

Include Minecraft, loader, Create and addon versions, the mod version, whether the
problem occurs without this mod, a minimal reproduction, and a short recording if
possible. Attach client `latest.log` after reviewing it for private information.
Train Trace is optional; do not publish a whole world or raw private logs by default.
