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

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


public class RoomStatePacket extends DelayedPacket {
  public static final int MAX_BUFF_SIZE = 8128;

  public ByteBuffer state;

  @Override
  protected void readImpl(ByteBufferInput read) {
    state = RawPacket.read(read, read.readChar());
  }

  @Override
  public void write(ByteBufferOutput write) {
    int limit = state.limit();
    if (state.remaining() > MAX_BUFF_SIZE) state.limit(MAX_BUFF_SIZE);
    write.writeChar(state.remaining());
    RawPacket.write(state, write);
    state.limit(limit);
  }
    
  @Override
  public boolean allow(boolean isServer) { 
    return isServer; 
  }
}
