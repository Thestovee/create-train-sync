// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package trainsyncfix;

/** Limits the accumulated fraction of one immutable interpolation snapshot. */
public final class FractionBudget {
    private double used;
    public void reset() { used = 0; }
    public void finish() { used = 1; }
    public float consume(float requested) {
        if (!Float.isFinite(requested) || requested <= 0) return 0;
        double amount = Math.min(requested, Math.max(0, 1 - used));
        used += amount;
        return (float) amount;
    }
}
