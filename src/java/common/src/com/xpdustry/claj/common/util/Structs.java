/**
 * This file is part of CLaJ. The system that allows you to play with your friends,
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025-2026  Xpdustry
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

import java.util.Iterator;
import java.util.NoSuchElementException;

import arc.func.*;
import arc.util.Reflect;


public class Structs extends arc.util.Structs {
  public static <T> T[] insert(T[] array, int index, T item) {
    T[] next = Reflect.newArray(array, array.length + 1);
    if (index > 0) System.arraycopy(array, 0, next, 0, index);
    int tail = array.length - index;
    if (tail > 0) System.arraycopy(array, index, next, index + 1, tail);
    next[index] = item;
    return next;
  }

  @SafeVarargs
  public static <T> Iterable<T> generator(T... list) {
    return () -> new Iterator<>() {
      int index = 0;
      public boolean hasNext() { return index < list.length; }
      public T next() { return list[index++]; }
    };
  }

  public static <T, R> Iterable<R> generator(T[] list, Func<T, R> extractor) {
    return () -> new Iterator<>() {
      int index = 0;
      public boolean hasNext() { return index < list.length; }
      public R next() { return extractor.get(list[index++]); }
    };
  }

  public static <T> Iterable<T> generator(T[] list, Boolf<T> predicate) {
    return () -> new Iterator<>() {
      int index = 0;
      boolean hasNext = true;
      T next;

      { advance(); }

      private T advance() {
        if (!hasNext) throw new NoSuchElementException();
        T old = next;
        hasNext = false;
        while (index < list.length) {
          T candidate = list[index++];
          if(predicate.get(candidate)) {
            next = candidate;
            hasNext = true;
            break;
          }
        }
        return old;
      }

      public boolean hasNext() { return hasNext; }
      public T next() { return advance(); }
    };
  }

  public static <T, R> Iterable<R> generator(T[] list, Boolf<T> predicate, Func<T, R> extractor) {
    return () -> new Iterator<>() {
      int index = 0;
      boolean hasNext = true;
      T next;

      { advance(); }

      private T advance() {
        if (!hasNext) throw new NoSuchElementException();
        T old = next;
        hasNext = false;
        while (index < list.length) {
          T candidate = list[index++];
          if(predicate.get(candidate)) {
            next = candidate;
            hasNext = true;
            break;
          }
        }
        return old;
      }

      public boolean hasNext() { return hasNext; }
      public R next() { return extractor.get(advance()); }
    };
  }

  public static <T> int max(Iterable<T> list, Intf<T> intifier) {
    boolean first = true;
    int index = 0;

    for (T i : list) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s > index) index = s;
      first = false;
    }

    return index;
  }

  public static <T> int max(T[] list, Intf<T> intifier) {
    boolean first = true;
    int index = 0;

    for (T i : list) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s > index) index = s;
      first = false;
    }

    return index;
  }

  public static <T> int min(Iterable<T> list, Intf<T> intifier) {
    boolean first = true;
    int index = 0;

    for (T i : list) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s < index) index = s;
      first = false;
    }

    return index;
  }

  public static <T> int min(T[] list, Intf<T> intifier) {
    boolean first = true;
    int index = 0;

    for (T i : list) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s < index) index = s;
      first = false;
    }

    return index;
  }
}
