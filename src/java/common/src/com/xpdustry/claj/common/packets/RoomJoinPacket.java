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


/**
 * Should be sent by the actual client connection after connected successfully and checked request validity. <br>
 * The packet will be manually serialized as, normally, no CLaJ serializer is defined in the actual client connection.
 *
 * @see RoomJoinRequestPacket
 */
public class RoomJoinPacket extends RoomLinkPacket {
  public boolean withPassword;
  /** Room pin password. */
  public short password = -1;
  /** CLaJ Implementation type. 16 bytes max. */
  public ClajType type;

  @Override
  protected void readImpl(ByteBufferInput read) {
    super.readImpl(read);
    // Make it compatible with older versions where room password wasn't here
    // This will only work if the room doesn't have a password set
    if (read.buffer.hasRemaining()) {
      withPassword =read.readBoolean();
      password = read.readShort();
      type = ClajType.read(read.buffer);
    } else {
      withPassword = false;
      password = -1;
      type = null;
    }
  }

  @Override
  public void write(ByteBufferOutput write) {
    super.write(write);
    write.writeBoolean(withPassword);
    write.writeShort(password);
    type.write(write.buffer);
  }
    
  @Override
  public boolean allow(boolean isServer) { 
    return isServer; 
  }
}
