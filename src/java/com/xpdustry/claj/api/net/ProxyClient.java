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

package com.xpdustry.claj.api.net;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ClosedSelectorException;

import arc.func.Cons;
import arc.net.ArcNetException;
import arc.net.Client;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.net.NetSerializer;
import arc.struct.IntMap;
import arc.struct.Queue;

import com.xpdustry.claj.common.ClajPackets.Disconnect;
import com.xpdustry.claj.common.net.ClientReceiver;
import com.xpdustry.claj.common.util.Structs;


/**
 * A client that act like a server. Discovery is not supported for now (i don't have the use). <br>
 * The proxy doesn't do all the job: <br>
 * - Packet reception must be done manually. <br>
 * - Notifying methods must be called ({@link #conConnected}, {@link #conDisconnected}, {@link #conReceived} and
 * {@link #conIdle}). <br>
 * - Packet making methods must be defined ({@link #makeConWrapPacket} and {@link #makeConClosePacket}).
 */
public abstract class ProxyClient extends Client {
  public static int defaultTimeout = 5000; //ms

  // Redefine some internal states btw
  protected int connectTimeout;
  protected InetAddress connectHost;
  protected int connectTcpPort;
  protected int connectUdpPort;

  /** For faster get. */
  protected final IntMap<VirtualConnection> connectionsMap = new IntMap<>();
  /** For faster iteration. */
  protected VirtualConnection[] connections = {};
  protected NetListener conListener;
  protected volatile boolean shutdown = true, starting, connecting;
  protected ClientReceiver receiver;
  protected long lastPing;
  protected Cons<Throwable> errorHandler;

  /** Packet queue to avoid a buffer overflow. */
  protected final Queue<Object> packetQueue = new Queue<>();
  protected int writeBufferThreshold;
  protected volatile boolean hasQueued = false; // used for fast check

  public ProxyClient(int writeBufferSize, int objectBufferSize, NetSerializer serialization,
                     Cons<Runnable> taskPoster) {
    super(writeBufferSize, objectBufferSize, serialization);
    receiver = new ClientReceiver(this, taskPoster);
    writeBufferThreshold = (int)(writeBufferSize * 0.8f);

    receiver.handle(Disconnect.class, _ -> {
      Throwable error = getLastProtocolError();
      if (error != null) errorHandler.get(error);
    });
  }

  /**
   * Connect used {@link #defaultTimeout} and same {@code port} for TCP and UDP. <br>
   * This also ensures that the client is running before connection.
   */
  public void connect(String host, int port) throws IOException {
    if (!isRunning()) start();
    connect(defaultTimeout, host, port, port);
  }

  @Override
  public void connect(int timeout, InetAddress host, int tcpPort, int udpPort) throws IOException {
    connecting = true;
    connectTimeout = timeout;
    connectHost = host;
    connectTcpPort = tcpPort;
    connectUdpPort = udpPort;
    try { super.connect(timeout, host, tcpPort, udpPort); }
    finally { connecting = false; }
  }

  @Override
  public void update(int timeout) throws IOException {
    updatePing();
    super.update(timeout);
    updateIdle();
    flushPacketQueue();
  }

  @Override
  public void run() {
    shutdown = starting = false;
    try { super.run(); }
    catch (ClosedSelectorException _) { close(); }
    catch (ArcNetException _) {} // Already handled by disconnect event
    catch (Exception e) {
      if (errorHandler != null) errorHandler.get(e);
      else throw e;
      close();
    }
    finally { shutdown = true; }
  }

  @Override
  public void start() {
    if (starting) return;
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

  public boolean isRunning() {
    return !shutdown;
  }

  public boolean isConnecting() {
    return connecting;
  }

  @Override
  public void close() {
    closeAllConnections(DcReason.closed);
    super.close();
  }

  @Override
  public void close(DcReason reason) {
    // We cannot communicate with the server anymore, so close all virtual connections
    closeAllConnections(reason);
    super.close(reason);
  }

  public void closeAllConnections(DcReason reason) {
    for (VirtualConnection c : connections) c.closeQuietly(reason);
    clearConnections();
  }

  protected void addConnection(VirtualConnection con) {
    connectionsMap.put(con.getID(), con);
    // Connections are added at the start instead of end
    //connections = Structs.add(connections, con);
    connections = Structs.insert(connections, 0, con);
  }

  protected void removeConnection(VirtualConnection con) {
    connectionsMap.remove(con.getID());
    connections = Structs.remove(connections, con);
  }

  protected void clearConnections() {
    connectionsMap.clear();
    connections = new VirtualConnection[0];
  }

  public VirtualConnection getConnection(int id) {
    return connectionsMap.get(id);
  }

  public VirtualConnection[] getConnections() {
    return connections;
  }

  public int send(VirtualConnection con, Object object, boolean tcp) {
    if(object == null) throw new IllegalArgumentException("object cannot be null.");
    Object p = makeConWrapPacket(con.getID(), object, tcp);
    return tcp ? sendSafeTCP(p) : sendUDP(p);
  }

  /**
   * Because all {@link VirtualConnection}s shares the same tcp buffer, it can be filled quickly. <br>
   * This tries to queue packets when needed, to avoid an overflow.
   */
  public int sendSafeTCP(Object object) {
    // Fast path
    if (!hasQueued && getTcpWriteBufferSize() <= writeBufferThreshold)
      return sendTCP(object);

    synchronized (packetQueue) {
      if (hasQueued || getTcpWriteBufferSize() > writeBufferThreshold) {
        packetQueue.add(object);
        hasQueued = true;
        return 0;
      }
    }
    return sendTCP(object);
  }

  public void flushPacketQueue() {
    if (!hasQueued) return; // fast check
    synchronized (packetQueue) {
      while (!packetQueue.isEmpty() && getTcpWriteBufferSize() <= writeBufferThreshold)
        sendTCP(packetQueue.removeFirst());
      hasQueued = !packetQueue.isEmpty();
    }
  }

  /** Tries to mimic connection idling. */
  public void updateIdle() {
    for (VirtualConnection c : connections) {
      c.updateIdle();
      if (c.isIdle()) c.notifyIdle0();
    }
  }

  public void updatePing() {
    if (!isConnected()) return;
    long now = System.currentTimeMillis();
    if (now - lastPing > 1) {
      lastPing = now;
      updateReturnTripTime();
    }
  }

  /**
   * Can be used notify the server to close the connection when not created by the proxy. <br>
   * This will not trigger callbacks.
   */
  protected void close(int conId, DcReason reason) {
    sendTCP(makeConClosePacket(conId, reason));
  }

  public void close(VirtualConnection con, DcReason reason) {
    closeQuietly(con, reason);
    close(con.getID(), reason);
  }

  /** Close connection without notify the server. */
  public void closeQuietly(VirtualConnection con, DcReason reason) {
    boolean wasConnected = con.isConnected();
    con.setConnected0(false);
    if(wasConnected) con.notifyDisconnected0(reason);
    removeConnection(con);
  }

  /** @return never {@code null}. */
  protected VirtualConnection conConnected(int conId, long addressHash) {
    VirtualConnection con = getConnection(conId);
    if (con == null) {
      con = new VirtualConnection(this, conId, addressHash);
      if (conListener != null) con.addListener(conListener);
      addConnection(con);
    }
    con.notifyConnected0();
    return con;
  }

  protected VirtualConnection conDisconnected(int conId, DcReason reason) {
    VirtualConnection con = getConnection(conId);
    if (con != null) closeQuietly(con, reason);
    return con;
  }

  protected VirtualConnection conReceived(int conId, Object object) {
    VirtualConnection con = getConnection(conId);
    if (con != null) con.notifyReceived0(object);
    return con;
  }

  protected VirtualConnection conIdle(int conId) {
    VirtualConnection con = getConnection(conId);
    if (con != null) con.notifyIdle0();
    return con;
  }

  protected abstract Object makeConWrapPacket(int conId, Object object, boolean tcp);
  protected abstract Object makeConClosePacket(int conId, DcReason reason);
}
