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

import arc.net.DcReason;

import com.xpdustry.claj.common.status.*;


//TODO: use record classes?
public class ClajEvents {
  public static class ServerLoadedEvent {}
  public static class ServerStoppingEvent {
    /** Whether the server is still available or not to send packets. (timeout) */
    public final boolean stillAvailable;

    public ServerStoppingEvent(boolean stillAvailable) {
      this.stillAvailable = stillAvailable;
    }
  }

  public static class ClientConnectedEvent {
    public final ClajConnection connection;

    public ClientConnectedEvent(ClajConnection connection) {
      this.connection = connection;
    }
  }
  /** When a connection left the server. Only called if the connection was valid. */
  public static class ClientDisonnectedEvent {
    public final ClajConnection connection;
    public final DcReason reason;

    public ClientDisonnectedEvent(ClajConnection connection, DcReason reason) {
      this.connection = connection;
      this.reason = reason;
    }
  }

  /** Currently, the only reason is for packet spam. */
  public static class ClientKickedEvent {
    public final ClajConnection connection;

    public ClientKickedEvent(ClajConnection connection) {
      this.connection = connection;
    }
  }
  public static class HostKickedEvent {
    /** At this point, the room is already closed and everyone have been kicked. */
    public final ClajRoom room;

    public HostKickedEvent(ClajRoom room) {
      this.room = room;
    }
  }

  /** When a connection will join a room. */
  public static class ConnectionPreJoinEvent {
    public final ClajConnection connection;
    public final ClajRoom room;

    public ConnectionPreJoinEvent(ClajConnection connection, ClajRoom room) {
      this.connection = connection;
      this.room = room;
    }
  }
  /** When a connection join a room. */
  public static class ConnectionJoinedEvent {
    public final ClajConnection connection;
    public final ClajRoom room;

    public ConnectionJoinedEvent(ClajConnection connection, ClajRoom room) {
      this.connection = connection;
      this.room = room;
    }
  }
  /** When a connection left a room. */
  public static class ConnectionLeftEvent {
    public final ClajConnection connection;
    public final ClajRoom room;

    public ConnectionLeftEvent(ClajConnection connection, ClajRoom room) {
      this.connection = connection;
      this.room = room;
    }
  }
  public static class ConnectionJoinRejectedEvent {
    public final ClajConnection connection;
    /** Can be {@code null} if the room is not found. */
    public final ClajRoom room;
    public final RejectReason reason;

    public ConnectionJoinRejectedEvent(ClajConnection connection, ClajRoom room, RejectReason reason) {
      this.connection = connection;
      this.room = room;
      this.reason = reason;
    }
  }

  public static class RoomCreatedEvent {
    public final ClajRoom room;

    public RoomCreatedEvent(ClajRoom room) {
      this.room = room;
    }
  }
  public static class RoomClosedEvent {
    /** Note that the room is closed, so it cannot be used anymore. */
    public final ClajRoom room;

    public RoomClosedEvent(ClajRoom room) {
      this.room = room;
    }
  }
  public static class RoomCreationRejectedEvent {
    /** the connection that tried to create the room */
    public final ClajConnection connection;
    public final CloseReason reason;

    public RoomCreationRejectedEvent(ClajConnection connection, CloseReason reason) {
      this.connection = connection;
      this.reason = reason;
    }
  }

  /**
   * Defines an action tried by a connection but was not allowed to do it.
   * <p>
   * E.g. a client of the room tried to close it, or the host tried to join another room while hosting one.
   */
  public static class ActionDeniedEvent {
    public final ClajConnection connection;
    public final ClajRoom room;
    public final MessageType reason;

    public ActionDeniedEvent(ClajConnection connection, ClajRoom room, MessageType reason) {
      this.connection = connection;
      this.room = room;
      this.reason = reason;
    }
  }

  public static class ConfigurationChangedEvent {
    public final ClajRoom room;

    public ConfigurationChangedEvent(ClajRoom room) {
      this.room = room;
    }
  }
  public static class StateChangedEvent {
    public final ClajRoom room;

    public StateChangedEvent(ClajRoom room) {
      this.room = room;
    }
  }

  public static class RoomInfoRejectedEvent {
    public final ClajConnection connection;
    /** Not {@code nul} means that action was denied because the room is closing. */
    public final ClajRoom room;
    /**
     * {@code true} if the action was denied because of rate limit. <br>
     * if both {@link #room} and field are {@code null} and {@code false},
     * this means that the room was not found.
     */
    public final boolean rateLimited;

    public RoomInfoRejectedEvent(ClajConnection connection, ClajRoom room, boolean rateLimited) {
      this.connection = connection;
      this.room = room;
      this.rateLimited = rateLimited;
    }
  }
  public static class RoomListRejectedEvent {
    public final ClajConnection connection;
    public final ClajType type;
    public final boolean rateLimited;

    public RoomListRejectedEvent(ClajConnection connection, ClajType type, boolean rateLimited) {
      this.connection = connection;
      this.type = type;
      this.rateLimited = rateLimited;
    }
  }
}
