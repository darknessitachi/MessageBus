/*
 * Copyright 2012 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.util.messagebus;

import java.util.concurrent.LinkedTransferQueue;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.util.VMSupport;

import dorkbox.util.messagebus.common.simpleq.jctools.Node;

public class LinkTransferQueuePerfTest {
    // 15 == 32 * 1024
    public static final int REPETITIONS = Integer.getInteger("reps", 50) * 1000 * 100;
    public static final Integer TEST_VALUE = Integer.valueOf(777);

    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 17);

    public static void main(final String[] args) throws Exception {
        System.out.println(VMSupport.vmDetails());
        System.out.println(ClassLayout.parseClass(Node.class).toPrintable());


        System.out.println("capacity:" + QUEUE_CAPACITY + " reps:" + REPETITIONS);

        final LinkedTransferQueue<Integer> queue = new LinkedTransferQueue<Integer>();

        final long[] results = new long[20];
        for (int i = 0; i < 20; i++) {
            System.gc();
            results[i] = performanceRun(i, queue);
        }
        // only average last 10 results for summary
        long sum = 0;
        for (int i = 10; i < 20; i++) {
            sum += results[i];
        }
        System.out.format("summary,QueuePerfTest,%s,%d\n", queue.getClass().getSimpleName(), sum / 10);
    }


    private static long performanceRun(int runNumber, LinkedTransferQueue<Integer> queue) throws Exception {
        Producer p = new Producer(queue);
        Thread thread = new Thread(p);
        thread.start(); // producer will timestamp start

        LinkedTransferQueue<Integer> consumer = queue;
        Object result;
        int i = REPETITIONS;
        int queueEmpty = 0;
        do {
            result = consumer.take();
        } while (0 != --i);
        long end = System.nanoTime();

        thread.join();
        long duration = end - p.start;
        long ops = REPETITIONS * 1000L * 1000L * 1000L / duration;
        String qName = queue.getClass().getSimpleName();
        System.out.format("%d - ops/sec=%,d - %s result=%d failed.poll=%d failed.offer=%d\n", runNumber, ops,
                qName, result, queueEmpty, p.queueFull);
        return ops;
    }

    public static class Producer implements Runnable {
        private final LinkedTransferQueue<Integer> queue;
        int queueFull = 0;
        long start;

        public Producer(LinkedTransferQueue<Integer> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            LinkedTransferQueue<Integer> producer = this.queue;
            int i = REPETITIONS;
            int f = 0;
            long s = System.nanoTime();

            try {
                do {
                    producer.transfer(TEST_VALUE);
                } while (0 != --i);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                // log.error(e);
            }
            this.queueFull = f;
            this.start = s;
        }
    }
}
