// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package traintrace;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Observation only: never changes Create, world or packet state. */
public final class Trace {
    private static final ArrayBlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(16384);
    private static final ConcurrentMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final AtomicLong DROPPED = new AtomicLong(), ERRORS = new AtomicLong();
    private static volatile boolean running;
    private static volatile boolean started;
    private static volatile boolean armed;
    private static volatile long deadline;
    private static Thread writer;
    private static long viewTick = Long.MIN_VALUE;
    private record Context(Object entity, Object carriage) {}
    private static final Map<Object, Context> SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());
    private record ApplyContext(Object data, Object entity, Object carriage) {}
    private static final ThreadLocal<ApplyContext> APPLY = new ThreadLocal<>();
    private static final ThreadLocal<float[]> APPROACH_DELTA = new ThreadLocal<>();
    private static final long MAX_BYTES = 256L * 1024 * 1024;

    public static synchronized void start() {
        if (started) return;
        started = true;
        if (!Boolean.parseBoolean(System.getProperty("traintrace.enabled", "true"))) return;
        deadline = Long.MAX_VALUE;
        running = true;
        writer = new Thread(Trace::write, "TrainTrace writer");
        writer.setDaemon(true);
        writer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            try { writer.join(4000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "TrainTrace shutdown"));
    }

    private static synchronized void arm() {
        if (!running || armed) return;
        armed = true;
        deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
        enqueue("# capture_started_epoch_ms=" + System.currentTimeMillis());
    }

    private static void write() {
        Path path = Path.of("logs", "traintrace", "trace-" + Instant.now().toString().replace(':', '-') + ".csv");
        long bytes = 0, lastFlush = System.nanoTime();
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                out.write("epoch_ms,mono_ns,thread,phase,game_tick,side,train,index,speed,target_speed,distance,entity_x,entity_y,entity_z,yaw,leading,trailing,detail\n");
                while (running || !QUEUE.isEmpty()) {
                    String row = QUEUE.poll(250, TimeUnit.MILLISECONDS);
                    if (row != null) {
                        out.write(row); out.newLine();
                        bytes += row.length() * 3L; // conservative UTF-8 size bound
                    }
                    if (System.nanoTime() - lastFlush > TimeUnit.SECONDS.toNanos(1)) {
                        out.flush(); lastFlush = System.nanoTime();
                    }
                    if (bytes >= MAX_BYTES || System.nanoTime() > deadline) {
                        running = false;
                        QUEUE.clear();
                        out.write("# capture_limit_reached\n");
                    }
                }
                out.write("# dropped=" + DROPPED.get() + ",errors=" + ERRORS.get() + "\n");
            }
        } catch (Exception e) {
            running = false;
            System.err.println("[TrainTrace] Cannot write " + path + ": " + e);
        }
    }

    public static Object field(Object o, String name) throws Exception {
        if (o == null) return null;
        String key = o.getClass().getName() + "#" + name;
        Field f = FIELDS.get(key);
        if (f == null) {
            Class<?> cls = o.getClass();
            while (cls != null) {
                try { f = cls.getDeclaredField(name); break; }
                catch (NoSuchFieldException e) { cls = cls.getSuperclass(); }
            }
            if (f == null) throw new NoSuchFieldException(key);
            f.setAccessible(true); FIELDS.putIfAbsent(key, f);
        }
        return f.get(o);
    }

    public static Object call(Object o, String name, Object... args) throws Exception {
        if (o == null) return null;
        String key = o.getClass().getName() + "#" + name + "/" + args.length;
        Method method = METHODS.get(key);
        if (method == null) {
            for (Method candidate : o.getClass().getMethods()) {
                if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) continue;
                boolean fits = true;
                for (int i = 0; i < args.length; i++)
                    if (args[i] != null && !candidate.getParameterTypes()[i].isInstance(args[i])) fits = false;
                if (fits) { method = candidate; break; }
            }
            if (method == null) throw new NoSuchMethodException(key);
            method.setAccessible(true); METHODS.putIfAbsent(key, method);
        }
        return method.invoke(o, args);
    }

    private static String vector(Object v) throws Exception {
        return v == null ? "" : field(v, "x") + ";" + field(v, "y") + ";" + field(v, "z");
    }

    private static String point(Object carriage, String accessor, Object graph) throws Exception {
        Object p = call(carriage, accessor);
        if (p == null || field(p, "edge") == null || field(p, "node1") == null || field(p, "node2") == null) return "unbound";
        return vector(call(p, "getPosition", graph));
    }

    private static String csv(Object... values) {
        StringJoiner row = new StringJoiner(",");
        for (Object v : values) row.add("\"" + String.valueOf(v == null ? "" : v).replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ') + "\"");
        return row.toString();
    }

    private static void enqueue(String row) { if (!QUEUE.offer(row)) DROPPED.incrementAndGet(); }
    private static void error(Exception e) {
        if (ERRORS.incrementAndGet() <= 3) System.err.println("[TrainTrace] Observation failed: " + e);
    }

    public static void train(Object train, Object level, String phase) {
        if (!running || !armed) return;
        try {
            List<?> carriages = (List<?>) field(train, "carriages");
            for (int i = 0; i < carriages.size(); i++) sample(train, carriages.get(i), null, level, phase, i, "");
        } catch (Exception e) { error(e); }
    }

    public static void entity(Object entity) {
        if (!running) return;
        try {
            Object level = call(entity, "level");
            if (!Boolean.TRUE.equals(field(level, "isClientSide"))) return;
            arm();
            Object carriage = call(entity, "getCarriage");
            if (carriage == null) return;
            Object train = field(carriage, "train");
            sample(train, carriage, entity, level, "client_tick", field(entity, "carriageIndex"), "");
            view(level);
        } catch (Exception e) { error(e); }
    }

    public static void sync(Object data, Object entity, Object carriage, String phase) {
        if (!running) return;
        try {
            Object level = call(entity, "level");
            if (!Boolean.TRUE.equals(field(level, "isClientSide"))) return;
            String detail = "age=" + field(data, "ticksSince")
                + ";delta=" + Arrays.toString((float[]) field(data, "pointDistanceSnapshot"))
                + ";wheels=" + field(data, "wheelLocations")
                + ";fallback=" + (field(data, "fallbackLocations") != null)
                + ";points=" + carriagePoints(carriage)
                + ";targets=" + targetPoints(data);
            sample(field(carriage, "train"), carriage, entity, level, phase, field(entity, "carriageIndex"), detail);
        } catch (Exception e) { error(e); }
    }

    public static void beginApply(Object data, Object entity, Object carriage) {
        if (!running) return;
        arm();
        APPLY.set(new ApplyContext(data, entity, carriage));
        sync(data, entity, carriage, "sync_before");
    }

    public static void endApply(Object data, Object entity, Object carriage) {
        try { sync(data, entity, carriage, "sync_after"); }
        finally { APPLY.remove(); }
    }

    private static String pointState(Object point) throws Exception {
        if (point == null) return "null";
        Object n1 = field(point, "node1"), n2 = field(point, "node2");
        return (n1 == null ? "?" : call(n1, "getNetId")) + ":"
            + (n2 == null ? "?" : call(n2, "getNetId")) + "@" + field(point, "position");
    }

    private static String carriagePoints(Object carriage) throws Exception {
        StringJoiner states = new StringJoiner("|");
        Object bogeys = field(carriage, "bogeys");
        for (String side : List.of("getFirst", "getSecond")) {
            Object bogey = call(bogeys, side);
            if (bogey == null) continue;
            Object points = field(bogey, "points");
            states.add(pointState(call(points, "getFirst")));
            states.add(pointState(call(points, "getSecond")));
        }
        return states.toString();
    }

    private static String targetPoints(Object data) throws Exception {
        StringJoiner states = new StringJoiner("|");
        for (Object point : (Object[]) field(data, "pointsToApproach")) states.add(pointState(point));
        return states.toString();
    }

    public static void searched(Object graph, Object current, Object target, float limit, boolean forward, float result) {
        if (!running) return;
        ApplyContext ctx = APPLY.get();
        if (ctx == null) return;
        try {
            int index = -1;
            Object[] points = (Object[]) field(ctx.data(), "pointsToApproach");
            for (int i = 0; i < points.length; i++) if (points[i] == target) index = i;
            String detail = "point=" + index + ";forward=" + forward + ";input_limit=" + limit
                + ";default_limit=" + (limit == -1 ? 32 : limit) + ";result=" + result
                + ";current=" + pointState(current) + ";target=" + pointState(target)
                + ";graph=" + field(graph, "id");
            sample(field(ctx.carriage(), "train"), ctx.carriage(), ctx.entity(), call(ctx.entity(), "level"),
                "client_search", field(ctx.entity(), "carriageIndex"), detail);
        } catch (Exception e) { error(e); }
    }

    public static void beginApproach(Object data) {
        if (!running) return;
        try { APPROACH_DELTA.set(((float[]) field(data, "pointDistanceSnapshot")).clone()); }
        catch (Exception e) { error(e); }
    }

    public static void endApproach(Object data, Object entity, Object carriage) {
        float[] before = APPROACH_DELTA.get();
        APPROACH_DELTA.remove();
        if (!running || before == null) return;
        try {
            float[] after = (float[]) field(data, "pointDistanceSnapshot");
            for (int i = 0; i < after.length; i++) {
                if (before[i] != 0 && after[i] == 0) {
                    sample(field(carriage, "train"), carriage, entity, call(entity, "level"), "client_follow_reset",
                        field(entity, "carriageIndex"), "point=" + i + ";previous_delta=" + before[i]
                            + ";age=" + field(data, "ticksSince") + ";points=" + carriagePoints(carriage)
                            + ";targets=" + targetPoints(data));
                }
            }
        } catch (Exception e) { error(e); }
    }

    public static void prepared(Object data, Object entity, Object carriage) {
        if (!running) return;
        SNAPSHOTS.put(data, new Context(entity, carriage));
        snapshot(data, entity, carriage, "server_snapshot");
    }

    public static void serialized(Object data) {
        if (!running) return;
        Context ctx = SNAPSHOTS.get(data);
        if (ctx != null) {
            arm();
            snapshot(data, ctx.entity(), ctx.carriage(), "server_serialize");
        }
    }

    public static void copied(Object source, Object copy) {
        if (!running || copy == null) return;
        Context ctx = SNAPSHOTS.get(source);
        if (ctx != null) SNAPSHOTS.put(copy, ctx);
    }

    private static void snapshot(Object data, Object entity, Object carriage, String phase) {
        try {
            sample(field(carriage, "train"), carriage, entity, call(entity, "level"), phase,
                field(entity, "carriageIndex"), "wheels=" + field(data, "wheelLocations")
                    + ";fallback=" + (field(data, "fallbackLocations") != null)
                    + ";points=" + carriagePoints(carriage));
        } catch (Exception e) { error(e); }
    }

    private static void sample(Object train, Object carriage, Object entity, Object level, String phase, Object index, String detail) throws Exception {
        if (!armed) return;
        Object nav = field(train, "navigation"), graph = field(train, "graph");
        enqueue(csv(System.currentTimeMillis(), System.nanoTime(), Thread.currentThread().getName(), phase,
            call(level, "getGameTime"), field(level, "isClientSide"), field(train, "id"), index,
            field(train, "speed"), field(train, "targetSpeed"), field(nav, "distanceToDestination"),
            entity == null ? "" : call(entity, "getX"), entity == null ? "" : call(entity, "getY"),
            entity == null ? "" : call(entity, "getZ"), entity == null ? "" : call(entity, "getYRot"),
            point(carriage, "getLeadingPoint", graph), point(carriage, "getTrailingPoint", graph), detail));
    }

    private static void view(Object level) throws Exception {
        long tick = ((Number) call(level, "getGameTime")).longValue();
        if (tick == viewTick) return;
        viewTick = tick;
        Object mc = Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance").invoke(null);
        Object player = field(mc, "player");
        Object camera = call(mc, "getCameraEntity");
        if (camera != null) enqueue(csv(System.currentTimeMillis(), System.nanoTime(), Thread.currentThread().getName(),
            "client_view", tick, true, "", "", "", "", "", call(camera, "getX"), call(camera, "getY"),
            call(camera, "getZ"), call(camera, "getYRot"), "", "", "player=" + (player == null ? "" : vector(call(player, "position")))));
    }
}
