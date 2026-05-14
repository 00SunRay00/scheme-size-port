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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import arc.net.Connection;
import arc.util.io.ReusableByteOutStream;


/** Class holding pre-serialized stream, ready to send. This avoids re-serializing the data every times. */
public class PreparedStream {
  protected final ReusableByteOutStream data;
  public final byte type;
  public final int chunkSize;
  public final boolean compressed;

  public PreparedStream(ReusableByteOutStream data, byte type, int chunkSize, boolean compressed) {
    this.data = data;
    this.type = type;
    this.chunkSize = chunkSize;
    this.compressed = compressed;
  }

  public StreamSender send(Connection connection) {
    InputStream in = new ByteArrayInputStream(data.getBytes(), 0, data.size());
    return new StreamSender(connection, in, type, data.size(), chunkSize, compressed);
  }
}
