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

import com.xpdustry.claj.common.util.ByteBufferPool;


/**
 * Wrapper for {@link ByteBuffer} that implements {@link Packet}. <br>
 * This is only needed due to compatibility with receivers.
 */
public class RawPacket implements Packet {
  protected ByteBuffer first = ByteBufferPool.getHeap(0), second = ByteBufferPool.getHeap(0);
  protected boolean flipped;

  public RawPacket read(ByteBuffer buffer) {
    ByteBuffer buf = flipIfNeeded(buffer.capacity());
    buf.clear();
    buf.put(buffer).flip();
    return this;
  }

  @Override
  public void read(ByteBufferInput read) {
    read(read.buffer);
  }

  @Override
  public void write(ByteBufferOutput write) {
    write(data(), write);
  }

  public ByteBuffer flipIfNeeded(int capacity) {
    ByteBuffer buf = data();
    if (buf.capacity() == capacity) return buf;
    flipped ^= true;
    buf = data();
    if (buf.capacity() == capacity) return buf;
    ByteBufferPool.free(buf);
    buf = ByteBufferPool.getHeap(capacity);
    if (flipped) second = buf;
    else first = buf;
    return buf;
  }

  public ByteBuffer data() {
    return flipped ? second : first;
  }

  public RawPacket copy() {
    return new RawPacket().read(data());
  }


  public static ByteBuffer readAll(ByteBufferInput read) { return read(read, read.buffer.remaining()); }
  public static ByteBuffer read(ByteBufferInput read, int length) {
    byte[] data = new byte[length];
    read.readFully(data);
    return ByteBuffer.wrap(data);
  }

  /** Suppresses {@code src} reading. Optimized for backed array buffers. */
  public static void write(ByteBuffer src, ByteBufferOutput write) {
    if (src == null) return;
    if (src.hasArray()) {
      write.write(src.array(), src.arrayOffset() + src.position(), src.remaining());
    } else {
      // Not safe to write a direct buffer directly, as it can be a stream
      for (int i=src.position(), n=src.limit(); i<n; i++) {
        write.write(src.get(i));
      }
    }
  }
}