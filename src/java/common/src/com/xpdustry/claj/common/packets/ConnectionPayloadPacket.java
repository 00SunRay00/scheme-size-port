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

import arc.net.ArcNetException;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


/** Special packet for connection packet wrapping. */
public class ConnectionPayloadPacket extends ConnectionWrapperPacket {
  /** Used to notify serializer to read/write the rest. MUST BE SET! */
  public static Serializer serializer;

  /** Decoded object received by the client. */
  public Object object;
  /** Copy of the raw packet received by the server. Must be freed after writing. */
  public RawPacket raw;

  public boolean isTCP;

  @Override
  public void read(ByteBufferInput read) {
    super.read(read);
    isTCP = read.readBoolean();
    if (serializer == null)
      throw new ArcNetException(getClass().getSimpleName() + ".serializer is not set!");
    serializer.read(this, read);
  }

  @Override
  public void write(ByteBufferOutput write) {
    super.write(write);
    write.writeBoolean(isTCP);
    if (serializer == null)
      throw new ArcNetException(getClass().getSimpleName() + ".serializer is not set!");
    serializer.write(this, write);
  }


  public interface Serializer {
    void read(ConnectionPayloadPacket packet, ByteBufferInput read);
    void write(ConnectionPayloadPacket packet, ByteBufferOutput write);
  }
}
