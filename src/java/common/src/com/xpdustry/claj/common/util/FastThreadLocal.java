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

import java.util.concurrent.atomic.*;

import arc.func.Prov;


/** {@link ThreadLocal} with MRU fast path. */
public class FastThreadLocal<T> extends ThreadLocal<T> {
  public static <T> FastThreadLocal<T> with(Prov<T> supplier) {
    return new FastThreadLocal<>(supplier);
  }

  @SuppressWarnings("rawtypes")
  private static final AtomicLongFieldUpdater<FastThreadLocal> UPDATER =
      AtomicLongFieldUpdater.newUpdater(FastThreadLocal.class, "last");
  private static final long EMPTY = -1L, UPDATING = -2L;

  private volatile long last = EMPTY;
  private volatile T data;
  private final Prov<T> supplier;

  public FastThreadLocal() { this(null); }
  public FastThreadLocal(Prov<T> supplier) {
    this.supplier = supplier;
  }

  @Override
  protected T initialValue() {
    return supplier == null ? null : supplier.get();
  }

  @Override
  public T get() {
    long current = getCurrent();
    if (last == current) {
      T val = data;
      if (last == current) return val; // safe check
    }
    T val = super.get(); // cache missed
    tryUpdate(current, val);
    return val;
  }

  @Override
  public void set(T value) {
    long current = getCurrent();
    super.set(value);
    tryUpdate(current, value);
  }

  @Override
  public void remove() {
    long current = getCurrent();
    super.remove();
    tryRemove(current);
  }

  @SuppressWarnings("deprecation")
  private long getCurrent() {
    return Thread.currentThread().getId();
  }

  private void tryUpdate(long current, T val) {
    long currentOwner = last;
    if (currentOwner == UPDATING) return;
    if (!UPDATER.compareAndSet(this, currentOwner, UPDATING)) return;
    data = val;
    last = current;
  }

  private void tryRemove(long current) {
    if (!UPDATER.compareAndSet(this, current, UPDATING)) return;
    data = null;
    last = EMPTY;
  }
}
