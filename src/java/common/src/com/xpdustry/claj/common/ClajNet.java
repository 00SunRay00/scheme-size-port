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
import arc.struct.ObjectMap;

import com.xpdustry.claj.common.packets.Packet;


public class ClajNet {
  /** Identifier for framework messages. */
  public static final byte frameworkId = -2;
  /** Old CLaJ id. */
  public static final byte oldId = -3;
  /** Identifier for CLaJ packets. */
  public static final byte id = -4;

  /** Maximum number of packet that can be registered. */
  public static final int MAX_PACKETS = 256;

  protected static final ObjectMap<Class<?>, Byte> packetToId = new ObjectMap<>(32);
  protected static final ObjectMap<Byte, Prov<?>> idToPacket = new ObjectMap<>(32);

  /**
   * Registers a new packet type for serialization. Ignores if already registered.
   * @throws IllegalArgumentException if no id is available for this packet. ({@code 256} packets max)
   */
  public static <T extends Packet> void register(Prov<T> cons) {
    Class<?> type = cons.get().getClass();
    if (packetToId.containsKey(type)) return;
    if (packetToId.size >= MAX_PACKETS) throw new IllegalArgumentException("packet limit reached");
    byte id = (byte)packetToId.size;
    packetToId.put(type, id);
    idToPacket.put(id, cons);
  }

  public static byte getId(Packet packet) { return getId(packet.getClass()); }
  public static byte getId(Class<? extends Packet> packet) {
    Byte id = packetToId.get(packet);
    if(id == null) throw new ArcNetException("Unknown packet type: " + packet.getClass());
    return id;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Packet> T newPacket(byte id) {
    Prov<?> packet = idToPacket.get(id);
    if (packet == null) throw new ArcNetException("Unknown packet id: " + id);
    return (T)packet.get();
  }
}
