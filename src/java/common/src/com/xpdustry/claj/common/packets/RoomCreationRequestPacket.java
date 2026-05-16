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

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.status.ClajType;


public class RoomCreationRequestPacket extends DelayedPacket {
  /** Must be the same as the server's major version to be able to connect. */
  public int version = -1;
  /** Implementation type. */
  public ClajType type;

  //TODO: test this
  @Override
  protected void readImpl(ByteBufferInput read) {
    // Make it compatible with older version were no CLaJ version check was done,
    // or were a string was used to do the check.
    // This works due to the way strings are encoded. 2 bytes (length) + 3 or 5 bytes (claj version)
    if (read.buffer.hasRemaining()) {
      int utflen = read.readUnsignedShort();
      if (read.buffer.hasRemaining() && utflen == 0) {
        version = read.readInt();
        type = ClajType.read(read.buffer);
        return;
      }
    }
    version = -1;
    type = null;
  }

  @Override
  public void write(ByteBufferOutput write) {
    write.writeShort(0); //waste two bytes corresponding to utflen
    write.writeInt(version);
    type.write(write.buffer);
  }
    
  @Override
  public boolean allow(boolean isServer) { 
    return isServer; 
  }
}
