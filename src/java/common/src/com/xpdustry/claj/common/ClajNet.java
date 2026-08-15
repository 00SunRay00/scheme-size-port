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

package com.xpdustry.claj.common;

import arc.func.Prov;
import arc.net.ArcNetException;
import arc.struct.*;
import arc.util.Threads;
import arc.util.pooling.Pool;

import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.util.FastThreadLocal;


public class ClajNet {
  /** Identifier for framework messages. */
  public static final byte frameworkId = -2;
  /** Old CLaJ id. */
  public static final byte oldId = -3;
  /** Identifier for CLaJ packets. */
  public static final byte id = -4;

  /** Maximum number of packet that can be registered. */
  public static final int MAX_PACKETS = 1<<Byte.SIZE;

  private static final ObjectIntMap<Class<?>> packetIds = new ObjectIntMap<>(16);
  private static final Seq<Prov<?>> packets = new Seq<>();
  private static final Seq<ThreadLocal<?>> packetLocals = new Seq<>();
  private static final Seq<Pool<?>> packetPools = new Seq<>();

  /**
   * Registers a new packet type for serialization. Ignores if already registered.
   * @throws IllegalArgumentException if no id is available for this packet. ({@code 256} packets max)
   */
  public static <T extends Packet> void register(Prov<T> cons) {
    Class<?> type = cons.get().getClass();
    if (packetIds.containsKey(type)) return;
    if (packets.size >= MAX_PACKETS) throw new IllegalArgumentException("Packets limit reached");
    packetIds.put(type, packets.size);
    packets.add(cons);
    packetLocals.add((ThreadLocal<?>)null);
    packetPools.add((Pool<?>)null);
  }

  protected static int getIndex(Class<? extends Packet> packet) {
    int i = packetIds.get(packet, -1);
    if (i == -1 || i >= packets.size) throw new ArcNetException("Unknown packet type: " + packet);
    return i;
  }

  protected static int getIndex(byte id) {
    int i = id & 0xff;
    if (i >= packets.size) throw new ArcNetException("Unknown packet id: " + id);
    return i;
  }

  public static byte getId(Packet packet) { return getId(packet.getClass()); }
  public static byte getId(Class<? extends Packet> packet) {
    return (byte)getIndex(packet);
  }

  @SuppressWarnings("unchecked")
  public static <T extends Packet> T newPacket(byte id) {
    return (T)packets.get(getIndex(id)).get();
  }

  public static <T extends Packet> T newLocalPacket(byte id) { return newLocalPacket(id, false); }
  /**
   * For use with read, if packets are processed on the same thread.
   * @see #newLocalPacket(Class, boolean)
   */
  public static <T extends Packet> T newLocalPacket(byte id, boolean fast) {
    return newLocalPacket(getIndex(id), fast);
  }

  public static <T extends Packet> T newLocalPacket(Class<T> packet) { return newLocalPacket(packet, true); }
  /**
   * For use with send, as packets are serialized in-place.
   * <p>
   * The {@code fast} argument determines whether to use an implementation with a same thread use (MRU) fast path.
   * Default is {@code false}. <br>
   * You would set it to {@code false} only if you think the packet is likely to be frequently used
   * by multiple threads. As the fast path is only faster when a single thread is requesting it intensively. <br>
   * The argument is only taken into account during the first call for a given packet.
   */
  public static <T extends Packet> T newLocalPacket(Class<T> packet, boolean fast) {
    return newLocalPacket(getIndex(packet), fast);
  }

  @SuppressWarnings("unchecked")
  protected static <T extends Packet> T newLocalPacket(int index, boolean fast) {
    ThreadLocal<?> local = packetLocals.get(index);
    if (local == null) {
      Prov<?> prov = packets.get(index);
      local = fast ? FastThreadLocal.with(prov): Threads.local(prov);
      packetLocals.set(index, local);
    }
    return (T)local.get();
  }


  /** For use with read, as packets are more likely to be processed on another thread. */
  public static <T extends Packet> T newPooledPacket(byte id) {
    return newPooledPacket(getIndex(id));
  }

  /** For use with send, if packets are serialized on another thread, or for other usages. */
  public static <T extends Packet> T newPooledPacket(Class<T> packet) {
    return newPooledPacket(getIndex(packet));
  }

  @SuppressWarnings("unchecked")
  protected static <T extends Packet> T newPooledPacket(int index) {
    Pool<?> pool = packetPools.get(index);
    if (pool == null) {
      Prov<?> prov = packets.get(index);
      pool = new Pool<>(8, 128) { protected Object newObject() { return prov.get(); } };
      packetPools.set(index, pool);
    }
    return (T)pool.obtain();
  }

  /** Never free a packet get from {@link #newLocalPacket}!! */
  public static <T extends Packet> void freePooledPacket(T packet) {
    freePooledPacket(getIndex(packet.getClass()), packet);
  }

  /** Never free a packet get from {@link #newLocalPacket}!! */
  public static <T extends Packet> void freePooledPacket(byte id, T packet) {
    freePooledPacket(getIndex(id), packet);
  }

  @SuppressWarnings("unchecked")
  protected static <T extends Packet> void freePooledPacket(int index, T packet) {
    Pool<T> pool = (Pool<T>)packetPools.get(index);
    if (pool != null) pool.free(packet);
  }
}
