# Changelog

## 1.0.0-beta.1

First public beta, following local multiplayer playtests of the 0.1.0 prototype.

- Bound the accumulated interpolation fraction of a carriage snapshot to one.
- Recover active wheel points together when an individual point takes Create's
  immediate fallback, provided all targets match the current snapshot.
- Apply the same coherent recovery following a detected follow-path reset.
- Preserve server simulation, train physics, controls, balance and packet format.
- Include reproducible builds, invariant/target-bytecode checks, research and
  optional diagnostic source. Train Trace is not a runtime dependency.

Behavioral algorithm unchanged from the tested prototype. Public metadata and
packaging updated; public availability does not imply every edge case was tested.
