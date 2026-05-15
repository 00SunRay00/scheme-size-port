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


/**
 * Wrapper for {@link ByteBuffer} that implements {@link Packet}. <br>
 * This is only needed due to compatibility with receivers.
 */
public class RawPacket implements Packet {
  public final ByteBuffer data;

  public RawPacket(ByteBuffer buffer) {
    data = copyRemaining(buffer);
  }

  @Override
  public void read(ByteBufferInput read) {
    data.clear();
    data.put(read.buffer).flip();
  }

  @Override
  public void write(ByteBufferOutput write) {
    write(data, write);
  }

  // Helpers

  public static ByteBuffer copyRemaining(ByteBufferInput in) { return copyRemaining(in.buffer); }
  public static ByteBuffer copyRemaining(ByteBuffer src) {
    ByteBuffer data = ByteBuffer.allocate(src.remaining());
    data.put(src).flip();
    return data;
  }

  public static ByteBuffer read(ByteBufferInput read, int length) {
    byte[] data = new byte[length];
    read.readFully(data);
    return ByteBuffer.wrap(data);
  }

  /** Suppresses {@code src} reading. Optimized for backed array buffers. */
  public static void write(ByteBuffer src, ByteBufferOutput write) {
    if (src.hasArray()) {
      write.write(src.array(), src.arrayOffset() + src.position(), src.remaining());
    } else {
      // Not safe to write buffer directly
      int pos = src.position();
      byte[] bytes = new byte[src.remaining()];
      src.get(bytes);
      src.position(pos);
      write.write(bytes);
    }
  }
}