package com.xpdustry.claj.common.util;

import java.util.concurrent.atomic.*;

import arc.func.Prov;
import arc.struct.Seq;

/**
 * Taken from arc-lite.
 * <p>
 * A pool of objects that can be reused to avoid allocation.
 * The implementation is thread-safe, lock-less, and pool array is preallocated.
 * @author Nathan Sweet
 * @see Pools
 */
public class Pool<T> extends arc.util.pooling.Pool<T>{
    /** The maximum number of objects that can be pooled. */
    public final int capacity;
    private final AtomicReferenceArray<T> pool;
    private final AtomicInteger head, tail;
    private final Prov<T> newObject;
    /** The highest number of free objects. Can be reset any time. */
    public volatile int peak;

    /** Creates a pool with a maximum capacity of {@code 2048} objects. */
    public Pool(Prov<T> newObject){
        this(2048, newObject);
    }

    /** @param capacity The maximum number of free objects to store in this pool. */
    public Pool(int capacity, Prov<T> newObject){
      this.capacity = capacity;
      this.newObject = newObject;
      pool = new AtomicReferenceArray<>(capacity);
      head = new AtomicInteger(0);
      tail = new AtomicInteger(0);
    }

    @Override
    protected T newObject() {
      return newObject.get();
    }

    /**
     * Returns an object from this pool.
     * The object may be new (from {@link #newObject()}) or
     * reused (previously {@link #free(Object) freed}).
     */
    public T obtain(){
        T o = poll();
        return o == null ? newObject() : o;
    }

    /** Same as {@link #obtain()}, but returns {@code null} instead of allocating a new object if the pool is empty. */
    public T poll(){
        for(;;){
            int h = head.get();
            int t = tail.get();
            if(h == t) return null; // Pool empty
            T e = pool.get(h);
            if(e != null && head.compareAndSet(h, (h + 1) % capacity)){
                pool.set(h, null); // Help GC
                return e;
            }
            // Lost CAS race or slot is in mid-writing, re-read
        }
    }

    /**
     * Puts the specified object in the pool, making it eligible to be returned by {@link #obtain()}.
     * If the pool already contains {@link #max} free objects, the specified object is reset but not added to the pool.
     * <p>
     * The pool does not check if an object is already freed, so the same object must not be freed multiple times.
     */
    public void free(T object){
        offer(object);
    }

    /**
     * Same as {@link #free()}, but returns {@code true} if the object has been placed to the pool array.
     * Else it means that the pool is full.
     */
    public boolean offer(T object){
        if(object == null) throw new IllegalArgumentException("object cannot be null.");
        for(;;){
            int h = head.get();
            int t = tail.get();
            int i = (t + 1) % capacity;
            if(i == h) return false; // Pool full
            if(tail.compareAndSet(t, i)){
                peak = Math.max(peak, (t - h + capacity) % capacity);
                reset(object);
                pool.set(t, object);
                return true;
            }
            // Lost CAS race, re-read
        }
    }

    /**
     * Called when an object is freed to clear the state of the object for possible later reuse.
     * The default implementation calls {@link Poolable#reset()} if the object is {@link Poolable}.
     */
    protected void reset(T object){
        if(object instanceof Poolable) ((Poolable)object).reset();
    }

    /**
     * Puts the specified objects in the pool. Null objects within the array are silently ignored.
     * <p>
     * The pool does not check if an object is already freed, so the same object must not be freed multiple times.
     * @see #free(Object)
     */
    public void freeAll(Seq<T> objects){
        if(objects == null) throw new IllegalArgumentException("objects cannot be null.");
        for(int i = 0; i < objects.size; i++){
            T object = objects.get(i);
            if(object == null) continue;
            if(!offer(object)) reset(object);
        }
    }

    /** Fill the pool with new objects. */
    public void fill(){
        fill(capacity);
    }

    /** Fill the pool with {@code size} new objects. */
    public void fill(int size){
        for(int i = 0; i < size; i++){
            if(!offer(newObject())) return;
        }
    }

    /** Removes all free objects from this pool. */
    public void clear(){
        while(poll() != null);
    }

    /**
     * The number of objects available to be obtained.
     * This can be an estimation if pool is highly used.
     */
    public int getFree(){
      return (tail.get() - head.get() + capacity) % capacity;
    }
}
