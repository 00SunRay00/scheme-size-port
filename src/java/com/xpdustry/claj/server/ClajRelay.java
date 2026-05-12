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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;

import arc.*;
import arc.math.Mathf;
import arc.net.*;
import arc.struct.*;
import arc.util.*;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.net.*;
import com.xpdustry.claj.common.net.stream.*;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;
import com.xpdustry.claj.common.util.AddressUtil;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.ClajEvents.*;
import com.xpdustry.claj.server.util.NetworkSpeed;


/** CLaJ server main class that doing all the stuff. */
public class ClajRelay extends Server implements ApplicationListener {
  protected boolean closed;
  /** Read/Write speed. */
  public final NetworkSpeed networkSpeed;
  /** Server packet receiver. */
  protected final ServerReceiver receiver;
  /** Server routines that manages cache and cleaning things. */
  public final ClajRoutines routines;
  /** List of valid connections. DO NOT EDIT MANUALLY! */
  public final IntMap<ClajConnection> connections = new IntMap<>();
  /** List of created rooms. DO NOT EDIT MANUALLY! */
  public final LongMap<ClajRoom> rooms = new LongMap<>();
  /** Rooms sorted by type. DO NOT EDIT MANUALLY! */
  public final ObjectMap<ClajType, LongMap<ClajRoom>> types = new ObjectMap<>(8);
  /** As the server is very talkative, here is a field to shut up logs (excepts debug) for a time, if you want. */
  public volatile boolean beQuiet;
  /** Total number of clients in rooms. */
  protected int clientsInRooms;

  // Caches
  /**
   * Keeps a cache of packets received from connections that are not yet in a room. (queue of 2)<br>
   * Sometimes the join packet comes after other packets, and can lead to a client-side error/timeout.
   */
  private final IntMap<RawPacket[]> packetQueue = new IntMap<>();
  /** Size of the packet queue. */
  private final int packetQueueSize = 2, packetSizeInQueue = 1 << 13;
  /** As server version will not change at runtime, cache the serialized packet to avoid re-serialization. */
  private ByteBuffer versionBuff;
  /** Empty room list to send to client requesting no type or a not found one. */
  protected final RoomListPacket emptyList = new RoomListPacket().clear(true);

  public ClajRelay() { this(null); }
  public ClajRelay(NetworkSpeed speedCalculator) {
    super(32768, 32768, new ClajServerSerializer(speedCalculator));
    networkSpeed = speedCalculator;
    receiver = new ServerReceiver(this, Core.app::post);
    routines = new ClajRoutines();

    setDiscoveryHandler((_, r) -> {
      if (versionBuff == null)
        versionBuff = ByteBuffer.allocate(5).put(ClajNet.id).putInt(ClajVars.version.majorVersion);
      r.respond((ByteBuffer)versionBuff.rewind());
    });

    receiver.setFilter(new NetListenerFilter() {
      public boolean connected(Connection connection) { return isConnectAllowed(connection); }
      public boolean disconnected(Connection connection, DcReason reason) { return isDisconnectAllowed(connection, reason); }
      public boolean received(Connection connection, Object object) { return isReceiveAllowed(connection, object); }
    });

    receiver.handle(Connect.class, c -> onConnect(toClajCon(c)));
    receiver.handle(Disconnect.class, (c, p) -> onDisconnect(toClajCon(c), p.reason));
    receiver.handle(Idle.class, c -> onIdle(toClajCon(c)));

    receiver.handle(RoomCreationRequestPacket.class, (c, p) -> onRoomCreate(toClajCon(c), p.version, p.type));
    receiver.handle(RoomClosureRequestPacket.class, c -> onRoomClose(toClajCon(c)));
    receiver.handle(RoomJoinPacket.class, (c, p) ->
      onRoomJoin(toClajCon(c), false, p.roomId, p.type, p.withPassword, p.password));
    receiver.handle(RoomJoinRequestPacket.class, (c, p) ->
      onRoomJoin(toClajCon(c), true, p.roomId, p.type, p.withPassword, p.password));
    receiver.handle(RoomConfigPacket.class, (c, p) ->
      onRoomConfig(toClajCon(c), p.isPublic, p.isProtected, p.password, p.requestState, p.maxClients));
    receiver.handle(RoomStatePacket.class, (c, p) -> onRoomState(toClajCon(c), p.state));
    receiver.handle(RoomInfoRequestPacket.class, (c, p) -> onInfoRequest(toClajCon(c), p.roomId));
    receiver.handle(RoomListRequestPacket.class, (c, p) -> onListRequest(toClajCon(c), p.type));

    receiver.handle(ConnectionClosedPacket.class, (c, p) -> onConClose(toClajCon(c), p.conID, p.reason));
    //TODO: keep these two on the network thread for optimization?
    receiver.handle(ConnectionPacketWrapPacket.class, (c, p) -> onHostPacket(toClajCon(c), p));
    receiver.handle(RawPacket.class, (c, p) -> onConPacket(toClajCon(c), p));
  }

  // region logging

  protected void info(String text, Object... args) { if (!beQuiet) Log.info(text, args); }
  protected void warn(String text, Object... args) { if (!beQuiet) Log.warn(text, args); }

  // end region
  // region events

  /** Will also prepare the connection if valid. */
  public boolean isConnectAllowed(Connection connection) {
    if (connection == null) return false;
    String id = AddressUtil.encodeId(connection);
    String ip = AddressUtil.getString(connection);
    connection.setName("Connection " + id); // fix id format in stacktraces

    if (isClosed() || ip == null || ClajConfig.blacklist.contains(ip)) {
      connection.close(DcReason.closed);
      warn("Connection @ (@) rejected " +
           (isClosed() ? "because of a closed server." : "for a blacklisted address."), id, ip);
      return false;
    } else if (ClajConfig.maxConnections.get() > 0 && connections.size >= ClajConfig.maxConnections.get()) {
      connection.close(DcReason.closed);
      warn("Connection @ (@) rejected because the server is full.", id, ip);
      return false;
    }

    Log.debug("Connection @ (@) received.", id, ip);
    connection.setArbitraryData(new ClajConnection(connection));
    return true;
  }

  /** Weird name =/ */
  @SuppressWarnings("null")
  public boolean isDisconnectAllowed(Connection connection, DcReason reason) {
    if (connection == null) return false;
    ClajConnection con = toClajCon(connection);
    boolean valid = con != null;
    String id = valid ? con.sid : AddressUtil.encodeId(connection);
    String ip = valid ? con.saddress : AddressUtil.getString(connection);
    Log.debug("Connection @ (@) lost: @.", id, ip, reason);
    if (StreamReceiver.has(connection))
      Core.app.post(() -> StreamReceiver.reset(connection));
    // Because speed is calculated between calls, if there is no traffic, the values remains stuck at last update
    if (networkSpeed != null) {
      networkSpeed.uploadMark(0);
      networkSpeed.downloadMark(0);
    }
    // Avoid searching for a room if it was an invalid connection or just a ping
    return valid;
  }

  public boolean isReceiveAllowed(Connection connection, Object object) {
    ClajConnection con = toClajCon(connection);
    if (con == null) return false;
    // Compatibility with the xzxADIxzx's version
    if (object instanceof String) {
      rejectObsoleteClient(con);
      return false;
    }
    return checkRateLimit(con);
  }

  public void onConnect(ClajConnection connection) {
    if (connection == null) return;
    connections.put(connection.id, connection);
    Events.fire(new ClientConnectedEvent(connection));
  }

  public void onDisconnect(ClajConnection connection, DcReason reason) {
    if (connection == null) return;
    Events.fire(new ClientDisonnectedEvent(connection, reason));
    connections.remove(connection.id);

    ClajRoom room = connection.room;
    if (removeClient(connection, reason)){
      info("Room @ closed because connection @ (the host) has disconnected.", room.sid, connection.sid);
    } else if (room != null) {
      info("Connection @ left the room @.", connection.sid, room.sid);
    }
  }

  public void onIdle(ClajConnection connection) {
    if (connection == null) return;
    if (connection.room != null) connection.room.idle(connection);
    // No event for that, this is received to many times
  }

  /** @return not {@code null} if action was denied. */
  public CloseReason onRoomCreate(ClajConnection connection, int version, ClajType type) {
    if (connection == null) return CloseReason.error;
    // Ignore room creation requests when the server is closing
    if (isClosed()) {
      rejectRoomCreation(connection, CloseReason.serverClosed);
      warn("Connection @ tried to create a room but the server is closed.", connection.sid);
      return CloseReason.serverClosed;

    } else if (ClajConfig.maxRooms.get() > 0 && rooms.size >= ClajConfig.maxRooms.get()) {
      rejectRoomCreation(connection, CloseReason.serverFull);
      warn("Connection @ tried to create a room but the server is full.", connection.sid);
      return CloseReason.serverFull;

    } else if (version != ClajVars.version.majorVersion) {
      boolean isGreater = version > ClajVars.version.majorVersion;
      CloseReason reason = isGreater ? CloseReason.outdatedServer : CloseReason.outdatedClient;
      rejectRoomCreation(connection, reason);
      warn("Connection @ tried to create a room but has " + (isGreater ? "a too recent" : "an outdated") +
           " version. (was: @)", connection.sid, version);
      return reason;

    } else if (type != null && ClajConfig.typeBlacklist.contains(type)) {
      rejectRoomCreation(connection, CloseReason.blacklisted);
      warn("Connection @ tried to create a room but his implementation is blacklisted. (was: @)",
           connection.sid, type);
      return CloseReason.blacklisted;

    } else if (!routines.getAddressRate(connection).allowCreate()) {
      rejectRoomCreation(connection, CloseReason.serverFull); // act as server full
      warn("Connection @ tried to create a room but reached the limit per IP.", connection.sid);
      return CloseReason.closed;
    }

    ClajRoom room = connection.room;
    // Ignore if the connection is already in a room or hold one
    if (room != null) {
      denyAction(connection, room, MessageType.alreadyHosting);
      warn("Connection @ tried to create a room but is already hosting the room @.", connection.sid, room.sid);
      return CloseReason.error;
    }

    room = createRoom(connection, type);
    info("Room @ created by connection @.", room.sid, connection.sid);
    return null;
  }

  /** @return whether the action was allowed or not. */
  public boolean onRoomClose(ClajConnection connection) {
    if (checkRoomHost(
        connection,
        MessageType.roomClosureDenied,
        "Connection @ tried to close the room @ but is not the host."
    )) return false;

    ClajRoom room = connection.room;
    closeRoom(room);
    info("Room @ closed by connection @ (the host).", room.sid, connection.sid);
    return true;
  }

  /** @return not {@code null} if the action was denied. */
  public RejectReason onRoomJoin(ClajConnection connection, boolean isRequest, long roomId, ClajType type,
                                 boolean withPassword, short password) {
    if (connection == null) return RejectReason.error;
    ClajRoom room = connection.room;

    // Disconnect from a potential another room.
    if (room != null) {
      // Ignore if it's the host of another room
      if (room.isHost(connection)) {
        denyAction(connection, room, MessageType.alreadyHosting);
        warn("Connection @ tried to join the room @ but is already hosting the room @.", connection.sid,
             Strings.longToBase64(roomId), room.sid);
        return RejectReason.error;
      }
      room.disconnected(connection, DcReason.closed);
    }

    room = getRoom(roomId);

    // Check room accessibility
    if (isClosed()) {
      if (isRequest) rejectRoomJoin(connection, room, roomId, RejectReason.serverClosing);
      else connection.close(DcReason.error);
      warn("Connection @ tried to join the room @ but the server is closed.", connection.sid,
           room == null ? Strings.longToBase64(roomId) : room.sid);
      return RejectReason.serverClosing;

    } else if (room == null) {
      if (isRequest) rejectRoomJoin(connection, room, roomId, RejectReason.roomNotFound);
      else connection.close(DcReason.error);
      warn("Connection @ tried to join a not found room. (id: @)", connection.sid, Strings.longToBase64(roomId));
      return RejectReason.roomNotFound;

    // Limit to avoid room searching
    } else if (!routines.getAddressRate(connection).allowJoin()) {
      // Act same way as not found
      if (isRequest) rejectRoomJoin(connection, room, RejectReason.roomNotFound);
      else connection.close(DcReason.error);
      warn("Connection @ tried to join the room @ but was rate limited.", connection.sid, room.sid);
      return RejectReason.roomNotFound;

    } else if (!room.allowsType(type)) {
      if (isRequest) rejectRoomJoin(connection, room, RejectReason.incompatible);
      else connection.close(DcReason.error);
      warn("Connection @ tried to join the room @ but has an incompatible type. (was: @, need: @)",
           connection.sid, room.sid, type, room.type);
      return RejectReason.incompatible;

    } else if (room.isProtected && !withPassword) {
      if (isRequest) rejectRoomJoin(connection, room, RejectReason.passwordRequired);
      else connection.close(DcReason.error);
      warn("Connection @ tried to join the room @ but a password is needed.", connection.sid, room.sid);
      return RejectReason.incompatible;

    } else if (room.isProtected && room.password != password) {
      if (isRequest) rejectRoomJoin(connection, room, RejectReason.invalidPassword);
      else connection.close(DcReason.error);
      warn("Connection @ tried to join the room @ but used the wrong password.", connection.sid, room.sid);
      return RejectReason.invalidPassword;

    } else if (room.maxClients > 0 && room.clients.size >= room.maxClients) {
      if (isRequest) rejectRoomJoin(connection, room, RejectReason.roomFull);
      else connection.close(DcReason.error);
      warn("Connection @ tried to join the room @ but it is full.", connection.sid, room.sid);
      return RejectReason.roomFull;

    // Stop here if it's a request
    } else if (isRequest) {
      acceptJoinRequest(connection, room);
      Log.debug("Connection @ validated its join request to the room @.", connection.sid, room.sid);
      return null;
    }

    addClient(room, connection);
    info("Connection @ joined the room @. (type: @)", connection.sid, room.sid, type);
    handleQueue(connection, room);
    return null;
  }

  /** @return whether the action was allowed or not. */
  public boolean onRoomConfig(ClajConnection connection, boolean isPublic, boolean isProtected, short password,
                              boolean requestState, int maxClients) {
    if (checkRoomHost(
        connection,
        MessageType.configureDenied,
        "Connection @ tried to configure the room @ but is not the host."
    )) return false;

    ClajRoom room = connection.room;
    setRoomConfiguration(room, isPublic, isProtected, password, requestState, maxClients);
    info("Connection @ (the host) changed configuration of room @.", connection.sid, room.sid);
    return true;
  }

  /** @return whether the action was allowed or not. */
  public boolean onRoomState(ClajConnection connection, ByteBuffer state) {
    if (checkRoomHost(
        connection,
        MessageType.statingDenied,
        "Connection @ tried to set state of room @ but is not the host."
    )) return false;

    ClajRoom room = connection.room;

    int limit = ClajConfig.stateLimit.get();
    if (limit > 0 && !room.stateRate.allow(60000L, limit)) {
      warn("Connection @ (the host) tried to change state of room @ but was rate limited.", connection.sid,
           room.sid);
      return false;
    }

    setRoomState(room, state);
    info("Connection @ (the host) changed the state of room @.", connection.sid, room.sid);
    sendRoomState(room);
    sendRoomList(room.type);
    return true;
  }

  /** @return whether the action was allowed or not. */
  public boolean onInfoRequest(ClajConnection connection, long roomId) {
    if (connection == null) return false;
    else if (!routines.getAddressRate(connection).allowInfo()) {
      rejectRoomInfo(connection, null, true);
      warn("Connection @ tried to get state of room @ but was rate limited.", connection.sid,
           Strings.longToBase64(roomId));
      return false;
    }

    ClajRoom room = getRoom(roomId);
    if (room == null) {
      rejectRoomInfo(connection, null, false);
      warn("Connection @ tried to get state of a not found room. (id: @)", connection.sid,
           Strings.longToBase64(roomId));
      return false;
    } else if (room.shouldRequestState() && room.isStateOutdated()) {
      requestRoomState(connection, room);
      info("Connection @ requested state of room @ but current one is " +
           (room.requestingState ? "in progress" : "outdated."), connection.sid, room.sid);
      return false;
    } else {
      room.sendRoomState(connection);
      info("Connection @ requested state of room @.", connection.sid, room.sid); //TODO: maybe debug
      return true;
    }
  }

  /** @return whether the action was allowed or not. */
  public boolean onListRequest(ClajConnection connection, ClajType type) {
    if (connection == null) return false;
    else if (!routines.getAddressRate(connection).allowList()) {
      rejectRoomList(connection, type, true);
      if (type != null)
        warn("Connection @ tried to get room list of type @ but was rate limited.", connection.sid, type);
      return false;
    }

    //TODO: maybe debug?
    switch (requestRoomList(connection, type)) {
      case 0 ->
        info("Connection @ requested room list of type @ but the current one is oudated.", connection.sid, type);
      case 1 ->
        info("Connection @ requested room list of type @.", connection.sid, type);
      case 2 ->
        info("Connection @ requested room list of type @ but the current one is not finished.", connection.sid, type);
      case 3 ->
        warn("Connection @ requested room list of type @ but the request limit is reached.", connection.sid, type);
      case 4 -> {
        if (type == null) break;
        warn("Connection @ tried to get room list of a not found type @.", connection.sid, type);
      }
    }
    return true;
  }

  /**
   * Will not notify the host about closing.
   * @return whether the action was allowed or not.
   */
  public boolean onConClose(ClajConnection connection, int conId, DcReason reason) {
    String tsid = AddressUtil.encodeId(conId);

    if (checkRoomHost(
        connection,
        MessageType.conClosureDenied,
        "Connection @ from room @ tried to close connection @ but is not the host.", tsid
    )) return false;

    ClajConnection target = connections.get(conId);
    ClajRoom room = connection.room;

    // Ignore when trying to close itself or one that not in the same room
    if (target == null || target == connection || !room.contains(target)) {
      denyAction(connection, room, MessageType.conClosureDenied);
      warn("Connection @ from room @ tried to close a " +
           (target == null ? "not found connection" : "connection of another room") + ". (id: @)",
           connection.sid, room.sid, tsid);
      return false;
    }

    info("Connection @ (the host) from room @ closed connection @.", connection.sid, room.sid, tsid);
    room.disconnectedQuietly(target, reason);
    target.close(reason);
    return true;
  }

  public boolean onHostPacket(ClajConnection connection, ConnectionPacketWrapPacket packet) {
    if (connection == null) return false;
    ClajRoom room = connection.room;
    if (room == null) return false;
    room.received(connection, packet);
    return true;
  }

  public void onConPacket(ClajConnection connection, RawPacket packet) {
    if (connection == null) return;
    else if (connection.room != null) connection.room.received(connection, packet);
    else addQueue(connection, packet);
  }

  // end region
  // region hosting

  @Override
  public void init() {
    Events.on(ClajEvents.ServerLoadedEvent.class, _ -> host(ClajVars.port));
  }

  /** At this point it's too late to notify closure. */
  @Override
  public void dispose() {
    if (!closed) {
      closed = true;
      Events.fire(new ServerStoppingEvent(false));
      clearAndStop();
    }
    try { super.dispose(); }
    catch (Exception _) {}
    Log.info("Server disposed.");
  }

  public void host(int port) throws RuntimeException {
    stop(false);
    try { bind(port, port); }
    catch (BindException e) {
      throw new RuntimeException("Port " + port + " already in use! "
                               + "Make sure no other servers are running on the same port.");
    } catch (IOException e) { throw new UncheckedIOException(e); }

    Threads.daemon("CLaJ Relay", () -> {
      try { run(); }
      catch (Throwable th) {
        if(!(th instanceof ClosedSelectorException)) {
          Threads.throwAppException(th);
          return;
        }
      }
      Log.info("Server stopped.");
    });
  }

  @Override
  public void run() {
    closed = false;
    super.run();
  }

  @Override
  public void stop() { stop(false); }
  public void stop(boolean notify) { stop(false, null); }
  public void stop(Runnable stopped) { stop(true, stopped); }
  public void stop(boolean notify, Runnable stopped) {
    if (closed) {
      clearAndStop();
      return;
    }
    closed = true;
    if (!notify) {
      clearAndStop();
      if (stopped != null) stopped.run();
      return;
    }
    notifyStop(() -> {
      clearAndStop();
      if (stopped != null) stopped.run();
    });
  }

  /** Will notify stopping and wait a little before running callback. (if configured for) */
  public void notifyStop(Runnable notified) {
    Events.fire(new ServerStoppingEvent(true));
    if (ClajConfig.warnClosing.get() && !rooms.isEmpty()) {
      float wait = ClajConfig.closeWait.get();
      Log.info("Notifying server closure to rooms... The server will exit in @s.", wait);
      rooms.eachValue(r -> r.message(MessageType.serverClosing));
      Timer.schedule(notified, wait);
    } else notified.run();
  }

  protected void clearAndStop() {
    closeRooms();
    super.stop();
  }

  public boolean isClosed() {
    return closed;
  }

  public void closeRooms() { closeRooms(CloseReason.serverClosed); }
  public void closeRooms(CloseReason reason) {
    packetQueue.clear();
    routines.clearCaches((c, r) -> rejectRoomInfo(c, getRoom(r), false));
    rooms.eachValue(r -> r.close(reason));
    rooms.clear();
    types.clear();
    clientsInRooms = 0;
  }

  /** Creates a room with it's associated caches. */
  public ClajRoom createRoom(ClajConnection host, ClajType type) {
    ClajRoom room = newRoom(host, type);
    rooms.put(room.id, room);
    if (type != null) types.get(type, LongMap::new).put(room.id, room);
    room.create();
    routines.scheludeRoomAfk(room, () -> {
      closeRoom(room, CloseReason.afk);
      info("Room @ closed due to a long period without anyone joining in.", room.sid);
    });
    clientsInRooms++;
    return room;
  }

  public void closeRoom(ClajRoom room) { closeRoom(room, null); }
  public void closeRoom(ClajRoom room, CloseReason reason) { closeRoom(room, reason, true); }
  protected void closeRoom(ClajRoom room, CloseReason reason, boolean removeFromTypes) {
    for (ClajConnection c : room.clients.values()) removeQueue(c);
    clientsInRooms -= room.clients.size + 1;
    rooms.remove(room.id);

    boolean removeList = false;
    if (removeFromTypes && room.type != null) {
      LongMap<ClajRoom> r = types.get(room.type);
      if (r != null) {
        r.remove(room.id);
        if (r.isEmpty()) {
          types.remove(room.type);
          removeList = true;
        }
      }
    }

    routines.getAddressRate(room.host).removeRoom();
    routines.cancelRoomAfk(room);
    Seq<ClajConnection> cons = routines.getPendingRoomRequestsForSend(room);
    if (cons != null) cons.each(c -> rejectRoomInfo(c, room, false));

    if (removeFromTypes) routines.clearRoomCache(room, removeList);
    else routines.cancelRoomInfoTask(room);

    if (reason == null) room.close();
    else room.close(reason);
  }

  public int closeRoomList(ClajType type) { return closeRoomList(type, null); }
  /** @return the number of room that has been closed. {@code -1} if no room have the type. */
  public int closeRoomList(ClajType type, CloseReason reason) {
    if (type == null) return -1;
    LongMap<ClajRoom> rooms = types.remove(type);
    if (rooms == null) return -1;
    int number = rooms.size;
    rooms.eachValue(r -> closeRoom(r, reason, false));
    routines.clearRoomListCache(type);
    return number;
  }

  public void addClient(ClajRoom room, ClajConnection con) {
    if (con == null) return;
    room.connected(con);
    routines.cancelRoomAfk(room);
    clientsInRooms++;
  }

  /** @return whether client was the host. If so the room will be closed. */
  public boolean removeClient(ClajConnection con, DcReason reason) {
    if (con == null) return false;
    removeQueue(con);
    routines.clearClientCache(con);
    ClajRoom room = con.room;
    if (room != null) {
      room.disconnected(con, reason);
      // Close the room if it was the host
      if (room.isHost(con)) {
        closeRoom(room);
        return true;
      }
      routines.scheludeRoomAfk(room, () -> {
        closeRoom(room, CloseReason.afk);
        info("Room @ closed due to a long period without anyone joining in.", room.sid);
      });
      clientsInRooms--;
    }
    return false;
  }

  public void setRoomConfiguration(ClajRoom room, boolean isPublic, boolean isProtected, short password,
                                   boolean requestState, int maxClients) {
    room.setConfiguration(isPublic, isProtected, password, requestState, maxClients);
    routines.updateRoom(room, false);
  }

  public void setRoomState(ClajRoom room, ByteBuffer state) {
    room.setState(state);
    routines.updateRoom(room, true);
  }

  /** Requests a room state (if not already) and adds the connection to the pending requests cache. */
  public boolean requestRoomState(ClajConnection con, ClajRoom room) {
    Seq<ClajConnection> cons = routines.getPendingRoomRequests(room);
    int limit = ClajConfig.infoRequestLimit.get();
    if (limit > 0 && cons.size >= limit) {
      rejectRoomInfo(con, room, true);
      return false;
    }
    cons.add(con);
    return routines.requestRoomState(room, this::sendRoomState);
  }

  /**
   * Send room state to connections that awaiting it.
   * @return whether any connections are waiting for the state.
   */
  public boolean sendRoomState(ClajRoom room) {
    Seq<ClajConnection> cons = routines.getPendingRoomRequestsForSend(room);
    if (cons == null) return false;
    Log.debug("Sending state of room @ to @ pending request" + (cons.size > 1 ? "s..." : "..."),
              room.sid, cons.size);
    routines.cancelRoomInfoTask(room);
    cons.each(room::sendRoomState);
    return true;
  }

  /**
   * Requests a room list (if not already) and adds the connection to the pending requests cache.
   * @return the state value. (0: refreshing, 1: up to date, 2: updating, 3: pending request limit, 4: type not found)
   */
  public int requestRoomList(ClajConnection con, ClajType type) {
    return routines.requestRoomList(con, type, rooms, r -> rejectRoomList(con, type, r));
  }

  public boolean sendRoomList(ClajType type) { return sendRoomList(type, false); }
  public boolean sendRoomList(ClajType type, boolean force) {
    return routines.sendRoomList(type, force);
  }

  public boolean refreshRoomList(ClajType type) { return refreshRoomList(type, false); }
  public boolean refreshRoomList(ClajType type, boolean force) {
    return routines.refreshRoomList(type, force, types.getNull(type));
  }

  public void refreshRoomLists() {
    types.each(routines::refreshRoomList);
  }


  public void denyAction(ClajConnection con, ClajRoom room, MessageType type) {
    room.message(type);
    Events.fire(new ActionDeniedEvent(con, room, type));
  }

  protected boolean checkRoomHost(ClajConnection con, MessageType errType, String errMsg) {
    return checkRoomHost(con, errType, errMsg, null);
  }
  protected boolean checkRoomHost(ClajConnection con, MessageType errType, String errMsg, Object extra) {
    if (con == null) return true;
    ClajRoom room = con.room;
    if (room == null) return true;
    if (room.isHost(con)) return false;
    denyAction(con, room, errType);
    if (extra == null) warn(errMsg, con.sid, room.sid);
    else warn(errMsg, con.sid, room.sid, extra);
    return true;
  }

  /**
   * Simple packet spam protection.
   * @return whether the packet is allowed or not. If not, the client will be kicked and the room warned.
   */
  public boolean checkRateLimit(ClajConnection con) {
    if (con == null) return true;
    int limit = con.isRoomHost() ? ClajConfig.hostSpamLimit.get() * con.room.clients.size : ClajConfig.spamLimit.get();
    boolean isRated = limit > 0 && !con.packetRate.allow(3000L, limit);
    if (isRated) {
      if (con.isRoomHost()) rejectRateLimitedHost(con.room);
      else rejectRateLimitedClient(con);
    }
    return !isRated;
  }

  public boolean addQueue(ClajConnection con, RawPacket packet) {
    return addQueue(con.connection, packet);
  }
  /** @return whether a slot was found or not. */
  public boolean addQueue(Connection con, RawPacket packet) {
    if (packet.data.remaining() >= packetSizeInQueue) {
      removeQueue(con);
      con.close(DcReason.error);
      Log.debug("Connection @ kicked for sending too big packets in the queue.", AddressUtil.encodeId(con));
      return false;
    }

    RawPacket[] queue = packetQueue.get(con.getID(), () -> new RawPacket[packetQueueSize]);
    for (int i=0; i<queue.length; i++) {
      if (queue[i] == null) {
        queue[i] = packet;
        return true;
      }
    }
    return false;
  }

  /** @return whether a queue was removed or not. */
  public boolean removeQueue(ClajConnection con) {
    return packetQueue.remove(con.id) != null;
  }

  /** @return whether a queue was removed or not. */
  public boolean removeQueue(Connection con) {
    return packetQueue.remove(con.getID()) != null;
  }

  /** @return whether the queue has been send to the room host, or not (because no packet was queued). */
  public boolean handleQueue(ClajConnection con, ClajRoom room) {
    RawPacket[] queue = packetQueue.remove(con.id);
    if (queue != null) {
      Log.debug("Sending queued packets of connection @ to room host.", con.sid);
      for (RawPacket element : queue) {
        if (element != null) room.received(con.connection, element);
      }
      return true;
    }
    return false;
  }

  // end region
  // region packet sending

  /** As this is mainly called from network thread, it will be posted to the main thread. */
  public void rejectObsoleteClient(ClajConnection connection) {
    if (ClajConfig.warnDeprecated.get()) {
      connection.send("[scarlet][[CLaJ Server]:[] Your CLaJ version is obsolete! "
                    + "Please upgrade it by installing the 'claj' mod, in the mod browser.");
      Core.app.post(() -> {
        warn("Connection @ tried to create a room but has an incompatible version.", connection.sid);
        Events.fire(new RoomCreationRejectedEvent(connection, CloseReason.obsoleteClient));
      });
    }
    connection.close(DcReason.error);
  }

  /** As this is mainly called from network thread, it will be posted to the main thread. */
  public void rejectRateLimitedClient(ClajConnection connection) {
    ClajRoom room = connection.room;
    connection.close(); // close now, event will be discarded if received twice
    Core.app.post(() -> {
      if (room != null) {
        room.message(MessageType.packetSpamming);
        room.disconnected(connection, DcReason.closed);
      }
      warn("Connection @ (@) disconnected for packet spamming.", connection.sid, connection.address);
      Events.fire(new ClientKickedEvent(connection));
    });
  }

  /** As this is mainly called from network thread, it will be posted to the main thread. */
  public void rejectRateLimitedHost(ClajRoom room) {
    if (room == null) return;
    // We cannot close the room now, so just kick everyone to be safe and close after
    for (ClajConnection c : room.clients.values()) c.close();
    Core.app.post(() -> {
      closeRoom(room, CloseReason.spam);
      warn("Room @ closed for packet spamming.", room.sid);
      Events.fire(new HostKickedEvent(room));
    });
  }

  public void rejectRoomCreation(ClajConnection connection, CloseReason reason) {
    RoomClosedPacket p = new RoomClosedPacket();
    p.reason = reason;
    connection.send(p);
    Events.fire(new RoomCreationRejectedEvent(connection, reason));
    connection.close();
  }

  public void rejectRoomJoin(ClajConnection connection, ClajRoom room, RejectReason reason) {
    rejectRoomJoin(connection, room, room.id, reason);
  }
  protected void rejectRoomJoin(ClajConnection connection, ClajRoom room, long roomId, RejectReason reason) {
    RoomJoinDeniedPacket p = new RoomJoinDeniedPacket();
    p.roomId = room == null ? roomId : room.id;
    p.reason = reason;
    connection.send(p);
    Events.fire(new ConnectionJoinRejectedEvent(connection, room, reason));
    connection.close();
  }

  public void acceptJoinRequest(ClajConnection connection, ClajRoom room) {
    RoomJoinAcceptedPacket p = new RoomJoinAcceptedPacket();
    p.roomId = room.id;
    connection.send(p);
    Events.fire(new ConnectionPreJoinEvent(connection, room));
  }

  public void rejectRoomInfo(ClajConnection connection, ClajRoom room, boolean rateLimited) {
    connection.send(RoomInfoDeniedPacket.instance);
    Events.fire(new RoomInfoRejectedEvent(connection, room, rateLimited));
    connection.close();
  }

  public void rejectRoomList(ClajConnection connection, ClajType type, boolean rateLimited) {
    connection.send(emptyList);
    Events.fire(new RoomListRejectedEvent(connection, type, rateLimited));
    //connection.close(); // closing can be handled as rejected
  }

  // end region
  // region getters

  public int clientsInRooms() {
    return clientsInRooms;
  }

  public long newRoomId() {
    long id;
    /* re-roll if 0 because it's used to specify an uncreated room. */
    do { id = Mathf.rand.nextLong(); }
    while (id == 0 || rooms.containsKey(id));
    return id;
  }

  public ClajRoom newRoom(ClajConnection host, ClajType type) {
    return new ClajRoom(newRoomId(), host, type);
  }

  public ClajRoom getRoom(long roomId) {
    return rooms.get(roomId);
  }

  /** Try to find a room using the base64 encoded id. */
  public ClajRoom getRoom(String encodedRoomId) {
    try { return getRoom(Strings.base64ToLong(encodedRoomId)); }
    catch (Exception _) { return null; }
  }

  public static ClajConnection toClajCon(Connection con) {
    return con != null && con.getArbitraryData() instanceof ClajConnection ccon ? ccon : null;
  }

  // end region
}