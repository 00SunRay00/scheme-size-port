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

package com.xpdustry.claj.common.packets;

import arc.net.Connection;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


/** Base packet of CLaJ protocol. Packets should be handled in the same thread. */
public interface Packet {
  default void read(ByteBufferInput read) {}
  default void write(ByteBufferOutput write) {}

  @SuppressWarnings("unchecked")
  default <T extends Packet> T r(ByteBufferInput read) {
    read(read);
    return (T)this;
  }
  @SuppressWarnings("unchecked")
  default <T extends Packet> T w(ByteBufferOutput write) {
    write(write);
    return (T)this;
  }

  /** @return whether this packet should be allowed or ignored, for this type of endpoint. */
  default boolean allow(boolean isServer){ return true; }

  /** Called when handling the packet (after reading), in another thread. */
  default void handled() {}

  /** Called when the client handle this packet. Only called when no listener is defined for this packet. */
  default void handleClient() {}
  /** Called when the server handle this packet. Only called when no listener is defined for this packet. */
  default void handleServer(Connection con) {}
}
