/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;

/**
 * BlockingSubmitter maintains multiple blocking queues such that each queue will be subject to all operations
 * performed on the BlockingSubmitter.
 *
 * This implementation IS NOT SAFE for concurrent/multi-threaded use! (It might not even be completely sound in
 * single-threaded use.)
 *
 * @param <E> The type of elements contained in the blocking queue.
 */
public final class BlockingSubmitter<E> {

    private final ArrayList<BlockingQueue<E>> queues;

    public BlockingSubmitter() {
        this.queues = new ArrayList<>();
    }

    public boolean addQueue(final BlockingQueue<E> queue) {
        return this.queues.add(requireNonNull(queue));
    }

    public boolean removeQueue(final BlockingQueue<E> queue) {
        return this.queues.remove(requireNonNull(queue));
    }

    public void add(final E e) {
        for (final BlockingQueue<E> queue : this.queues) {
            queue.add(e);
        }
    }

    public void addAll(final Collection<? extends E> c) {
        for (final BlockingQueue<E> queue : this.queues) {
            queue.addAll(c);
        }
    }

    public boolean offer(final E e) {
        BlockingQueue<E> failedQueue = null;
        for (final BlockingQueue<E> queue : this.queues) {
            if (!queue.offer(e)) {
                failedQueue = queue;
                break;
            }
        }
        if (failedQueue == null) {
            return true;
        }
        // failed to add element to every queue, reverting changes
        for (final BlockingQueue<E> queue : this.queues) {
            if (queue == failedQueue) {
                break;
            }
            // NOTE: strictly speaking I cannot be sure that it will not remove another occurrence (than the most
            // recently added) from the queue. That's acceptable for now, but may bite me in the future.
            queue.remove(e);
        }
        return false;
    }

    public boolean offer(final E e, final long timeout, final TimeUnit unit) throws InterruptedException {
        BlockingQueue<E> failedQueue = null;
        final long start = nanoTime();
        for (final BlockingQueue<E> queue : this.queues) {
            if (!queue.offer(e, timeout, unit)) {
                failedQueue = queue;
                break;
            }
            if (nanoTime() - start > unit.toNanos(timeout)) {
                throw new InterruptedException("Offering timed out while iterating over queues.");
            }
        }
        if (failedQueue == null) {
            return true;
        }
        // failed to add element to every queue, reverting changes
        for (final BlockingQueue<E> queue : this.queues) {
            if (queue == failedQueue) {
                break;
            }
            // NOTE: strictly speaking I cannot be sure that it will not remove another occurrence (than the most
            // recently added) from the queue. That's acceptable for now, but may bite me in the future.
            queue.remove(e);
        }
        return false;
    }

    public void put(final E e) throws InterruptedException {
        for (final BlockingQueue<E> queue : this.queues) {
            queue.put(e);
        }
    }
}
