/**
 * This file is part of CLaJ. The system that allows you to play with your friends,
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2026  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xpdustry.claj.common.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public final class ByteBufferPool {
  private static final ByteBufferPool INSTANCE = new ByteBufferPool();

  public static ByteBufferPool get() {
    return INSTANCE;
  }

  public static ByteBuffer getHeap(int size) {
    return get().obtain(size, false);
  }

  public static ByteBuffer getDirect(int size) {
    return get().obtain(size, true);
  }

  public static boolean free(ByteBuffer buff) {
    return get().release(buff);
  }


  /** Optimization to avoid a bucket with only empty buffers. */
  private static final ByteBuffer EMPTY_HEAP = ByteBuffer.allocate(0),
                                  EMPTY_DIRECT = ByteBuffer.allocateDirect(0);

  protected final ConcurrentHashMap<Integer, Pool<ByteBuffer>> heaps, directs;
  protected final Function<Integer, Pool<ByteBuffer>> newBucket;
  public final int factor, bucketCap;

  /** Creates a buffer pool with a default {@link #factor} and {@link #bucketCap} of {@code 1024}. */
  public ByteBufferPool() {
    this(1024, 1024);
  }

  public ByteBufferPool(int factor, int bucketCap) {
    this.heaps = new ConcurrentHashMap<>(8);
    this.directs = new ConcurrentHashMap<>(8);
    this.factor = factor;
    this.bucketCap = bucketCap;
    this.newBucket = _ -> new Pool<>(() -> null); // Buffer allocation is handled else where
  }

  protected ConcurrentHashMap<Integer, Pool<ByteBuffer>> getBuckets(boolean direct) {
    return direct ? directs : heaps;
  }

  protected ByteBuffer newBuffer(boolean direct, int capacity) {
    return direct ? ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder()) : ByteBuffer.allocate(capacity);
  }

  /** @return the bucket fitting the given {@code size} according to the {@link #factor}. */
  public int bucketOf(int size) {
    return (size + factor - 1) / factor;
  }

  public ByteBuffer obtain(int size) {
    return obtain(size, false);
  }

  /**
   * Get a buffer of the given {@code size} from free pool, or a new one. <br>
   * Capacity is rounded to the upper {@link #factor}, but limited to the given {@code size}.
   */
  public ByteBuffer obtain(int size, boolean direct) {
    if (size <= 0) return direct ? EMPTY_DIRECT : EMPTY_HEAP;
    int bucket = bucketOf(size);
    Pool<ByteBuffer> pool = getBuckets(direct).get(bucket);
    ByteBuffer buf = null;
    if (pool != null) {
      buf = pool.poll();
      if (buf != null) buf = (ByteBuffer)buf.clear();
    }
    if (buf == null) buf = newBuffer(direct, bucket * factor);
    return (ByteBuffer)buf.limit(size);
  }

  /**
   * Double release protection not handled!
   * @return whether the buffer was added to the free buffer pool.
   *         A buffer might not be added for several reason: a {@code null} value, zero capacity,
   *         not at the defined {@link #factor}, or simply because the associated bucket is full.
   */
  public boolean release(ByteBuffer buf) {
    if (buf == null) return false;
    int capaticy = buf.capacity();
    if (capaticy <= 0 || capaticy % factor != 0) return false;
    return getBuckets(buf.isDirect()).computeIfAbsent(capaticy / factor, newBucket).offer(buf);
  }

  /** Fill a {@code bucket} completely. */
  public void fill(int bucket) {
    fill(bucket, bucketCap, false);
  }

  public void fill(int bucket, int size) {
    fill(bucket, size, false);
  }

  /** Fill a {@code bucket} with {@code size} new buffers. */
  public void fill(int bucket, int size, boolean direct) {
    if (size <= 0 || bucket <= 0) return;
    int capacity = bucket * factor;
    Pool<ByteBuffer> b = getBuckets(direct).computeIfAbsent(bucket, newBucket);
    for (int i=0; i<size; i++) {
      if (!b.offer(newBuffer(direct, capacity))) return;
    }
  }

  /** Clear all buffer buckets. */
  public void clear() {
    heaps.clear();
    directs.clear();
  }

  public void clear(boolean direct) {
    getBuckets(direct).clear();
  }

  public int size(int bucket) {
    return size(bucket, false);
  }

  /** @return the number of currently freed buffers in a bucket,
   *          or {@code -1} if no pool has been created for this bucket. */
  public int size(int bucket, boolean direct) {
    if (bucket <= 0) return -1;
    Pool<ByteBuffer> b = getBuckets(direct).get(bucket);
    return b == null ? -1 : b.getFree();
  }

  public boolean has(int bucket) {
    return has(bucket, false);
  }

  /** @return whether a pool has been created for the specified {@code bucket}. */
  public boolean has(int bucket, boolean direct) {
    return bucket > 0 && getBuckets(direct).containsKey(bucket);
  }
}
