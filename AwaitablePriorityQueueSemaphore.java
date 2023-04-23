/*
Name: Yoni
Reviewer: guy
Description: awaitablepriorityqueue
*/

package il.co.ilrd.awaitablepriorityqueue;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AwaitablePriorityQueueSemaphore<E>{
    private PriorityQueue<E> priorityQueue;
    private Lock lock;
    private Semaphore semaphoreDequeue;
    private Semaphore semaphoreEnqueue;
    private int capacity;
    private final static int DEFAULT_CAPACITY = 11;
    /*
     The default initial capacity of a PriorityQueue in Java is 11 because it is the smallest power of two that is
      greater than or equal to the default load factor of 0.75. This ensures that the PriorityQueue is able to
      store elements efficiently without having to resize the underlying array.
     */
    private Comparator comparator;

    public AwaitablePriorityQueueSemaphore(int capacity, Comparator<E> comparator) {
        capacity = capacity > DEFAULT_CAPACITY ? capacity : DEFAULT_CAPACITY;
        this.comparator = comparator;
        priorityQueue = new PriorityQueue<>(capacity, comparator);
        semaphoreDequeue = new Semaphore(0);
        semaphoreEnqueue = new Semaphore(capacity);
        lock = new ReentrantLock();
    }

    public AwaitablePriorityQueueSemaphore(Comparator<E> comparator) {
        this(DEFAULT_CAPACITY,comparator);
    }
    /*
    The elements of the priority queue are ordered according to their natural ordering, or by a Comparator provided
     at queue construction time, depending on which constructor is used.
     */
    public AwaitablePriorityQueueSemaphore(int capacity) {
        this(capacity, null);
    }

    public AwaitablePriorityQueueSemaphore() {
        this(DEFAULT_CAPACITY,null);
    }

    public void enqueue(E element) throws InterruptedException {
        semaphoreEnqueue.acquire();
        lock.lock();
        try {
            priorityQueue.add(element);
        }catch (RuntimeException e){
            semaphoreEnqueue.release();
            e.printStackTrace();
            throw new  RuntimeException();
        }
        finally {
            lock.unlock();
        }
        semaphoreDequeue.release();
    }

    public E dequeue() throws InterruptedException {
        E element;
        semaphoreDequeue.acquire();
        lock.lock();
        try {
            element = priorityQueue.poll();
        }
        catch (RuntimeException e){
            semaphoreDequeue.release();
            e.printStackTrace();
            throw new  RuntimeException();
        }
        finally {
            lock.unlock();
        }
        semaphoreEnqueue.release();
        return element;
    }

    public boolean remove(E element) throws InterruptedException {
        boolean isRemoved = false;

        if(semaphoreDequeue.tryAcquire()){
            lock.lock();
            try {
                isRemoved = priorityQueue.remove(element);
            }catch (RuntimeException e){
                semaphoreDequeue.release();
                e.printStackTrace();
                throw new  RuntimeException();
            }
            finally {
                lock.unlock();
            }
            if(isRemoved){
                semaphoreEnqueue.release();
            }
            else {
                semaphoreDequeue.release();
            }
        }
        return isRemoved;
    }

    public boolean isEmpty() {
        return priorityQueue.isEmpty();
    }

    public int size() {
        return priorityQueue.size();
    }
}