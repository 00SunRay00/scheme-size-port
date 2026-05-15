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

import java.nio.ByteBuffer;

import arc.net.ArcNetException;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


/** Special packet for connection packet wrapping. */
public class ConnectionPacketWrapPacket extends ConnectionWrapperPacket {
  /** Used to notify serializer to read/write the rest. MUST BE SET! */
  public static Serializer serializer;

  /** Decoded object received by the client. Should be handled by the serializer. */
  public Object object;
  /** Copy of the raw packet received by the server. Should be handled by the serializer. */
  public ByteBuffer raw;

  public boolean isTCP;

  @Override
  protected void readImpl(ByteBufferInput read) {
    super.readImpl(read);
    isTCP = read.readBoolean();
    if (serializer == null)
      throw new ArcNetException("ConnectionPacketWrapPacket.serializer is not set!");
    serializer.read(this, read);
  }

  @Override
  public void write(ByteBufferOutput write) {
    super.write(write);
    write.writeBoolean(isTCP);
    if (serializer == null)
      throw new ArcNetException("ConnectionPacketWrapPacket.serializer is not set!");
    serializer.write(this, write);
  }

  
  public interface Serializer {
    void read(ConnectionPacketWrapPacket packet, ByteBufferInput read);
    void write(ConnectionPacketWrapPacket packet, ByteBufferOutput write);
  }
}
