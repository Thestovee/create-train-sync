// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package trainsyncfix;
import java.util.*;

/** Invariant tests without starting Minecraft or a server. */
public final class RecoveryTest {
    public static final class Pair {
        Object first, second;
        Pair(Object a, Object b) { first=a; second=b; }
        public Object getFirst() { return first; }
        public Object getSecond() { return second; }
    }
    public static final class Node {
        int id;
        Node(int id) { this.id=id; }
        public int getNetId() { return id; }
    }
    public static final class Point {
        public Object node1, node2, edge;
        public double position;
        Point(Object a, Object b, Object e, double p) { node1=a;node2=b;edge=e;position=p; }
    }
    public static final class Bogey {
        public Pair points;
        Bogey(Point a, Point b) { points=new Pair(a,b); }
    }
    public static final class Carriage {
        public Pair bogeys;
    }
    public static final class Data {
        public Object fallbackLocations;
        public Point[] pointsToApproach;
        public List<Pair> wheelLocations;
        public float[] pointDistanceSnapshot;
        public float destinationDistanceSnapshot=5;
    }
    private record Fixture(Data data, Carriage carriage, Point[] actual) {}
    private static Fixture fixture(int count) {
        Node n1=new Node(1), n2=new Node(2); Object edge=new Object();
        Point[] actual=new Point[count], target=new Point[count];
        Data data=new Data();data.pointDistanceSnapshot=new float[4];data.wheelLocations=new ArrayList<>();
        for (int i=0;i<count;i++) {
            actual[i]=new Point(n1,n2,edge,20-i*2);
            target[i]=new Point(n1,n2,edge,60-i*2);
            data.wheelLocations.add(new Pair(new Pair(1,2),(float)target[i].position));
            data.pointDistanceSnapshot[i]=40;
        }
        data.pointsToApproach=target;
        Carriage carriage=new Carriage();
        carriage.bogeys=new Pair(new Bogey(actual[0],actual[1]),count==4?new Bogey(actual[2],actual[3]):null);
        return new Fixture(data,carriage,actual);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void close(double a,double b) { check(Math.abs(a-b)<1e-5,a+" != "+b); }

    public static void main(String[] args) throws Exception {
        // Delayed snapshots must never advance past a total interpolation fraction of one.
        FractionBudget budget=new FractionBudget();double position=0;
        for (int i=0;i<50;i++) position+=3*budget.consume(1f/3);
        close(position,3);
        budget.reset();close(budget.consume(2),1);close(budget.consume(.2f),0);
        budget.reset();close(budget.consume(Float.NaN),0);close(budget.consume(-1),0);
        close(budget.consume(.25f),.25);close(budget.consume(.25f),.25);close(budget.consume(1),.5);
        budget.reset();close(budget.consume(.1f),.1);

        // Reproduce mixed fallback: front point snaps, the remaining three still carry deltas.
        Fixture f=fixture(4);Recovery.Point[] previous=Recovery.capture(f.carriage);
        f.actual[0].position=60;f.data.pointDistanceSnapshot[0]=0;
        check(Recovery.afterApply(f.data,f.carriage,previous),"mixed apply must recover the entire carriage");
        for(int i=0;i<4;i++){close(f.actual[i].position,60-i*2);close(f.data.pointDistanceSnapshot[i],0);}
        close(f.data.destinationDistanceSnapshot,0);

        // A stale target from an earlier packet must not be copied into any other point.
        f=fixture(4);f.data.pointsToApproach[3].position=-100;
        check(!Recovery.recover(f.data,f.carriage),"stale target must be rejected");
        for(int i=0;i<4;i++) close(f.actual[i].position,20-i*2);

        f=fixture(4);f.data.pointsToApproach[3]=null;
        check(!Recovery.recover(f.data,f.carriage),"missing target must be rejected before commit");
        for(int i=0;i<4;i++) close(f.actual[i].position,20-i*2);

        // Reused graph node identifiers must agree with the current packet.
        f=fixture(4);f.data.pointsToApproach[2].node1=new Node(99);
        check(!Recovery.recover(f.data,f.carriage),"wrong node must be rejected before commit");
        for(int i=0;i<4;i++) close(f.actual[i].position,20-i*2);

        // Ordinary interpolation must stay untouched.
        f=fixture(4);previous=Recovery.capture(f.carriage);
        check(!Recovery.afterApply(f.data,f.carriage,previous),"normal apply should not recover");
        close(f.actual[0].position,20);

        // Follow failure, including one-bogey carriages.
        f=fixture(2);float[] deltas=Recovery.deltas(f.data);f.data.pointDistanceSnapshot[1]=0;
        check(Recovery.afterApproach(f.data,f.carriage,deltas),"follow failure must recover both points");
        close(f.actual[0].position,60);close(f.actual[1].position,58);

        // Non-track fallback and explicit disable must retain the original behavior.
        f=fixture(4);f.data.fallbackLocations=new Object();
        check(!Recovery.recover(f.data,f.carriage),"fallback vectors are outside geometry recovery");
        System.setProperty("trainsyncfix.enabled","false");
        check(Recovery.capture(f.carriage)==null,"disabled patch must not capture");
        System.clearProperty("trainsyncfix.enabled");
        System.out.println("PASS: delayed interpolation, atomic fallback, stale/missing target rejection, normal path, one bogey, disable");
    }
}
