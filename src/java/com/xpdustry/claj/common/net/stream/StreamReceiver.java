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

import arc.net.Connection;
import arc.struct.IntMap;

import com.xpdustry.claj.common.packets.Packet;


//TODO: clear unfinished streams after one minute.
public class StreamReceiver {
  public static final int MAX_SIMULTANEOUS_STREAMS = 8;

  protected static IntMap<IntMap<StreamBuilder>> sbuilders;
  protected static IntMap<StreamBuilder> cbuilders;

  /**
   * Client stream builders.
   * @return {@code null} until a stream is complete.
   * @throws RuntimeException if a chunk was received before his head.
   */
  public static Packet received(StreamPacket packet) {
    if (cbuilders == null) cbuilders = new IntMap<>(MAX_SIMULTANEOUS_STREAMS);

    if (packet instanceof StreamHead begin) {
      if (begin.total >= StreamHead.MAX_STREAM_SIZE)
        throw new RuntimeException("Stream is too big; " + begin.total + ">=" + StreamHead.MAX_STREAM_SIZE);
      if (cbuilders.size >= MAX_SIMULTANEOUS_STREAMS)
        throw new RuntimeException("Too many simultaneous streams; " + cbuilders.size + ">=" + MAX_SIMULTANEOUS_STREAMS);
      cbuilders.put(begin.id, new StreamBuilder(begin));

    } else if (packet instanceof StreamChunk chunk) {
      StreamBuilder builder = cbuilders.get(chunk.id);
      if (builder == null)
        throw new RuntimeException("Received a StreamChunk without a StreamHead beforehand!");
      builder.add(chunk);
      if (builder.isDone()) {
        cbuilders.remove(chunk.id);
        return builder.build();
      }
    }

    return null;
  }

  /**
   * Server stream builders.
   * @return {@code null} until a stream is complete.
   * @throws RuntimeException if a chunk was received before his head.
   */
  @SuppressWarnings("null")
  public static Packet received(Connection connection, StreamPacket packet) {
    if (sbuilders == null) sbuilders = new IntMap<>(16);

    if (packet instanceof StreamHead begin) {
      if (begin.total >= StreamHead.MAX_STREAM_SIZE)
        throw new RuntimeException("Stream is too big for " + connection + "; " +
                                   begin.total + ">=" + StreamHead.MAX_STREAM_SIZE);
      IntMap<StreamBuilder> builders = sbuilders.get(connection.getID(), () -> new IntMap<>(MAX_SIMULTANEOUS_STREAMS));
      if (builders.size >= MAX_SIMULTANEOUS_STREAMS)
        throw new RuntimeException("Too many simultaneous streams for " + connection + "; " +
                                   builders.size + ">=" + MAX_SIMULTANEOUS_STREAMS);
      builders.put(begin.id, new StreamBuilder(begin));

    } else if (packet instanceof StreamChunk chunk) {
      IntMap<StreamBuilder> builds = sbuilders.get(connection.getID());
      StreamBuilder builder = builds != null ? builds.get(chunk.id) : null;
      if (builder == null)
        throw new RuntimeException("Received a StreamChunk without a StreamHead beforehand!");
      builder.add(chunk.data);
      if (builder.isDone()) {
        builds.remove(chunk.id);
        // Must be cleared at disconnect for optimization
        //if (builds.isEmpty()) sbuilders.remove(connection.getID());
        return builder.build();
      }
    }

    return null;
  }

  /** Clears client stream builders. */
  public static void reset() {
    if (has()) cbuilders.clear();
  }

  /** Removes server stream builders of a connection (or all if null). Must be called at disconnect. */
  public static void reset(Connection connection) {
    if (sbuilders == null) return;
    else if (connection == null) sbuilders.clear();
    else sbuilders.remove(connection.getID());
  }

  /** @return whether a client builder is in progress or not. */
  public static boolean has() {
    return cbuilders != null && !cbuilders.isEmpty();
  }

  /** @return whether a server builder is present for the specified connection (or any if null) or not. */
  public static boolean has(Connection connection) {
    return sbuilders != null
        && (connection == null ? !sbuilders.isEmpty() : sbuilders.containsKey(connection.getID()));
  }
}
