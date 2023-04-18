/**
 * Name: jonathan
 * Reviewer: Bar b
 * Exercise: Thread Pool
 */

package il.co.ilrd.threadpool;

import il.co.ilrd.awaitablepriorityqueue.AwaitablePriorityQueueSemaphore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.Objects.requireNonNull;

public class ThreadPool implements Executor {
    private final AwaitablePriorityQueueSemaphore<Task<?>> pQueue;
    private final Semaphore semForPause = new Semaphore(0);
    private CountDownLatch latch;
    private List<PoolThread> threadList;
    private AtomicInteger numOfThread = new AtomicInteger(0);
    private final int SUPER_HIGH_PRIOR = TaskPriority.HIGH.ordinal() + 1;
    private final int SUPER_LOW_PRIOR = TaskPriority.LOW.ordinal() - 1;
    private static final int DEFAULT_NUM_OF_THREADS = 2 * Runtime.getRuntime().availableProcessors();
    SpecialTask specialTask = new SpecialTask();
    private boolean isThreadShutDown = false;
    private boolean isPause = false;


    public ThreadPool() {
        this(DEFAULT_NUM_OF_THREADS);
    }

    public ThreadPool(int numOfThreads) {
        if (numOfThreads < 1){
            throw  new IllegalArgumentException();
        }
        numOfThread.set(numOfThreads);
        pQueue = new AwaitablePriorityQueueSemaphore<>();
        threadList = new ArrayList<>();
        addThreadToPoolAndRun(numOfThread.get());
    }

    private void addThreadToPoolAndRun(int numOfThreadToAdd) {
        for (int i = 0; i < numOfThreadToAdd; ++i) {
            PoolThread threadForPool = new PoolThread();
            threadList.add(threadForPool);
            threadForPool.start();
        }
    }

    public Future<Void> submit(Runnable runnable) throws InterruptedException {
        return submit(Executors.callable(requireNonNull(runnable), null), TaskPriority.MEDIUM);
    }

    public <T> Future<T> submit(Runnable runnable, TaskPriority priority, T result) throws InterruptedException {
        return submit(Executors.callable(requireNonNull(runnable), result), priority);
    }

    public <T> Future<T> submit(Runnable runnable, TaskPriority priority) throws InterruptedException {
        return submit(Executors.callable(requireNonNull(runnable), null), priority);
    }

    public <T> Future<T> submit(Callable<T> callable) throws InterruptedException {
        return submit(requireNonNull(callable), TaskPriority.MEDIUM);
    }

    public <T> Future<T> submit(Callable<T> callable, TaskPriority priority) throws InterruptedException {
        if(isThreadShutDown){
            throw new RejectedExecutionException();
        }

        Task<T> task = new Task<T>(requireNonNull(callable), priority.ordinal());

        pQueue.enqueue(task);
        return task.getFuture();
    }

    @Override
    public void execute(Runnable runnable) {
        try {
            submit(requireNonNull(runnable));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeThreadFromPool(int numOfThreadToRemove) throws InterruptedException {
        for (int i = 0; i < numOfThreadToRemove; ++i) {
            pQueue.enqueue(new Task<>(specialTask.taskToStop, SUPER_HIGH_PRIOR));
        }
    }

    public void setNumberOfThreads(int numThreadsToSet) throws InterruptedException {
        if (numThreadsToSet < 1){
            throw new IllegalArgumentException();
        }
        if(isThreadShutDown) {
            throw new IllegalStateException();
        }
        int diff = numThreadsToSet - numOfThread.get();
        if (diff > 0) {
            addThreadToPoolAndRun(diff);
        } else if (diff < 0) {
            removeThreadFromPool(Math.abs(diff));
        }
        numOfThread.set(numThreadsToSet);
    }

    public void pause() throws InterruptedException {
        if (isThreadShutDown){
            return;
        }
        for (int i = 0; i < numOfThread.get(); ++i) {
            pQueue.enqueue(new Task<>(new SpecialTask().taskToPause , SUPER_HIGH_PRIOR));
        }
        isPause = true;
    }

    public void resume() throws InterruptedException {
        if (isThreadShutDown){
            return;
        }
        semForPause.release(numOfThread.get());
    }

    public void shutdown() throws InterruptedException {
        if(!isThreadShutDown){
            if (isPause){
                resume();
            }
            int currentNumOfThread = numOfThread.get();
            latch = new CountDownLatch(currentNumOfThread);
            isThreadShutDown = true;

            for (int i = 0; i < currentNumOfThread; ++i) {
                pQueue.enqueue(new Task<>(specialTask.taskToStop, SUPER_LOW_PRIOR));
            }
        }
    }

    public void awaitTermination() throws InterruptedException {
        awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if(!isThreadShutDown){
            return false;
        }
        return latch.await(timeout, unit);
    }

    public enum TaskPriority {
        LOW, MEDIUM, HIGH;
    }

    private class PoolThread extends Thread {
        private boolean isRunning = false;

        @Override
        public void run() {
            isRunning = true;
            while (isRunning) {
                Task<?> task = null;
                try {
                    task = pQueue.dequeue();
                    task.runTask();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (isThreadShutDown){
                    latch.countDown();
                }
            }
        }
    }

    private class Task<T> implements Comparable<Task<?>> {
        private final Callable<T> callable;
        private final int priority;
        private Future<T> future;
        private T resultFromCallable;
        private volatile boolean  isDoneFlags = false;
        private final Semaphore semTaskIsFinished = new Semaphore(0);
        private Exception executionException;

        private Task(Callable<T> callable, int priority) {
            this.priority = priority;
            this.callable = requireNonNull(callable);
            future = new TaskFuture();
        }

        @Override
        public int compareTo(Task<?> t) {
            return t.priority - this.priority;
        }

        public Future<T> getFuture() {
            return future;
        }

        private void runTask() {
            try {
                resultFromCallable = callable.call();
            } catch (Exception e) {
                executionException = e;
            }
            finally {
                isDoneFlags = true;
                semTaskIsFinished.release();
            }
        }

        private class TaskFuture implements Future<T> {
            private boolean isCancelled = false;

            @Override
            public boolean cancel(boolean b) {
                boolean isRemoved = false;
                if (isDone() || isCancelled){
                    return false;
                }
                try {
                    isRemoved = pQueue.remove(Task.this);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if(isRemoved){
                   isDoneFlags = true;
                   isCancelled = true;
                   semTaskIsFinished.release();
                }
                return isCancelled;
            }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public boolean isDone() {
                return isDoneFlags;
            }
            @Override
            public T get() throws InterruptedException, ExecutionException {
                try {
                    return get(Long.MAX_VALUE, TimeUnit.DAYS);
                } catch (TimeoutException e) {
                    throw new ExecutionException(e);
                }
            }

            @Override
            public T get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                if (semTaskIsFinished.tryAcquire(timeout, timeUnit) == false){
                    throw new TimeoutException();
                }

                if(isCancelled()){
                    throw new CancellationException();
                }
                if (executionException != null){
                    throw new ExecutionException(executionException);
                }
                return resultFromCallable;
            }
        }
    }

    private class SpecialTask {
        Callable<String> taskToPause = new Callable<String>() {
            @Override
            public String call() throws Exception {
                semForPause.acquire();
                return null;
            }
        };

        Callable<String> taskToStop = new Callable<String>() {
            @Override
            public String call() throws Exception {
                ((PoolThread)Thread.currentThread()).isRunning = false;
                numOfThread.incrementAndGet();
                return null;
            }
        };
    }
}
