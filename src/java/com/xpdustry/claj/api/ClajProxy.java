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
import arc.net.DcReason;

import com.xpdustry.claj.api.net.ProxyClient;
import com.xpdustry.claj.api.net.VirtualConnection;
import com.xpdustry.claj.common.ClajPackets.Connect;
import com.xpdustry.claj.common.ClajPackets.Disconnect;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.ClajType;
import com.xpdustry.claj.common.status.CloseReason;


/** The claj client that redirects packets from the relay to the local mindustry server. */
public class ClajProxy extends ProxyClient {
  /** Constant value saying that no room is created. This should be handled as an invalid id. */
  public static final long UNCREATED_ROOM = 0;

  public final ClajProvider provider;
  public boolean isPublic, isProtected, allowStateRequests;
  public short roomPassword;
  /** Can be used to not call {@link #roomClosed} callback, if the error is already handled. */
  public boolean quietErrors;

  protected Cons<ClajLink> roomCreated;
  protected Cons<CloseReason> roomClosed;
  protected long roomId = UNCREATED_ROOM;
  protected ClajLink link;

  public ClajProxy(ClajProvider provider) {
    super(32768, 16384, new ClajClientSerializer(), provider::postTask);
    this.provider = provider;
    conListener = provider.getConnectionListener(this);
    errorHandler = e -> provider.handleProxyError(this, e);

    receiver.handle(Connect.class, this::requestRoomId);
    receiver.handle(Disconnect.class, _ -> runRoomClose(CloseReason.error));

    receiver.handle(ConnectionJoinPacket.class, p -> conConnected(p.conID, p.addressHash));
    receiver.handle(ConnectionClosedPacket.class, p -> conDisconnected(p.conID, p.reason));
    receiver.handle(ConnectionPacketWrapPacket.class, p -> conReceived(p.conID, p.object));
    receiver.handle(ConnectionIdlingPacket.class, p -> conIdle(p.conID));

    receiver.handle(RoomClosedPacket.class, p -> runRoomClose(p.reason));
    receiver.handle(RoomLinkPacket.class, p -> runRoomCreated(p.roomId));
    receiver.handle(RoomStateRequestPacket.class, this::notifyRoomState);

    receiver.handle(ClajTextMessagePacket.class, p -> provider.showTextMessage(this, p.message));
    receiver.handle(ClajMessagePacket.class, p -> provider.showMessage(this, p.message));
    receiver.handle(ClajPopupPacket.class, p -> provider.showPopup(this, p.message));
  }

  /** This method must be used instead of others connect methods */
  public void connect(String host, int port, Cons<ClajLink> created, Cons<CloseReason> closed, Cons<Throwable> failed) {
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
  protected <T> void postTask(Cons<T> consumer, T object) { postTask(() -> consumer.get(object)); }
  protected void postTask(Runnable run) { provider.postTask(run); }

  protected void runRoomCreated(long roomId) {
    if (roomCreated()) return;
    this.roomId = roomId;
    link = new ClajLink(connectHost.getHostName(), connectTcpPort, roomId);
    // 0 is not allowed since it's used to specify an uncreated room
    if (roomId == UNCREATED_ROOM) return;
    if (roomCreated != null) postTask(roomCreated, link);
    notifyConfiguration();
    if (isPublic) notifyRoomState();
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
  }

  /** {@code 0} means no room created. */
  public long roomId() {
    return roomId;
  }

  public boolean roomCreated() {
    return roomId != UNCREATED_ROOM;
  }

  public ClajLink link() {
    return link;
  }

  @Override
  public void close() {
    if (isConnected()) closeRoom();
    super.close();
  }

  public void closeRoom() {
    closeRoom(null);
  }

  /** {@code null} reason means closed by user. */
  public void closeRoom(CloseReason reason) {
    if (!roomCreated()) return;
    sendTCP(makeRoomClosePacket());
    runRoomClose(reason);
  }

  public void requestRoomId() {
    if (roomCreated()) return;
    sendTCP(makeRoomCreatePacket(provider.getVersion().majorVersion, provider.getType()));
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
    if (wasPrivate && this.isPublic) notifyRoomState();
  }

  public void notifyConfiguration() {
    if (!roomCreated()) return;
    sendTCP(makeRoomConfigPacket(isPublic, isProtected, roomPassword, allowStateRequests));
  }

  public void notifyRoomState() {
    if (!roomCreated()) return;
    ByteBuffer state = allowStateRequests ? provider.writeRoomState(this) : null;
    Packet p = makeRoomStatePacket(roomId, state);
    if (state == null) {
      sendTCP(p);
      return;
    }
    state.flip();
    if (state.remaining() >= RoomStatePacket.MAX_BUFF_SIZE)
      throw new IllegalArgumentException("Buffer size must be less than " + RoomStatePacket.MAX_BUFF_SIZE);
    sendTCP(p);
  }

  protected Packet makeRoomStatePacket(long roomId, ByteBuffer state) {
    RoomStatePacket p = new RoomStatePacket();
    p.state = state;
    return p;
  }

  protected Packet makeRoomConfigPacket(boolean isPublic, boolean isProtected, short password, boolean requestState) {
    RoomConfigPacket p = new RoomConfigPacket();
    p.isPublic = isPublic;
    p.isProtected = isProtected;
    p.password = password;
    p.requestState = requestState;
    return p;
  }

  protected Packet makeRoomCreatePacket(int version, ClajType type) {
    RoomCreationRequestPacket p = new RoomCreationRequestPacket();
    p.version = version;
    p.type = type;
    return p;
  }

  protected Packet makeRoomClosePacket() {
    return RoomClosureRequestPacket.instance;
  }

  @Override
  protected Packet makeConWrapPacket(int conId, Object object, boolean tcp) {
    ConnectionPacketWrapPacket p = new ConnectionPacketWrapPacket();
    p.conID = conId;
    p.isTCP = tcp;
    p.object = object;
    return p;
  }

  @Override
  protected Packet makeConClosePacket(int conId, DcReason reason) {
    ConnectionClosedPacket p = new ConnectionClosedPacket();
    p.conID = conId;
    p.reason = reason;
    return p;
  }

  @Override
  protected VirtualConnection conConnected(int conId, long addressHash) {
    if (!roomCreated()) return null;
    VirtualConnection con = getConnection(conId);
    return con == null ? super.conConnected(conId, addressHash) : con;
  }

  @Override
  protected VirtualConnection conDisconnected(int conId, DcReason reason) {
    return roomCreated() ? super.conDisconnected(conId, reason) : null;
  }

  @Override
  protected VirtualConnection conReceived(int conId, Object object) {
    return roomCreated() ? super.conReceived(conId, object) : null;
  }

  @Override
  protected VirtualConnection conIdle(int conId) {
    return roomCreated() ? super.conIdle(conId) : null;
  }
}
