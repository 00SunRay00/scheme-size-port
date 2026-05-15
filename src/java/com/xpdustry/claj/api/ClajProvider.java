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

package com.xpdustry.claj.api;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

import arc.net.NetListener;

import com.xpdustry.claj.common.packets.ConnectionPacketWrapPacket.Serializer;
import com.xpdustry.claj.common.status.*;


/** Interface to provide implementation dependent client-side things. */
public interface ClajProvider {
  /** Used to post tasks to the main thread, when receiving a packet or running a callback. */
  default void postTask(Runnable task) { task.run(); }
  /** Executor used to post connection tasks. If {@code null}, these operations will be blocking. */
  default ExecutorService getExecutor() { return null; }
  // /** The ping executor used to post blocking ping tasks. */
  // default ExecutorService getPingExecutor() { return getExecutor(); }

  /** Used to create new proxy clients. Cannot be {@code null}. */
  default ClajProxy newProxy() { return new ClajProxy(this); }
  /** Used to create new pinger clients. Cannot be {@code null}. */
  default ClajPinger newPinger() { return new ClajPinger(this); }

  default void handleProxyError(ClajProxy proxy, Throwable error) {
    throw new RuntimeException("Unexpected error in proxy", error);
  }
  default void handlePingerError(ClajPinger pinger, Throwable error) {
    throw new RuntimeException("Unexpected error in pinger", error);
  }

  /**
   * The implementation type, used to validate compatibility between room host and clients. <br>
   * Can be {@code null} to not make any validation (not recommended). <br>
   * This means that if the room host doesn't specify it,
   * any CLaJ implementation can join the room at the cost of possible deserialization errors. <br>
   * And if a client doesn't specify it, the CLaJ server is free to reject it or not.
   * <p>
   * Note that a room without a type will never be displayed in the room browser, even if it is public.
   */
  ClajType getType();
  /**
   * The CLaJ version, used to request a room creation. <br>
   * Must be equals to the server.
   */
  ClajVersion getVersion();

  /** Listener to add to all virtual connections. Can be {@code null}. */
  default NetListener getConnectionListener(ClajProxy proxy) { return null; }

  /**
   * The actual room state, in an encoded form. <br>
   * Will be requested by the server if needed. {@code null} can be returned to not provide state.
   * <p>
   * <b>The buffer size must be less than {@code 2^16} ({@code 65536}). </b>
   * @apiNote The buffer must not be flipped rn.
   */
  default ByteBuffer writeRoomState(ClajProxy proxy) { return null; }
  /** Decode the room state received by the server. */
  default <T> T readRoomState(long roomId, ClajType type, ByteBuffer buff) {
    buff.position(buff.limit()); // fake reading
    return null;
  }

  /**
   * Connect the client to the specified server. <br>
   * @param success can be {@code null} and must be called when connected successfully.
   * @param joinPacket must be send in the client connection after connected successfully.
   *                   The server has little a queue in case this condition is not fully met.
   */
  void connectClient(String host, int port, Runnable success, ByteBuffer joinPacket);

  /**
   * <b>Essential for the protocol to work!</b>
   * <p>
   * This defines how encapsulated packets are serialized and deserialized. <br>
   * This method is called once by the global manager ({@link Claj}).
   */
  Serializer getPacketWrapperSerializer();

  // Client specific handling
  default void showTextMessage(ClajProxy proxy, String text) {}
  default void showMessage(ClajProxy proxy, MessageType message) {}
  default void showPopup(ClajProxy proxy, String text) {}
}
