# Create train synchronization: observations and a targeted client patch

Research date: November 1–2, 2026. Maintainer: The_Stove.

**AI disclosure:** Codex generated the investigation code, implementation, tests and
analysis. Humans supplied the scenario, operated the game, reported symptoms and
judged playability. No independent human source audit is claimed.

## Question and environment

Players observed trains pausing and catching up, reversing visually, and appearing
to compress or stretch. The investigation separated those synchronization symptoms
from dissatisfaction with Create's acceleration/braking model. Physics changes are
outside this mod's scope.

Tests used Minecraft 1.21.1, NeoForge 21.1.265, Create 6.0.10, with RailX
0.2.0-build.15, Steam 'n' Rails 0.3.0-beta.2, Threaded Trains 1.0.0 and other addons.
Two clients connected via e4mc. One participant was in Poland and one in Russia;
neither geographical location nor e4mc usage establishes the cause of every delay.

## Method

The optional [Train Trace source](../tools/train-trace) records:

- server wheel-point positions before/after train ticks;
- preparation and serialization of carriage snapshots;
- client snapshot application and per-point search results;
- client carriage ticks and camera/player positions;
- resets during interpolation.

Rows contain process-local monotonic time and wall time. Monotonic clocks from
different processes cannot be compared directly. Clock offsets were observed, so
equal wall-clock timestamps alone do not establish simultaneous states or latency.
Snapshot processing time is not the arrival time of bytes at the network socket.

Raw captures/worlds are not published: they contain private identifiers and world
coordinates. Consequently these aggregate results are not independently
recomputable from this repository alone. The tool is supplied for new captures;
sanitized aggregates below preserve the key findings without private logs.

## Two observed mechanisms

### Continuing a stale correction past its target

In Create 6.0.10, `CarriageSyncData.apply()` calculates correction distances.
`approach()` repeatedly traverses a fraction of those saved distances. Late-update
scaling reduces movement after a delay, but does not itself cap the accumulated
fraction at one. A delayed snapshot can therefore permit overshoot and subsequent
correction in the opposite direction. This is a code-derived mechanism, not a claim
that every visible pause had this sole cause.

### Recovering wheel points independently

When a point's path-distance search fails, `apply()` can place it directly at its
server target while other points continue interpolating. `approach()` also has an
immediate reset following unsuccessful path following. That permits a carriage's
points to represent different stages of recovery.

A diagnostic session recorded these events on the single-carriage locomotive:

| Measurement | Client A | Client B |
| --- | ---: | ---: |
| Points with no route returned in either search direction | 127 | 63 |
| Snapshot applications containing such points | 69 | 36 |
| Nonzero corrections reset during following | 21 | 11 |
| Measured immediate head-point displacements | 32 | 18 |
| Of these, displacement greater than 32 blocks | 32 | 18 |
| Largest immediate head-point displacement | 67.72 | 68.08 |

Already-at-target zero-distance searches were excluded, as were unsuccessful
directions where the other direction succeeded. These are point/event counts,
not independent incidents. The default search bound was 32 blocks. In every
measured head-point jump above, even straight-line separation exceeded 32, so this
bound is relevant; simply increasing it would not address stale interpolation or
independent point recovery. Other graph/search failures are not ruled out.

That session's server trace had already expired. It supports client control-flow
findings, but must not be described as a simultaneous server/client comparison.
Later tooling starts its recording timer on observed carriage update activity.

## Targeted patch

The patch caps each snapshot's cumulative interpolation fraction at one, after
Create's existing time-scaling calculation. Following a detected immediate reset,
it validates all active target points against that snapshot before recovering the
carriage together and clearing old corrections. It reuses addon-prepared targets
rather than rebinding points to a new graph.

It does not alter server simulation, the wire protocol, the search bound, train
physics or control inputs. It is carriage-local, not an atomic train-wide snapshot.

## Recorded results

The distance below is between selected head/tail wheel points, **not body length**.
An orientation flip is a negative dot product of that vector between neighboring
observations (a direction change greater than 90 degrees).

| Session | Observations | Wheel-point distance, blocks | Orientation flips |
| --- | ---: | --- | ---: |
| Client A, baseline | 1,937 | 0.28–74.89 | 20 |
| Client B, baseline | 2,444 | 0.12–59.37 | 26 |
| Client A, patch | 1,720 | 13.72–14.13 | 0 |
| Client B, patch | 2,147 | 13.81–14.18 | 0 |
| Server, patched-session capture | 4,567 | 13.92–14.09 | 0 |

Participants reported substantially more stable motion. Session durations, routes
and workload were not controlled as identical. This is observational evidence,
not a randomized benchmark, proof of causality for every symptom, or a TPS/FPS claim.

## Six-carriage test and the remaining limitation

A later ICE-3 session recorded approximately 2,052–2,099 observations for each of
six carriages. Every carriage had zero internal orientation flips; internal
wheel-point distance stayed within 13.87–14.10 blocks.

For adjacent carriages, 2,051 complete local samples were grouped by game tick,
without repeated carriage indices and with at most 25 ms observation spread.
This is sequential sampling, not a truly atomic train snapshot.

| Adjacent indices | Median chord | Minimum | Maximum | Samples below 1 or above 10 blocks |
| --- | ---: | ---: | ---: | ---: |
| 0–1 | 4 | 0.02 | 37.67 | 17 / 2,051 |
| 1–2 | 4 | 0.39 | 37.14 | 15 / 2,051 |
| 2–3 | 5 | 1.97 | 30.96 | 9 / 2,051 |
| 3–4 | 4 | 0.08 | 35.07 | 16 / 2,051 |
| 4–5 | 4 | 1.93 | 37.67 | 12 / 2,051 |

These chords connect selected wheel points, not coupler gaps; nominal saved
carriage spacing is a different quantity. Server chords stayed close to 4 or 5
blocks. Rare client spacing anomalies remain, though the participant did not
consider them materially disruptive after the improvement. No claim is made that
train-wide synchronization is completely solved or that these readings prove a
physical server collision.

## Separate server issues

Earlier logs contained seven railway worker-task exceptions: five concurrent
modification exceptions, one graph-sync null reference and one RailX null graph.
They warrant a separate thread-safety investigation. A later session showed visible
symptoms without those exceptions, so they are not a necessary condition for the
client defect fixed here. This addon does not fix those server errors.

## Limits and next questions

- Whole-carriage recovery can still snap; waiting for the next snapshot can pause.
- Chunk/loading stalls need frame/loading measurements, not attribution by appearance.
- A buffered train-wide timeline might improve residual spacing but would expand scope.
- Portals, graph replacement and all other addon combinations remain unverified.
- Positive playtests do not replace human review of the AI-generated implementation.

Primary code reference: [Create 6.0.10 train sources](https://github.com/Creators-of-Create/Create/tree/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/content/trains).
The report describes behavior in the studied build; it is not a statement about
maintainer intent, all Create releases or overall source-code quality.
