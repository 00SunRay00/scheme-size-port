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


public class RoomStatePacket implements Packet {
  /** Maximum buffer size allowed for a state. Anything exceeding this limit will be truncated. */
  public static final int MAX_BUFF_SIZE = 8128;

  public ByteBuffer state;

  @Override
  public void read(ByteBufferInput read) {
    int size = read.readChar();
    if (size == 0) {
      state = null;
      return;
    }

    state = RawPacket.read(read, Math.min(size, MAX_BUFF_SIZE));
    read.skipBytes(Math.max(size - MAX_BUFF_SIZE, 0));
  }

  @Override
  public void write(ByteBufferOutput write) {
    if (state == null) {
      write.writeChar(0);
      return;
    }

    int limit = state.limit();
    if (state.remaining() > MAX_BUFF_SIZE) state.limit(MAX_BUFF_SIZE);
    try {
      write.writeChar(state.remaining());
      RawPacket.write(state, write);
    } finally {
      state.limit(limit);
    }
  }

  @Override
  public boolean allow(boolean isServer) {
    return isServer;
  }
}
