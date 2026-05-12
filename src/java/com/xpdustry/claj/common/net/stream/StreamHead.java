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

package com.xpdustry.claj.common.net.stream;

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


/** {@link mindustry.net.Packets.StreamBegin}. */
public class StreamHead implements StreamPacket {
  public static final int MAX_STREAM_SIZE = 1<<20;
  private static int lastid;

  public int id = lastid++;
  public int total;
  public byte type;
  public boolean compressed;

  @Override
  public void read(ByteBufferInput in) {
    id = in.readInt();
    total = in.readInt();
    type = in.readByte();
    compressed = in.readBoolean();
  }

  @Override
  public void write(ByteBufferOutput out) {
    out.writeInt(id);
    out.writeInt(total);
    out.writeByte(type);
    out.writeBoolean(compressed);
  }
}
