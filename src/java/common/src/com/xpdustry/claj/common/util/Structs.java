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

import arc.func.Func;
import arc.func.Intf;
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

  public static <T, R> R[] map(T[] array, Class<R> type, Func<T, R> mapper) {
    R[] next = Reflect.newArray(type, array.length);
    for (int i=0; i<array.length; i++) next[i] = mapper.get(array[i]);
    return next;
  }

  @SafeVarargs
  public static <T> Iterable<T> iterable(T... array) {
    return () -> new Iterator<>() {
      int index = 0;
      public boolean hasNext() { return index < array.length; }
      public T next() {
        if (index >= array.length) throw new NoSuchElementException();
        return array[index++];
      }
    };
  }

  public static <T> int max(Iterable<T> array, Intf<T> intifier) {
    boolean first = true;
    int index = 0;
    for (T i : array) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s > index) index = s;
      first = false;
    }
    return index;
  }

  public static <T> int max(T[] array, Intf<T> intifier) {
    boolean first = true;
    int index = 0;
    for (T i : array) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s > index) index = s;
      first = false;
    }
    return index;
  }

  public static <T> int min(Iterable<T> array, Intf<T> intifier) {
    boolean first = true;
    int index = 0;
    for (T i : array) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s < index) index = s;
      first = false;
    }
    return index;
  }

  public static <T> int min(T[] array, Intf<T> intifier) {
    boolean first = true;
    int index = 0;
    for (T i : array) {
      int s = intifier.get(i);
      if (first) index = s;
      else if (s < index) index = s;
      first = false;
    }
    return index;
  }
}
