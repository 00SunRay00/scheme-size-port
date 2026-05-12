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

package com.xpdustry.claj.server;

import java.nio.ByteBuffer;

import arc.Events;
import arc.math.Mathf;
import arc.net.*;
import arc.struct.IntMap;
import arc.util.Ratekeeper;
import arc.util.Threads;
import arc.util.Time;

import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;
import com.xpdustry.claj.common.util.AddressUtil;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.ClajEvents.*;
import com.xpdustry.claj.server.util.NetworkSpeed;


public class ClajRoom implements NetListener {
  // Packet caching
  private static final ThreadLocal<ConnectionJoinPacket> cjp = Threads.local(ConnectionJoinPacket::new);
  private static final ThreadLocal<ConnectionClosedPacket> ccp = Threads.local(ConnectionClosedPacket::new);
  private static final ThreadLocal<ConnectionPacketWrapPacket> cwp = Threads.local(ConnectionPacketWrapPacket::new);
  private static final ThreadLocal<ConnectionIdlingPacket> cip = Threads.local(ConnectionIdlingPacket::new);


  protected boolean closed;

  /** The room id. */
  public final long id;
  /**
   * The room id encoded in an url-safe base64 string.
   * @see com.xpdustry.claj.api.ClajLink
   */
  public final String sid;
  /** The host connection of this room. */
  public final ClajConnection host;
  /** Using IntMap instead of Seq for faster search. */
  public final IntMap<ClajConnection> clients = new IntMap<>();
  /** For debugging, to know how many packets were transferred from a client to a host, and vice versa. */
  public final NetworkSpeed transferredPackets = new NetworkSpeed(8);
  /** Room state rate-limit. New states will simply be discarded. */
  public final Ratekeeper stateRate = new Ratekeeper();

  /** Creation date of the room. Sets when {@link #create()} is called. */
  public long createdAt;
  /** Closing date of the room. Sets when {@link #close()} is called. */
  public long closedAt;
  /** Whether the room will be added is public list or not. */
  public boolean isPublic;
  /** Whether the room needs a password or not to join it. */
  public boolean isProtected;
  /** The room password */
  public short password;
  /** Whether the host want, or not, the server to request his state when needed. */
  public boolean canRequestState;
  /** De-serialized room state, only present if the right decoder is present. */
  public Object state;
  /** State of the room as raw data. {@code null} if no state was received. */
  public ByteBuffer rawState;
  /** Time of the last received room state. (in ns). */
  public long lastReceivedState;
  /** Time of the last requested room state. (in ns). */
  public long lastRequestedState;
  /** Whether a state has been requested to the room. */
  public boolean requestingState;
  /** Room implementation type. Can be {@code null}. */
  public final ClajType type;
  /**
   * Maximum number of CLaJ client allowed in this room. <br>
   * {@code 0} means no limit and the value must not be higher that the server limit.
   */
  public int maxClients;

  public ClajRoom(long id, ClajConnection host, ClajType type) {
    if (id == 0) throw new IllegalArgumentException("invalid room id");
    if (host == null) throw new IllegalArgumentException("host cannot be null");
    this.id = id;
    this.sid = Strings.longToBase64(id);
    this.host = host;
    this.type = type;
    setRoom(host);
  }

  protected void setRoom(ClajConnection con) {
    if (con.room != null) {
      String msg = isHost(con) ? "the host is owning another room" : "the connection is already in another room";
      throw new IllegalArgumentException(msg);
    }
    con.room = this;
  }

  protected void removeRoom(ClajConnection con) {
    if (con.room == this) con.room = null;
  }

  /** Alerts the host that a new client is coming */
  @Override
  public void connected(Connection connection) {
    ClajConnection con = ClajRelay.toClajCon(connection);
    if (con != null) connected(con);
  }

  /** Alerts the host of the client arrival. */
  public void connected(ClajConnection connection) {
    if (closed || connection == null || clients.containsKey(connection.id)) return;

    ConnectionJoinPacket p = cjp.get();
    p.conID = connection.id;
    p.addressHash = AddressUtil.hash(connection.connection);
    host.send(p); // Assumes the host is still connected

    clients.put(connection.id, connection);
    setRoom(connection);
    Events.fire(new ConnectionJoinedEvent(connection, this));
  }

  /** Alerts the host that a client disconnected. This doesn't close the connection. */
  @Override
  public void disconnected(Connection connection, DcReason reason) {
    ClajConnection con = ClajRelay.toClajCon(connection);
    if (con != null) disconnected(con, reason);
  }

  /** Alerts the host that a client disconnected. This doesn't close the connection. */
  public void disconnected(ClajConnection connection, DcReason reason) {
    if (closed || connection == null) return;

    // Don't forget to update packet speed, as this is calculated between receives
    transferredPackets.uploadMark(0);
    transferredPackets.downloadMark(0);

    if (isHost(connection)) {
      Events.fire(new ConnectionLeftEvent(connection, this));
      close();
      return;
    }

    removeRoom(connection);
    ClajConnection con = clients.remove(connection.id);
    if (con == null) return; // In case of the event is received twice

    if (host.isConnected()) {
      ConnectionClosedPacket p = ccp.get();
      p.conID = connection.id;
      p.reason = reason;
      host.send(p);
    }

    Events.fire(new ConnectionLeftEvent(connection, this));
  }

  /** Doesn't notify the room host about a disconnected client. */
  public void disconnectedQuietly(Connection connection, DcReason reason) {
    ClajConnection con = ClajRelay.toClajCon(connection);
    if (con != null) disconnectedQuietly(con, reason);
  }

  /** Doesn't notify the room host about a disconnected client. */
  public void disconnectedQuietly(ClajConnection connection, DcReason reason) {
    if (closed || connection == null) return;

    if (isHost(connection)) {
      Events.fire(new ConnectionLeftEvent(connection, this));
      close();
    } else {
      removeRoom(connection);
      ClajConnection con = clients.remove(connection.id);
      // To avoid double fire if event is received twice
      if (con != null) Events.fire(new ConnectionLeftEvent(connection, this));
    }
  }

  /**
   * Wraps and re-sends the packet to the host, if it come from a connection. <br>
   * Or un-wraps and re-sends the packet to the specified connection.
   * <p>
   * Only {@link ConnectionPacketWrapPacket} and {@link RawPacket} are allowed.
   */
  @Override
  public void received(Connection connection, Object object) {
    if (closed || connection == null) return;

    if (isHost(connection)) {
      if (object instanceof ConnectionPacketWrapPacket wrap)
        received(connection, wrap);

    } else if (clients.containsKey(connection.getID())) {
      if (object instanceof RawPacket raw)
        received(connection, raw);
    }
  }

  public void received(ClajConnection connection, Object object) {
    if (connection == null) return;
    received(connection.connection, object);
  }

  /**
   * Unwraps the packet and sends it to the corresponding connection. <br>
   * This will notify the host if the connection is not found.
   */
  public void received(Connection connection, ConnectionPacketWrapPacket wrap) {
    if (closed || !isHost(connection)) return;
    ClajConnection con = clients.get(wrap.conID);

    if (con != null && con.isConnected()) {
      con.send(wrap.raw, wrap.isTCP);
      transferredPackets.uploadMark();

    // Notify that this connection doesn't exist, this case normally never happen
    } else if (host.isConnected()) {
      ConnectionClosedPacket p = ccp.get();
      p.conID = wrap.conID;
      p.reason = DcReason.error;
      host.send(p);
    }
  }

  public void received(ClajConnection connection, ConnectionPacketWrapPacket wrap) {
    if (connection == null) return;
    received(connection.connection, wrap);
  }

  /**
   * We never send claj packets to anyone other than the room host,
   * framework packets are ignored and mindustry packets are saved as raw buffer.
   */
  public void received(Connection connection, RawPacket raw) {
    if (closed || connection == null || !host.isConnected() ||
        !clients.containsKey(connection.getID())) return;

    //NOTE: ai slop is saying me this can lead to a buffer corruption...
    ConnectionPacketWrapPacket p = cwp.get();
    p.conID = connection.getID();
    p.raw = raw.data;
    host.send(p);
    transferredPackets.downloadMark();
  }

  public void received(ClajConnection connection, RawPacket raw) {
    if (connection == null) return;
    received(connection.connection, raw);
  }

  /** Notifies the host of an idle connection. */
  @Override
  public void idle(Connection connection) {
    if (closed || connection == null) return;

    if (isHost(connection)) {
      // Ignore if this is the room host

    } else if (host.isConnected() && clients.containsKey(connection.getID())) {
      ConnectionIdlingPacket p = cip.get();
      p.conID = connection.getID();
      host.send(p);
    }
  }

  /** Notifies the host of an idle connection. */
  public void idle(ClajConnection connection) {
    if (connection == null) return;
    idle(connection.connection);
  }

  /** @return {@code true} if {@link #type} is {@code null}, the provided one is the same or {@code null},
   *          if {@link ClajConfig#acceptNoType} is {@code true}.
   */
  public boolean allowsType(ClajType type) {
    return this.type == null || this.type.equals(type) || type == null && ClajConfig.acceptNoType.get();
  }

  /** Notifies the room id to the host. Must be called once. */
  public void create() {
    if (closed) return;
    createdAt = Time.millis();

    // Assume the host is still connected
    RoomLinkPacket p = new RoomLinkPacket();
    p.roomId = id;
    host.send(p);

    Events.fire(new RoomCreatedEvent(this));
  }

  /** @return whether the room is closed or not. */
  public boolean isClosed() {
    return closed;
  }

  public void close() {
    close(CloseReason.closed);
  }

  /**
   * Closes the room and disconnects the host and all clients. <br>
   * The room object cannot be used anymore after this.
   */
  public void close(CloseReason reason) {
    if (closed) return;
    closed = true; // close before kicking connections, to avoid receiving events
    closedAt = Time.millis();

    // Notify the reason to the host
    RoomClosedPacket p = new RoomClosedPacket();
    p.reason = reason;
    host.send(p);

    Events.fire(new RoomClosedEvent(this));

    removeRoom(host);
    host.close();
    for (ClajConnection c : clients.values()) {
      removeRoom(c);
      c.close();
    }
    clients.clear();
  }

  /** Sends a message to the host and clients. */
  public void message(String text) {
    if (closed) return;

    // Just send to host, it will re-send it properly to all clients
    ClajTextMessagePacket p = new ClajTextMessagePacket();
    p.message = text;
    host.send(p);
  }

  /** Sends a message the host and clients. Will be translated by the room host. */
  public void message(MessageType message) {
    if (closed) return;

    // Useless to cache packet here.
    ClajMessagePacket p = new ClajMessagePacket();
    p.message = message;
    host.send(p);
  }

  /** Sends a popup to the room host. */
  public void popup(String text) {
    if (closed) return;

    // Useless to cache packet here.
    ClajPopupPacket p = new ClajPopupPacket();
    p.message = text;
    host.send(p);
  }

  public void setConfiguration(boolean isPublic, boolean isProtected, short password, boolean requestState,
                               int maxClients) {
    if (closed) return;

    this.isPublic = isPublic;
    this.isProtected = isProtected;
    this.password = password;
    this.canRequestState = requestState;
    int limit = ClajConfig.clientLimit.get();
    this.maxClients = limit > 0 ? Mathf.clamp(maxClients, 0, limit) : maxClients;
    Events.fire(new ConfigurationChangedEvent(this));
  }

  /**
   * Only requests state if not already done.
   * @return whether the state has been requested.
   */
  public boolean requestState() {
    return requestState(Time.nanos());
  }

  /**
   * Only requests state if not already done.
   * @return whether the state has been requested.
   */
  public boolean requestState(long timeNs) {
    if (closed || !isStateRequestTimedOut(timeNs)) return false;
    lastRequestedState = timeNs;
    requestingState = true;
    host.send(RoomStateRequestPacket.instance);
    return true;
  }


  public void setState(ByteBuffer rawState) {
    if (closed) return;
    if (rawState != null && rawState.remaining() >= RoomInfoPacket.MAX_BUFF_SIZE)
      throw new IllegalArgumentException("Buffer size must be less than " + RoomInfoPacket.MAX_BUFF_SIZE);
    lastReceivedState = Time.nanos();
    this.rawState = rawState;
    state = null; //TODO: add public decoder list
    requestingState = false;
    Events.fire(new StateChangedEvent(this));
  }

  public boolean isStateRequestTimedOut() {
    if (!requestingState) return true;
    int timeout = ClajConfig.stateTimeout.get() * 1_000_000_000;
    return timeout > 0 && Time.timeSinceNanos(lastRequestedState) >= timeout;
  }
  public boolean isStateRequestTimedOut(long timeNs) {
    if (!requestingState) return true;
    int timeout = ClajConfig.stateTimeout.get() * 1_000_000_000;
    return timeout > 0 && timeNs - lastRequestedState >= timeout;
  }

  public boolean isStateOutdated() {
    int lifetime = ClajConfig.stateLifetime.get() * 1_000_000_000;
    return lifetime > 0 && Time.timeSinceNanos(lastReceivedState) >= lifetime;
  }
  public boolean isStateOutdated(long timeNs) {
    int lifetime = ClajConfig.stateLifetime.get() * 1_000_000_000;
    return lifetime > 0 && timeNs - lastReceivedState >= lifetime;
  }

  public boolean shouldRequestState() {
    return !closed && isPublic && canRequestState;
  }

  public boolean needStateRequest(long timeNs) {
    return shouldRequestState() && isStateOutdated(timeNs) && isStateRequestTimedOut(timeNs);
  }

  /** State is only send if room {@link #isPublic}. */
  public void sendRoomState(ClajConnection connection) {
    if (closed) return;

    RoomInfoPacket p = new RoomInfoPacket();
    p.roomId = id;
    p.isProtected = isProtected;
    p.type = type;
    p.clients = clients.size;
    p.maxClients = maxClients;
    p.state = isPublic ? rawState : null;
    // Do not throw an error if buffer is above limit
    connection.send(p);
  }

  /** @return whether specified connection is the room host or not. */
  public boolean isHost(Connection con) {
    return con == host.connection;
  }

  /** @return whether specified connection is the room host or not. */
  public boolean isHost(ClajConnection con) {
    return con == host;
  }

  /** @return whether the connection is the room host or one of his client. */
  public boolean contains(ClajConnection con) {
    return contains(con.connection);
  }

  /** @return whether the connection is the room host or one of his client. */
  public boolean contains(Connection con) {
    return !closed && con != null && (isHost(con) || clients.containsKey(con.getID()));
  }

  /** Only hashes {@link #id}. */
  @Override
  public int hashCode() {
    return Long.hashCode(id);
  }

  /** Only uses {@link #id} as identity. */
  @Override
  public boolean equals(Object o) {
    return o == this || o instanceof ClajRoom room && room.id == id;
  }

  @Override
  public String toString() {
    return sid;
  }
}
