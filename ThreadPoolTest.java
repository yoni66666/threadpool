package il.co.ilrd.threadpool;
import java.util.concurrent.*;

import il.co.ilrd.factory.Factory;
import il.co.ilrd.factory.FactoryTest;
import org.junit.Test;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;

public class ThreadPoolTest {
        ThreadPool pool = new ThreadPool();

        Callable<String> task1 = new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.err.println("task1 from MEDIUM thread "+Thread.currentThread().getId());
                return "task1 running from "+Thread.currentThread().getName();
            }
        };

        Callable<String> task2 = new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.err.println("task2 HIGH running from "+Thread.currentThread().getName());
                return "task2 running from "+Thread.currentThread().getName();
            }
        };

        Runnable task3 = new Runnable() {
            @Override
            public void run() {
                System.err.println("im from Runnable = MEDIUM");
            }
        };

    Callable<String> task4 = new Callable<String>() {
        @Override
        public String call() throws Exception {
            System.err.println("start Task");
            for (int i = 0; i < 1000;++i){
            }

            for (int i = 0; i < 100;++i){
                System.err.println(i);
            }
            //sleep(100);
            System.err.println("task4 HIGH running from "+Thread.currentThread().getName());
            return "task2 running from "+Thread.currentThread().getName();
        }
    };
    @Test
    public void submit() throws InterruptedException {
        for (int i = 0; i < 3; ++i){
            Future<String> result1 = pool.submit(task1, ThreadPool.TaskPriority.MEDIUM);
            Future<String> result2 = pool.submit(task2, ThreadPool.TaskPriority.HIGH);
            //System.out.println(result1);
            //System.out.println(result2);
            pool.execute(task3);
        }
    }
    @Test
    public void pause() throws InterruptedException {
            Future<String> result1 = pool.submit(task4, ThreadPool.TaskPriority.MEDIUM);
            pool.pause();

            sleep(3000);
            System.out.println("resume");
            pool.resume();
           // sleep(1000);
        }

    @Test
    public void shutdown() throws InterruptedException {
        Future<String> result1 = pool.submit(task4, ThreadPool.TaskPriority.MEDIUM);
        pool.shutdown();
        sleep(1000);
        Future<String> result2 = pool.submit(task2, ThreadPool.TaskPriority.MEDIUM);
        System.err.println(result2);
        sleep(1000);
    }
    @Test
    public void shutdownAndPause() throws InterruptedException {
        Future<String> result1 = pool.submit(task4, ThreadPool.TaskPriority.MEDIUM);
        pool.pause();
        pool.shutdown();
        sleep(1000);
        Future<String> result2 = pool.submit(task2, ThreadPool.TaskPriority.MEDIUM);
        System.err.println(result2);
        sleep(1000);
    }
    @Test
    public void setNumberOfThreads() throws InterruptedException {
        /* fix - not right*/
        int j = 0;
        pool.setNumberOfThreads(5);
        CountDownLatch latch = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            pool.execute(latch::countDown);
            System.err.println(j++);
        }
        latch.await();
        assertEquals(0, latch.getCount());
        sleep(2000);
    }

    @Test
    public void Future() throws InterruptedException {
        Future<String> result = pool.submit(task4, ThreadPool.TaskPriority.MEDIUM);
        System.err.println("is done = " + result.isDone());
        sleep(2000);
        System.err.println("is done = " + result.isDone());
    }

    @Test
    public void FutureCancel() throws InterruptedException {
        Future<String> result = pool.submit(task4, ThreadPool.TaskPriority.MEDIUM);
        //sleep(1000);
        result.cancel(true);
        System.err.println("is cancel = " + result.isCancelled());
        sleep(2000);
        System.err.println("is cancel = " + result.isCancelled());
    }

    @Test
    public void FutureGet() throws InterruptedException, ExecutionException, TimeoutException {
        Future<String> result = pool.submit(task4, ThreadPool.TaskPriority.MEDIUM);
        //sleep(1000);
        System.err.println("check");
        String s =  result.get(1, TimeUnit.SECONDS );
        System.err.println("get = " + s);
        sleep(2000);
    }
}
