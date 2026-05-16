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

import java.nio.ByteBuffer;

import arc.struct.LongMap;
import arc.struct.ObjectSet;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


/** Can be a huge packet, should be sent with {@link StreamSender} instead. */
public class RoomListPacket extends DelayedPacket {
  public final LongMap<ByteBuffer> states = new LongMap<>();
  public final ObjectSet<Long> protectedRooms = new ObjectSet<>(32);
  //TODO: add clients and maxClients info

  public RoomListPacket clear(boolean alsoShrink) {
    states.clear();
    protectedRooms.clear();
    if (alsoShrink) {
      states.shrink(0);
      protectedRooms.shrink(0);
    }
    return this;
  }
  
  @Override
  protected void readImpl(ByteBufferInput read) {
    for (int i=0, n=read.readInt(); i<n; i++) {
      long room = read.readLong();
      if (read.readBoolean()) protectedRooms.add(room);
      int length = read.readChar();
      states.put(room, length == 0 ? null : RawPacket.read(read, length));
    }
  }

  @Override
  public void write(ByteBufferOutput write) {
    write.writeInt(states.size);
    for (LongMap.Entry<ByteBuffer> e : states) {
      write.writeLong(e.key);
      write.writeBoolean(protectedRooms.contains(e.key));
      if (e.value != null) {
        write.writeChar(e.value.remaining());
        RawPacket.write(e.value, write);
      } else write.writeChar(0);
    }
  }
    
  @Override
  public boolean allow(boolean isServer) { 
    return !isServer; 
  }
}
