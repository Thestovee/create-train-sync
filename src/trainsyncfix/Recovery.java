// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package trainsyncfix;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Client-only bridge; never modifies the track graph or server simulation. */
public final class Recovery {
    private static final ConcurrentMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private static int warnings;
    public static boolean enabled() { return Boolean.parseBoolean(System.getProperty("trainsyncfix.enabled", "true")); }
    public static boolean recoverEnabled() { return enabled() && Boolean.parseBoolean(System.getProperty("trainsyncfix.recovery", "true")); }
    public static boolean capEnabled() { return enabled() && Boolean.parseBoolean(System.getProperty("trainsyncfix.cap", "true")); }

    public static Object get(Object object, String name) throws ReflectiveOperationException {
        return member(object.getClass(), name).get(object);
    }
    private static Field member(Class<?> type, String name) throws ReflectiveOperationException {
        String key = type.getName() + "#" + name;
        Field cached = FIELDS.get(key);
        if (cached != null) return cached;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true); FIELDS.putIfAbsent(key, f); return f;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(key);
    }
    private static void set(Object object, String name, Object value) throws ReflectiveOperationException {
        member(object.getClass(), name).set(object, value);
    }
    private static Object call(Object object, String name) throws ReflectiveOperationException {
        String key = object.getClass().getName() + "#" + name;
        Method m = METHODS.get(key);
        if (m == null) {
            m = object.getClass().getMethod(name); m.setAccessible(true); METHODS.putIfAbsent(key, m);
        }
        return m.invoke(object);
    }

    public record Point(Object node1, Object node2, Object edge, double position) {
        static Point read(Object point) throws ReflectiveOperationException {
            return new Point(get(point, "node1"), get(point, "node2"), get(point, "edge"), ((Number)get(point, "position")).doubleValue());
        }
        boolean changed(Object point) throws ReflectiveOperationException {
            return node1 != get(point, "node1") || node2 != get(point, "node2") || edge != get(point, "edge")
                || Math.abs(position - ((Number)get(point, "position")).doubleValue()) > 1e-6;
        }
        void write(Object point) throws ReflectiveOperationException {
            set(point, "node1", node1); set(point, "node2", node2); set(point, "edge", edge); set(point, "position", position);
        }
    }

    private static List<Object> points(Object carriage) throws ReflectiveOperationException {
        Object bogeys = get(carriage, "bogeys");
        List<Object> points = new ArrayList<>(4);
        for (String side : List.of("getFirst", "getSecond")) {
            Object bogey = call(bogeys, side);
            if (bogey == null) continue;
            Object pair = get(bogey, "points");
            points.add(call(pair, "getFirst")); points.add(call(pair, "getSecond"));
        }
        return points;
    }

    public static Point[] capture(Object carriage) {
        if (!recoverEnabled()) return null;
        try {
            List<Object> points = points(carriage);
            Point[] result = new Point[points.size()];
            for (int i = 0; i < result.length; i++) result[i] = Point.read(points.get(i));
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) { warn(e); return null; }
    }

    /** A zero delta plus a changed point means apply's immediate fallback, not normal interpolation. */
    public static boolean afterApply(Object data, Object carriage, Point[] before) {
        if (!recoverEnabled() || before == null) return false;
        try {
            List<Object> actual = points(carriage);
            float[] delta = (float[])get(data, "pointDistanceSnapshot");
            if (actual.size() != before.length) return false;
            boolean snapped = false;
            for (int i = 0; i < before.length; i++)
                if (delta[i] == 0 && before[i].changed(actual.get(i))) snapped = true;
            return snapped && recover(data, carriage);
        } catch (ReflectiveOperationException | RuntimeException e) { warn(e); return false; }
    }

    public static float[] deltas(Object data) {
        if (!recoverEnabled()) return null;
        try { return ((float[])get(data, "pointDistanceSnapshot")).clone(); }
        catch (ReflectiveOperationException | RuntimeException e) { warn(e); return null; }
    }

    public static boolean afterApproach(Object data, Object carriage, float[] before) {
        if (!recoverEnabled() || before == null) return false;
        try {
            float[] after = (float[])get(data, "pointDistanceSnapshot");
            for (int i = 0; i < before.length; i++)
                if (before[i] != 0 && after[i] == 0) return recover(data, carriage);
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) { warn(e); return false; }
    }

    /** Validate ALL targets against this packet before writing ANY target. */
    static boolean recover(Object data, Object carriage) throws ReflectiveOperationException {
        if (get(data, "fallbackLocations") != null) return false;
        List<Object> actual = points(carriage);
        Object[] targets = (Object[])get(data, "pointsToApproach");
        List<?> locations = (List<?>)get(data, "wheelLocations");
        if ((actual.size() != 2 && actual.size() != 4) || targets.length < actual.size() || locations.size() < actual.size()) return false;
        Point[] valid = new Point[actual.size()];
        for (int i = 0; i < valid.length; i++) {
            if (targets[i] == null || locations.get(i) == null) return false;
            Point target = Point.read(targets[i]);
            if (target.node1 == null || target.node2 == null || target.edge == null || !Double.isFinite(target.position)) return false;
            Object ids = call(locations.get(i), "getFirst");
            if (!Objects.equals(call(target.node1, "getNetId"), call(ids, "getFirst"))
                || !Objects.equals(call(target.node2, "getNetId"), call(ids, "getSecond"))) return false;
            double packetPosition = ((Number)call(locations.get(i), "getSecond")).doubleValue();
            if (!Double.isFinite(packetPosition) || Math.abs(target.position - packetPosition) > 1e-4) return false;
            valid[i] = target;
        }
        // Resolve writable members before the commit, so reflection lookup failures cannot partially commit.
        for (Object point : actual) for (String name : List.of("node1", "node2", "edge", "position")) member(point.getClass(), name);
        float[] delta = (float[])get(data, "pointDistanceSnapshot");
        if (delta.length < actual.size()) return false;
        member(data.getClass(), "destinationDistanceSnapshot");
        for (int i = 0; i < valid.length; i++) valid[i].write(actual.get(i));
        Arrays.fill(delta, 0);
        set(data, "destinationDistanceSnapshot", 0f);
        return true;
    }

    private static synchronized void warn(Exception e) {
        if (warnings++ < 5) System.err.println("[TrainSyncFix] Recovery skipped: " + e);
    }
}
