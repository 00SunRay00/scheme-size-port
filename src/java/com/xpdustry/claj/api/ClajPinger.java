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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Selector;

import arc.func.Cons;
import arc.net.ArcNetException;
import arc.net.Client;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.struct.LongMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.io.ByteBufferInput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.ClajPackets.Disconnect;
import com.xpdustry.claj.common.net.ClientReceiver;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;


// FIXME: see ClajPingerManager comment
/**
 * Note that {@link #pingHost}, {@link #requestRoomList} and {@link #joinRoom} are async operations.<br>
 * If one of them are called while another is running, the last one is canceled.
 */
public class ClajPinger extends Client {
  public static final short NO_PASSWORD = -1;
  /** In ms. */
  public static int connectTimeout = 5 * 1000,
                    pingTimeout = connectTimeout,
                    joinTimeout = connectTimeout,
                    infoTimeout = 10 * 1000,
                    listTimeout = 30 * 1000;

  protected final ClajProvider provider;
  protected final ClientReceiver receiver;
  protected String connectHost;
  protected int connectPort;
  protected volatile boolean shutdown = true, starting, connecting;
  /** If {@code true}, current and future operations will be canceled. */
  public volatile boolean canceling;
  protected volatile long time, timeout;

  protected Cons<ServerState> pingSuccess;
  protected Cons<Exception> pingFailed;
  protected volatile boolean pinging;

  protected Cons<Seq<ClajRoom<?>>> listInfo;
  protected Cons<Exception> listFailed;
  protected volatile boolean listing;

  protected Cons<ByteBuffer> joinSuccess;
  protected Cons<RejectReason> joinDenied;
  protected Cons<Exception> joinFailed;
  protected RoomJoinRequestPacket lastRequest;
  protected volatile long requestedRoom = ClajProxy.UNCREATED_ROOM;
  protected volatile boolean joining;

  protected Cons<ClajRoom<?>> infoSuccess;
  protected Runnable infoNotFound;
  protected Cons<Exception> infoFailed;
  protected volatile boolean infoing;

  public ClajPinger(ClajProvider provider) {
    super(8192, 8192, new Serializer());
    ((Serializer)getSerialization()).set(this);
    this.provider = provider;
    this.receiver = new ClientReceiver(this, null); // no need to delegate to the main thread

    receiver.handle(Disconnect.class, ignored -> {
      Throwable error = getLastProtocolError();
      if (error != null) provider.handlePingerError(this, error);
      failed(error);
    });

    receiver.handle(RoomJoinAcceptedPacket.class, p -> {
      if (p.roomId != ClajProxy.UNCREATED_ROOM && p.roomId == requestedRoom)
        runJoinSuccess();
    });
    receiver.handle(RoomJoinDeniedPacket.class, p -> {
      if (p.roomId != ClajProxy.UNCREATED_ROOM && p.roomId == requestedRoom)
        runJoinDenied(p.reason);
    });

    receiver.handle(RoomListPacket.class, p -> runListInfo(p.states, p.protectedRooms));
    receiver.handle(RoomInfoPacket.class, p -> {
      if (p.roomId != requestedRoom) return;
      runInfoSuccess(p.roomId, p.isProtected, p.type, p.clients, p.maxClients, p.state);
    });
    receiver.handle(RoomInfoDeniedPacket.class, this::runInfoNotFound);

    receiver.handle(ServerInfoPacket.class, p -> runPingSuccess(p.version));
  }

  @Override
  public void update(int t) throws IOException {
    super.update(canceling ? 0 : t);
    if (isRequestTimedOut()) timeout();
    if (canceling) close();
  }

  @Override
  public void run() {
    shutdown = starting = false;
    try { super.run(); }
    catch (ClosedSelectorException e) {}
    catch (ArcNetException e) {} // Already handled by disconnect event
    catch (Exception e) {
      provider.handlePingerError(this, e);
      failed(e);
      close();
    }
    finally { shutdown = true; }
  }

  @Override
  public void start() {
    if (starting) return;
    if (getUpdateThread() != null) shutdown = true;
    starting = true;
    super.start();
  }

  @Override
  public void stop() {
    if(shutdown) return;
    super.stop();
    starting = false;
    shutdown = true;
  }

  @SuppressWarnings("resource")
  @Override
  public void close() {
    if (canceling) {
      close(DcReason.closed);
      // Makes #close() doesn't wait for 'updateLock', which will makes #connect() cancelable
      Reflect.<Selector>get(Client.class, this, "selector").wakeup();
    } else super.close();
  }

  @Override
  public void close(DcReason reason) {
    if (reason == DcReason.closed) cancel();
    else timeout();
    super.close(reason);
  }

  protected void timeout() {
    stopTask("timed out", null);
    if (connecting) super.close(DcReason.timeout);
  }

  /** Cancel running operation. */
  public void cancel() {
    stopTask("canceled", null);
    if (connecting) super.close(DcReason.closed);
  }

  public void failed(Throwable error) {
    stopTask("failed", error);
    if (connecting) super.close(DcReason.closed);
  }

  public synchronized void stopTask(String reason, Throwable error) {
    if (pinging) runPingFailed(new RuntimeException("Ping " + reason, error));
    if (listing) runListFailed(new RuntimeException("Room listing " + reason, error));
    if (joining) runJoinFailed(new RuntimeException("Room join " + reason, error));
    if (infoing) runInfoFailed(new RuntimeException("Room info " + reason, error));
  }

  public boolean isRunning() {
    return !shutdown;
  }

  public boolean isConnecting() {
    return connecting;
  }

  public synchronized boolean isWorking() {
    return pinging || listing || joining || infoing;
  }

  public void setCancelState(boolean cancel) {
    canceling = cancel;
  }

  // Helpers
  protected <T> void postTask(Cons<T> consumer, T object) { postTask(() -> consumer.get(object)); }
  protected void postTask(Runnable run) { provider.postTask(run); }

  public boolean isRequestTimedOut() {
    return timeout > 0 && System.currentTimeMillis() >= timeout;
  }

  public void setRequestTimeout(int out) {
    if (out <= 0) {
      time = timeout = 0;
      setTimeout(12000);//default
      return;
    }
    time = System.currentTimeMillis();
    timeout = time + out;
    setTimeout(out);
  }

  protected <T> ClajRoom<T> makeRoom(long roomId, boolean isProtected, ClajType type, int clients, int maxClients,
                                     ByteBuffer state) {
    T decodedState = null;
    ClajLink link = new ClajLink(connectHost, connectPort, roomId);
    if (state != null && state.hasRemaining()) {
      try { decodedState = provider.readRoomState(roomId, type, state); }
      catch (Exception e) {
        Exception error = new RuntimeException("Failed to decode state of room " + link.encodedRoomId, e);
        provider.handlePingerError(this, error);
      }
    }
    return new ClajRoom<>(roomId, true, isProtected, decodedState, link, type, clients, maxClients);
  }

  protected synchronized void resetPingState(Cons<ServerState> success, Cons<Exception> failed) {
    pingSuccess = success;
    pingFailed = failed;
    setRequestTimeout(0);
    pinging = false;
  }

  protected void runPingSuccess(int version) {
    if (pingSuccess != null) {
      int ping = (int)(System.currentTimeMillis() - time);
      postTask(pingSuccess, new ServerState(connectHost, connectPort, version, ping));
    }
    resetPingState(null, null);
    close();
  }

  protected void runPingFailed(Exception e) {
    if (pingFailed != null) postTask(pingFailed, e);
    resetPingState(null, null);
    close();
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // meh...
  protected synchronized <T> void resetListState(Cons<Seq<ClajRoom<T>>> rooms, Cons<Exception> failed) {
    listInfo = (Cons)rooms;
    listFailed = failed;
    setRequestTimeout(0);
    listing = false;
  }

  protected void runListInfo(LongMap<ByteBuffer> states, ObjectSet<Long> protectedRooms) {
    // Avoid creating useless objects if the callback is not defined.
    if (listInfo != null) {
      Seq<ClajRoom<?>> roomList = new Seq<>(states.size);
      ClajType type = provider.getType();
      for (LongMap.Entry<ByteBuffer> e : states) {
        if (e.key == ClajProxy.UNCREATED_ROOM) continue; // ignore invalid rooms
        roomList.add(makeRoom(e.key, protectedRooms.contains(e.key), type, 0, 0, e.value));
      }
      postTask(listInfo, roomList);
    }
    resetListState(null, null);
    close();
  }

  protected void runListFailed(Exception e) {
    if (listFailed != null) postTask(listFailed, e);
    resetListState(null, null);
    close();
  }

  protected synchronized void resetJoinState(Cons<ByteBuffer> success, Cons<RejectReason> reject,
                                             Cons<Exception> failed) {
    joinSuccess = success;
    joinDenied = reject;
    joinFailed = failed;
    requestedRoom = ClajProxy.UNCREATED_ROOM;
    lastRequest = null;
    setRequestTimeout(0);
    joining = false;
  }

  protected void runJoinSuccess() {
    if (joinSuccess != null) postTask(joinSuccess, makeJoinPacket(lastRequest));
    resetJoinState(null, null, null);
    close();
  }

  protected void runJoinDenied(RejectReason reason) {
    if (joinDenied != null) postTask(joinDenied, reason);
    resetJoinState(null, null, null);
    close();
  }

  protected void runJoinFailed(Exception e) {
    if (joinFailed != null) postTask(joinFailed, e);
    resetJoinState(null, null, null);
    close();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected synchronized <T> void resetInfoState(Cons<ClajRoom<T>> success, Runnable notFound, Cons<Exception> failed) {
    infoSuccess = (Cons)success;
    infoNotFound = notFound;
    infoFailed = failed;
    requestedRoom = ClajProxy.UNCREATED_ROOM;
    setRequestTimeout(0);
    infoing = false;
  }

  protected void runInfoSuccess(long roomId, boolean isProtected, ClajType type, int clients, int maxClients,
                                ByteBuffer state) {
    if (infoSuccess != null)
      postTask(infoSuccess, makeRoom(roomId, isProtected, type, clients, maxClients, state));
    resetInfoState(null, null, null);
    close();
  }

  protected void runInfoFailed(Exception e) {
    if (infoFailed != null) postTask(infoFailed, e);
    resetInfoState(null, null, null);
    close();
  }

  protected void runInfoNotFound() {
    if (infoNotFound != null) postTask(infoNotFound);
    resetInfoState(null, null, null);
    close();
  }

  /**
   * Connect using {@link #connectTimeout} and same {@code port} for TCP and UDP. <br>
   * This also ensures that the client is running before connection, and can be canceled.
   */
  public void connect(String host, int port) throws IOException {
    if (!isRunning()) start();
    connecting = true;
    connectHost = host;
    connectPort = port;
    try { connect(connectTimeout, host, port, port); }
    finally { connecting = false; }
  }

  public void pingHost(String host, int port, Cons<ServerState> success, Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) {
        resetPingState(success, failed);
        runPingFailed(e);
        return;
      }
    } else close();
    //arc.util.Log.info("pinger: @, @, @", canceling, Thread.currentThread().getName(), System.currentTimeMillis());
    resetPingState(success, failed);
    setRequestTimeout(pingTimeout);
    pinging = true;
    if (canceling) cancel();
    else requestServerStatus();
  }

  public <T> void requestRoomList(String host, int port, Cons<Seq<ClajRoom<T>>> rooms, Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) {
        resetListState(rooms, failed);
        runListFailed(e);
        return;
      }
    } else close();
    resetListState(rooms, failed);
    setRequestTimeout(listTimeout);
    listing = true;
    if (canceling) cancel();
    else requestRoomList();
  }

  public void joinRoom(String host, int port, long roomId, Cons<ByteBuffer> success, Cons<RejectReason> reject,
                       Cons<Exception> failed) {
    joinRoom(host, port, roomId, false, NO_PASSWORD, success, reject, failed);
  }

  public void joinRoom(String host, int port, long roomId, short password, Cons<ByteBuffer> success,
                       Cons<RejectReason> reject, Cons<Exception> failed) {
    joinRoom(host, port, roomId, true, password, success, reject, failed);
  }

  protected void joinRoom(String host, int port, long roomId, boolean withPassword, short password,
                          Cons<ByteBuffer> success, Cons<RejectReason> reject, Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) {
        resetJoinState(success, reject, failed);
        runJoinFailed(e);
        return;
      }
    } else close();
    resetJoinState(success, reject, failed);
    requestedRoom = roomId;
    setRequestTimeout(joinTimeout);
    joining = true;
    if (canceling) cancel();
    else requestRoomJoin(roomId, withPassword, password);
  }

  public <T> void requestRoomInfo(String host, int port, long roomId, Cons<ClajRoom<T>> info, Runnable notFound,
                                  Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) {
        resetInfoState(info, notFound, failed);
        runInfoFailed(e);
        return;
      }
    } else close();
    resetInfoState(info, notFound, failed);
    requestedRoom = roomId;
    setRequestTimeout(infoTimeout);
    infoing = true;
    if (canceling) cancel();
    else requestRoomInfo(roomId);
  }

  protected void requestServerStatus() {
    sendUDP(FrameworkMessage.discoverHost);
  }

  protected void requestRoomInfo(long roomId) {
    RoomInfoRequestPacket p = new RoomInfoRequestPacket();
    p.roomId = roomId;
    sendTCP(p);
  }

  protected void requestRoomList() {
    RoomListRequestPacket p = new RoomListRequestPacket();
    p.type = provider.getType();
    sendTCP(p);
  }

  protected void requestRoomJoin(long roomId, boolean withPassword, short password) {
    RoomJoinRequestPacket p = new RoomJoinRequestPacket();
    p.roomId = roomId;
    p.withPassword = withPassword && password != NO_PASSWORD;
    p.password = password;
    p.type = provider.getType();
    lastRequest = p;
    sendTCP(p);
  }

  protected ByteBuffer makeJoinPacket(RoomJoinRequestPacket request) {
    if (request == null) return null;
    ByteBuffer buff = ByteBuffer.allocate(RoomJoinRequestPacket.SIZE);
    getSerialization().write(buff, request.toJoinPacket()); // not thread-safe  // sure?
    return (ByteBuffer)buff.flip();
  }


  /** Modified serializer that reads only one packet type in {@linkplain ClajPinger#pinging pinging} mode. */
  protected static class Serializer extends ClajClientSerializer {
    protected ClajPinger pinger;
    public void set(ClajPinger pinger) { this.pinger = pinger; }

    @Override
    public Object read(ByteBuffer buffer) {
      if (pinger.pinging) {
        if (!buffer.hasRemaining() || buffer.get() == ClajNet.id) {
          ByteBufferInput readi = read.get();
          readi.buffer = buffer;
          return new ServerInfoPacket().r(readi);
        }
        buffer.position(buffer.position()-1);
      }
      return super.read(buffer);
    }
  }
}
