package dorkbox.util.messagebus;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.util.VMSupport;

import dorkbox.util.messagebus.common.simpleq.jctools.Node;
import dorkbox.util.messagebus.common.simpleq.jctools.SimpleQueue;

public class SimpleQueueAltPerfTest {
    public static final int REPETITIONS = 50 * 1000 * 100;
    public static final Integer TEST_VALUE = Integer.valueOf(777);

    public static final int QUEUE_CAPACITY = 1 << 17;

    private static final int concurrency = 2;

    public static void main(final String[] args) throws Exception {
        System.out.println(VMSupport.vmDetails());
        System.out.println(ClassLayout.parseClass(Node.class).toPrintable());

        System.out.println("capacity:" + QUEUE_CAPACITY + " reps:" + REPETITIONS + "  Concurrency " + concurrency);

        final int warmupRuns = 2;
        final int runs = 5;

        long average = 0;

        final SimpleQueue queue = new SimpleQueue(QUEUE_CAPACITY);
        average = averageRun(warmupRuns, runs, queue);

//        SimpleQueue.INPROGRESS_SPINS = 64;
//        SimpleQueue.POP_SPINS = 512;
//        SimpleQueue.PUSH_SPINS = 512;
//        SimpleQueue.PARK_SPINS = 512;
//
//        for (int i = 128; i< 10000;i++) {
//            int full = 2*i;
//
//            final SimpleQueue queue = new SimpleQueue(QUEUE_CAPACITY);
//            SimpleQueue.PARK_SPINS = full;
//
//
//            long newAverage = averageRun(warmupRuns, runs, queue);
//            if (newAverage > average) {
//                average = newAverage;
//                System.err.println("Best value: " + i + "  : " + newAverage);
//            }
//        }


        System.out.format("summary,QueuePerfTest,%s %,d\n", queue.getClass().getSimpleName(), average);
    }

    private static long averageRun(int warmUpRuns, int sumCount, SimpleQueue queue) throws Exception {
        int runs = warmUpRuns + sumCount;
        final long[] results = new long[runs];
        for (int i = 0; i < runs; i++) {
            System.gc();
            results[i] = performanceRun(i, queue);
        }
        // only average last X results for summary
        long sum = 0;
        for (int i = warmUpRuns; i < runs; i++) {
            sum += results[i];
        }

        return sum/sumCount;
    }

    private static long performanceRun(int runNumber, SimpleQueue queue) throws Exception {

        Producer[] producers = new Producer[concurrency];
        Consumer[] consumers = new Consumer[concurrency];
        Thread[] threads = new Thread[concurrency*2];

        for (int i=0;i<concurrency;i++) {
            producers[i] = new Producer(queue);
            consumers[i] = new Consumer(queue);
        }

        for (int j=0,i=0;i<concurrency;i++,j+=2) {
            threads[j] = new Thread(producers[i], "Producer " + i);
            threads[j+1] = new Thread(consumers[i], "Consumer " + i);
        }

        for (int i=0;i<concurrency*2;i+=2) {
            threads[i].start();
            threads[i+1].start();
        }

        for (int i=0;i<concurrency*2;i+=2) {
            threads[i].join();
            threads[i+1].join();
        }

        long start = Long.MAX_VALUE;
        long end = -1;

        for (int i=0;i<concurrency;i++) {
            if (producers[i].start < start) {
                start = producers[i].start;
            }

            if (consumers[i].end > end) {
                end = consumers[i].end;
            }
        }


        long duration = end - start;
        long ops = REPETITIONS * 1_000_000_000L / duration;
        String qName = queue.getClass().getSimpleName();

        System.out.format("%d - ops/sec=%,d - %s\n", runNumber, ops, qName);
        return ops;
    }

    public static class Producer implements Runnable {
        private final SimpleQueue queue;
        volatile long start;

        public Producer(SimpleQueue queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            SimpleQueue producer = this.queue;
            int i = REPETITIONS;
            this.start = System.nanoTime();

            do {
                producer.transfer(TEST_VALUE);
            } while (0 != --i);
        }
    }

    public static class Consumer implements Runnable {
        private final SimpleQueue queue;
        Object result;
        volatile long end;

        public Consumer(SimpleQueue queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            SimpleQueue consumer = this.queue;
            Object result = null;
            int i = REPETITIONS;

            do {
                result = consumer.take();
            } while (0 != --i);

            this.result = result;
            this.end = System.nanoTime();
        }
    }
}
