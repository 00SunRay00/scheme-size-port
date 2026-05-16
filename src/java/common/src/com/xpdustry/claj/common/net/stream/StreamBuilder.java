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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.InflaterOutputStream;

import arc.util.io.ByteBufferInput;
import arc.util.io.ReusableByteOutStream;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.packets.Packet;


/** {@link mindustry.net.Streamable.StreamBuilder}. */
public class StreamBuilder {
  public final int id;
  public final byte type;
  public final int total;
  public final boolean compressed;
  public final ReusableByteOutStream back;
  public final OutputStream stream;
  protected boolean finished;
  protected int reads;

  public StreamBuilder(StreamHead head) {
    id = head.id;
    type = head.type;
    total = head.total;
    compressed = head.compressed;
    back = new ReusableByteOutStream();
    stream = compressed ? new InflaterOutputStream(back) : back;
  }

  public int size() {
    return reads;
  }

  public float progress() {
    return (float)size() / total;
  }

  public boolean isDone() {
    return finished || size() >= total;
  }

  /** Sets finish state and closes stream. */
  public void finish() {
    finished = true;
    try { stream.close(); }
    catch (IOException e) { throw new RuntimeException(e); }
  }

  public void add(StreamChunk chunk) {
    if (chunk.id != id) throw new IllegalArgumentException("wrong chunk id: " + chunk.id + "!=" + id);
    add(chunk.data);
    if (chunk.last) finish();
  }

  public void add(byte[] bytes) {
    try {
      stream.write(bytes);
      stream.flush();
      reads += bytes.length;
      //TODO: check for uncompressed data?
    } catch (IOException e) { throw new RuntimeException(e); }
  }

  @SuppressWarnings("unchecked")
  public <T extends Packet> T build() {
    Packet packet = ClajNet.newPacket(type);
    packet.read(new ByteBufferInput(ByteBuffer.wrap(back.getBytes(), 0, back.size())));
    return (T)packet;
  }
}
