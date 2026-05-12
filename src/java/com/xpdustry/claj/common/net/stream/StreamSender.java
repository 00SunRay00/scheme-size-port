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
import arc.net.InputStreamSender;
import arc.util.Threads;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.util.ByteArrayBufferOutput;


/**
 * {@link mindustry.net.ArcNetProvider.ArcConnection#sendStream(mindustry.net.Streamable)}.
 * <p>
 * Note: {@link StreamHead} and {@link StreamChunk} must be registered in {@link ClajNet}.
 */
public class StreamSender extends InputStreamSender {
  private static final ThreadLocal<StreamChunk> chunk = Threads.local(StreamChunk::new);

  public final Connection connection;
  public final InputStream input;
  public final byte type;
  public final int length;
  public final int chunkSize;
  public final boolean compressed;
  protected int id, written;

  /** Stream is directly started. */
  public StreamSender(Connection connection, InputStream stream, byte type, int length,
                      int chunkSize, boolean isCompressed) {
    super(stream, chunkSize);
    this.connection = connection;
    this.input = stream;
    this.type = type;
    this.length = length;
    this.chunkSize = chunkSize;
    this.compressed = isCompressed;

    connection.addListener(this);
  }

  @Override
  protected void start() {
    StreamHead head = new StreamHead();
    id = head.id;
    head.total = length;
    head.type = type;
    head.compressed = compressed;
    connection.sendTCP(head);
  }

  @Override
  protected Object next(byte[] bytes) {
    StreamChunk chunk = StreamSender.chunk.get();
    written += bytes.length;
    chunk.id = id;
    chunk.last = written >= length;
    chunk.data = bytes;
    return chunk;
  }


  public static StreamSender send(Connection connection, Packet packet) {
    return send(connection, packet, 2048, true);
  }

  public static StreamSender send(Connection connection, Packet packet, boolean compress) {
    return send(connection, packet, 2048, compress);
  }

  public static StreamSender send(Connection connection, Packet packet, int chunkSize, boolean compress) {
    byte id = ClajNet.getId(packet); // will check if the packet is registered
    ByteArrayBufferOutput buff = new ByteArrayBufferOutput(chunkSize, compress);
    packet.write(buff);
    buff.close();
    if (buff.back.size() > StreamHead.MAX_STREAM_SIZE)
      throw new RuntimeException("Stream is too big");
    InputStream in = new ByteArrayInputStream(buff.back.getBytes(), 0, buff.back.size());
    return new StreamSender(connection, in, id, buff.back.size(), chunkSize, buff.compressed);
  }

  public static PreparedStream prepare(Packet packet) {
    return prepare(packet, 2048, true);
  }

  public static PreparedStream prepare(Packet packet, boolean compress) {
    return prepare(packet, 2048, compress);
  }

  public static PreparedStream prepare(Packet packet, int chunkSize, boolean compress) {
    byte id = ClajNet.getId(packet); // will check if the packet is registered
    ByteArrayBufferOutput buff = new ByteArrayBufferOutput(chunkSize,  compress);
    packet.write(buff);
    buff.close();
    if (buff.back.size() > StreamHead.MAX_STREAM_SIZE)
      throw new RuntimeException("Stream is too big");
    return new PreparedStream(buff.back, id, chunkSize, buff.compressed);
  }
}
