import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Benchmark {
    public static void main(String[] args) {
        System.out.println("Warmup...");
        for (int i = 0; i < 5; i++) {
            runArrayList();
            runArrayDeque();
        }

        System.out.println("Benchmarking...");
        long alTime = 0;
        long adTime = 0;
        for (int i = 0; i < 10; i++) {
            alTime += runArrayList();
            adTime += runArrayDeque();
        }

        System.out.println("ArrayList Time: " + (alTime / 10) + "ms");
        System.out.println("ArrayDeque Time: " + (adTime / 10) + "ms");
    }

    private static long runArrayList() {
        List<Long> list = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            list.add((long) i);
            final long now = i;
            list.removeIf(time -> now - time > 100);
        }
        return System.currentTimeMillis() - start;
    }

    private static long runArrayDeque() {
        Deque<Long> deque = new ArrayDeque<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            deque.add((long) i);
            long now = i;
            while (!deque.isEmpty() && now - deque.peekFirst() > 100) {
                deque.pollFirst();
            }
        }
        return System.currentTimeMillis() - start;
    }
}
