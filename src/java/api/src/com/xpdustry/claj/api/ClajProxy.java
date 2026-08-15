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

package com.xpdustry.claj.api;

import java.nio.ByteBuffer;

import arc.func.Cons;
import arc.func.Cons2;
import arc.net.DcReason;
import arc.util.Time;

import com.xpdustry.claj.api.net.ProxyClient;
import com.xpdustry.claj.api.net.VirtualConnection;
import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.ClajPackets.Connect;
import com.xpdustry.claj.common.ClajPackets.Disconnect;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.ClajType;
import com.xpdustry.claj.common.status.CloseReason;


/** The claj client that redirects packets from the relay to the local mindustry server. */
public class ClajProxy extends ProxyClient {
  /** Constant value saying that no room is created. This should be handled as an invalid id. */
  public static final long UNCREATED_ROOM = 0;
  /**
   * Id meaning that that the connection is invalid, and used to broadcast packets to all clients. <br>
   * This is used for disconnect, received and idle events, but not for connected one, as it makes no sense.
   */
  public static final int CON_BROADCAST = 0;

  public final ClajProvider provider;
  public boolean isPublic, isProtected, allowStateRequests;
  public short roomPassword;
  /** Can be used to not call {@link #roomClosed} callback, if the error is already handled. */
  public boolean quietErrors;

  protected Cons<ClajLink> roomCreated;
  protected Cons<CloseReason> roomClosed;
  protected long roomId = UNCREATED_ROOM;
  protected ClajLink link;

  /** To check broadcast compatibility. */
  private boolean testingBroadcast;
  /**
   * Broadcast check is done asynchronously after created the room. <br>
   * This defines the timeout within to wait an error, in ms.
   */
  private long broadcastTestStart, broadcastTestTimeout = 1000;

  public ClajProxy(ClajProvider provider) {
    // Keep a big write buffer in case of a big traffic, ProxyClient will block if nearly full
    super(131072, 32768, new ClajClientSerializer());
    this.provider = provider;
    conListener = provider.getConnectionListener(this);
    errorHandler = e -> provider.handleProxyError(this, e);

    receiver.handle(Connect.class, this::requestRoomId);
    receiver.handle(Disconnect.class, _ -> runRoomClose(CloseReason.error));

    receiver.handle(ConnectionJoinPacket.class, p -> conConnected(p.conID, p.addressHash));
    receiver.handle(ConnectionClosedPacket.class, p -> conDisconnected(p.conID, p.reason));
    receiver.handle(ConnectionPayloadPacket.class, p -> conReceived(p.conID, p.object));
    receiver.handle(ConnectionIdlingPacket.class, p -> conIdle(p.conID));

    receiver.handle(RoomClosedPacket.class, p -> runRoomClose(p.reason));
    receiver.handle(RoomLinkPacket.class, p -> runRoomCreated(p.roomId));
    receiver.handle(RoomStateRequestPacket.class, this::notifyRoomState);

    receiver.handle(ClajTextMessagePacket.class, p -> postTask(provider::showTextMessage, this, p.message));
    receiver.handle(ClajMessagePacket.class, p -> postTask(provider::showMessage, this, p.message));
    receiver.handle(ClajPopupPacket.class, p -> postTask(provider::showPopup, this, p.message));
  }

  /** This method must be used instead of others connect methods */
  public void connect(String host, int port, Cons<ClajLink> created, Cons<CloseReason> closed,
                      Cons<Throwable> failed) {
    try {
      connect(host, port);
      roomCreated = created;
      roomClosed = closed;
    } catch (Exception e) {
      runRoomClose(CloseReason.error);
      failed.get(e);
    } finally {
      quietErrors = false;
    }
  }

  // Helpers
  protected <T1, T2> void postTask(Cons2<T1, T2> consumer, T1 t1, T2 t2) { postTask(() -> consumer.get(t1, t2)); }
  protected <T> void postTask(Cons<T> consumer, T object) { postTask(() -> consumer.get(object)); }
  protected void postTask(Runnable run) { provider.postTask(run); }

  protected void runRoomCreated(long roomId) {
    if (roomCreated()) return;
    this.roomId = roomId;
    link = new ClajLink(connectHost.getHostName(), connectTcpPort, roomId);
    // 0 is not allowed since it's used to specify an uncreated room
    if (roomId == UNCREATED_ROOM) {
      runRoomClose(CloseReason.error);
      return;
    }
    if (roomCreated != null) postTask(roomCreated, link);
    notifyConfiguration();
    if (isPublic) notifyRoomState();

    // Check broadcast compatibility
    if (!broadcastSupported || testingBroadcast) return;
    broadcastTestStart = Time.millis();
    testingBroadcast = true;
    broadcastImpl(ByteBuffer.allocate(0), true);
  }

  /** This also resets room id and removes callbacks. */
  protected void runRoomClose(CloseReason reason) {
    roomId = UNCREATED_ROOM;
    link = null;
    if (!(quietErrors && reason == CloseReason.error) && roomClosed != null)
      postTask(roomClosed, reason);
    roomCreated = null;
    roomClosed = null;
    close();
    quietErrors = false;
    testingBroadcast = false;
  }

  /** {@code 0} means no room created. */
  public long roomId() {
    return roomId;
  }

  public boolean roomCreated() {
    return isConnected() && roomId != UNCREATED_ROOM;
  }

  public ClajLink link() {
    return link;
  }

  public void closeRoom() {
    closeRoom(null);
  }

  /** {@code null} reason means closed by user. */
  public void closeRoom(CloseReason reason) {
    if (!roomCreated()) return;
    closeAllConnections(DcReason.closed);
    send(makeRoomClosePacket());
    runRoomClose(reason);
  }

  public void requestRoomId() {
    testingBroadcast = false;
    if (roomCreated()) return;
    send(makeRoomCreatePacket(provider.getVersion().majorVersion, provider.getType()));
  }

  public void setDefaultConfiguration(boolean isPublic, boolean isProtected, short roomPassword,
                                      boolean allowStateRequests) {
    boolean wasPrivate = !this.isPublic;
    boolean notify = this.isPublic != isPublic
                  || this.isProtected != isProtected
                  || this.roomPassword != roomPassword
                  || this.allowStateRequests != allowStateRequests;
    this.isPublic = isPublic;
    this.isProtected = isProtected;
    this.roomPassword = roomPassword;
    this.allowStateRequests = allowStateRequests;
    if (notify) notifyConfiguration();
    if (wasPrivate && isPublic) notifyRoomState();
  }

  public void notifyConfiguration() {
    if (!roomCreated()) return;
    send(makeRoomConfigPacket(isPublic, isProtected, roomPassword, allowStateRequests));
  }

  public void notifyRoomState() {
    if (!roomCreated()) return;
    ByteBuffer state = allowStateRequests ? provider.writeRoomState(this) : null;
    if (state != null && state.remaining() > RoomStatePacket.MAX_BUFF_SIZE)
      throw new IllegalArgumentException("State size must be less than " + RoomStatePacket.MAX_BUFF_SIZE);
    send(makeRoomStatePacket(roomId, state));
  }

  // Region callbacks

  /** @return {@code null} if room isn't created or if {@code conId} is {@link #CON_BROADCAST}. */
  @Override
  protected VirtualConnection conConnected(int conId, long addressHash) {
    if (!roomCreated()) return null;
    // Of course broadcasting a connect event makes no sense.
    if (conId == CON_BROADCAST) return null;
    VirtualConnection con = getConnection(conId); // avoid multiple connect events
    return con == null ? super.conConnected(conId, addressHash) : con;
  }

  /** @return {@code null} if room isn't created or if {@code conId} is {@link #CON_BROADCAST}. */
  @Override
  protected VirtualConnection conDisconnected(int conId, DcReason reason) {
    if (!roomCreated()) return null;
    if (conId != CON_BROADCAST) return super.conDisconnected(conId, reason);
    if (testingBroadcast && reason == DcReason.error &&
        Time.timeSinceMillis(broadcastTestStart) < broadcastTestTimeout) {
      testingBroadcast = broadcastSupported = false;
      return null;
    }
    eachConnections(c -> closeQuietly(c, reason));
    return null;
  }

  /** @return {@code null} if room isn't created or if {@code conId} is {@link #CON_BROADCAST}. */
  @Override
  protected VirtualConnection conReceived(int conId, Object object) {
    if (!roomCreated()) return null;
    if (conId != CON_BROADCAST) return super.conReceived(conId, object);
    eachConnections(c -> c.notifyReceived0(object));
    return null;
  }

  /** @return {@code null} if room isn't created or if {@code conId} is {@link #CON_BROADCAST}. */
  @Override
  protected VirtualConnection conIdle(int conId) {
    if (!roomCreated()) return null;
    if (conId != CON_BROADCAST) return super.conIdle(conId);
    eachConnections(VirtualConnection::notifyIdle0);
    return null;
  }

  // Region packet making

  /** Packet ids for optimization. */
  private static final byte
      rsp = ClajNet.getId(RoomStatePacket.class),           rcp = ClajNet.getId(RoomConfigPacket.class),
      rrp = ClajNet.getId(RoomCreationRequestPacket.class), cpp = ClajNet.getId(ConnectionPayloadPacket.class),
      ccp = ClajNet.getId(ConnectionClosedPacket.class);

  protected Packet makeRoomStatePacket(long roomId, ByteBuffer state) {
    RoomStatePacket p = ClajNet.newLocalPacket(rsp);
    p.state = state;
    return p;
  }

  protected Packet makeRoomConfigPacket(boolean isPublic, boolean isProtected, short password,
                                        boolean requestState) {
    RoomConfigPacket p = ClajNet.newLocalPacket(rcp);
    p.isPublic = isPublic;
    p.isProtected = isProtected;
    p.password = password;
    p.requestState = requestState;
    return p;
  }

  protected Packet makeRoomCreatePacket(int version, ClajType type) {
    RoomCreationRequestPacket p = ClajNet.newLocalPacket(rrp);
    p.version = version;
    p.type = type;
    return p;
  }

  protected Packet makeRoomClosePacket() {
    return RoomClosureRequestPacket.instance;
  }

  @Override
  protected Packet makeBroadcastWrapPacket(Object object, boolean tcp) {
    return makeConWrapPacket(CON_BROADCAST, object, tcp);
  }

  @Override
  protected Packet makeBroadcastClosePacket(DcReason reason) {
    return makeConClosePacket(CON_BROADCAST, reason);
  }

  @Override
  protected Packet makeConWrapPacket(int conId, Object object, boolean tcp) {
    ConnectionPayloadPacket p = ClajNet.newLocalPacket(cpp);
    p.conID = conId;
    p.isTCP = tcp;
    p.object = object;
    return p;
  }

  @Override
  protected Packet makeConClosePacket(int conId, DcReason reason) {
    ConnectionClosedPacket p = ClajNet.newLocalPacket(ccp);
    p.conID = conId;
    p.reason = reason;
    return p;
  }
}
