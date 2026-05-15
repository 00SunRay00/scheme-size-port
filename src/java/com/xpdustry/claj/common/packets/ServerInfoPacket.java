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

package com.xpdustry.claj.common.packets;

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


public class ServerInfoPacket extends DelayedPacket {
  public int version = -1;
  //TODO: add current server load: number of rooms, max rooms (if any), 
  //      number of clients, max allowed clients per room, maybe an health status?

  @Override
  protected void readImpl(ByteBufferInput read) {
    // By default, arc server is configured to reply an empty buffer.
    // This can be used to determine whether this is an old CLaJ server or not.
    // Because on older versions, no discovery was configured.
    version = read.buffer.hasRemaining() ? read.readInt() : -1;
  }

  @Override
  public void write(ByteBufferOutput write) {
    write.writeInt(version);
  }
    
  @Override
  public boolean allow(boolean isServer) { 
    return !isServer; 
  }
}
