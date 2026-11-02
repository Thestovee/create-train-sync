# Train Trace — optional research tool

AI-generated diagnostic code; see [the disclosure](../../AI_DISCLOSURE.md).
This is a local recording tool, **not** a dependency of Train Sync.

Build from the repository root with `python build.py --component trace`. Install
the resulting `traintrace-0.2.1.jar` on the test server and each client to record
both sides. Restart the game/server after changing mods. Use matching versions.

CSV files are written locally under `logs/traintrace/`. The tool sends no telemetry
to an external service. It records train UUIDs, world coordinates, player/camera
positions, timing and correction details, so review files before sharing them.

Capture arms on the first associated snapshot serialization on the server, or
first carriage update/tick on the client. It stops after 15 minutes or a conservative
256 MiB size limit. `capture_started_epoch_ms` marks the start. To capture another
session after expiry, restart. Set JVM argument `-Dtraintrace.enabled=false` to disable.

Rows are queued to a bounded background writer. Overflow drops rows instead of
blocking gameplay. The closing line reports `dropped` and `errors`; a live copied
file may not have that footer. Recording adds overhead and is not a free profiler.

Run `python tools/train-trace/analyze.py /path/to/trace.csv` for phase counts and
client update intervals. Intervals describe application by the game thread, not
network RTT. Process-local monotonic clocks cannot be compared across computers;
wall clocks may be offset. See [the report](../../research/REPORT.md).
