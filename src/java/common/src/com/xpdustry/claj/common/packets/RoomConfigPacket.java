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


public class RoomConfigPacket extends DelayedPacket {
  /** Whether the room is visible in the explorer. */
  public boolean isPublic;
  /** Whether a password is needed to join the room. This doens't means that the connection will be encrypted! */
  public boolean isProtected;
  /** The room password. It's a 4 digits pin code and should be {@code -1} if unset. */
  public short password;
  /** Whether the host allows or not the server to request his state. */
  public boolean requestState;
  /** Maximum number of client allowed in the room. Cannot be higher that the server limit. {@code 0 if disabled}. */
  public int maxClients;

  @Override
  protected void readImpl(ByteBufferInput read) {
    int data = read.readUnsignedByte();
    isPublic =     (data & 0b0100) == 0b0100;
    isProtected =  (data & 0b0010) == 0b0010;
    requestState = (data & 0b0001) == 0b0001;
    password = read.readShort();
    maxClients = read.readChar();
  }

  @Override
  public void write(ByteBufferOutput write) {
    int data =     ((isPublic ? 1 : 0) << 2)
             |  ((isProtected ? 1 : 0) << 1)
             | ((requestState ? 1 : 0) << 0);
    write.writeByte(data);
    write.writeShort(password);
    write.writeChar(maxClients);
  }
    
  @Override
  public boolean allow(boolean isServer) { 
    return isServer; 
  }
}
