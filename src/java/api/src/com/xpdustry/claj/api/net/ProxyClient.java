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
import arc.net.*;
import arc.struct.*;
import arc.util.Structs;

import com.xpdustry.claj.common.ClajPackets.Disconnect;
import com.xpdustry.claj.common.net.ClientReceiver;
import com.xpdustry.claj.common.net.NetListenerFilter;


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
  /** Delay between pings. */
  public static int pingTime = 3000;

  // Redefine some internal states btw
  protected int connectTimeout;
  protected InetAddress connectHost;
  protected int connectTcpPort;
  protected int connectUdpPort;

  protected final IntMap<VirtualConnection> connectionsMap = new IntMap<>(16);
  protected final Seq<VirtualConnection> connections = new Seq<>(false);
  /** Used by {@link #close(VirtualConnection, DcReason)} to thread-safely remove a connection. */
  private volatile VirtualConnection[] stales = null;

  protected NetListener conListener;
  protected volatile boolean shutdown = true, starting, connecting, closing;
  protected ClientReceiver receiver;
  protected long lastPing;
  protected Cons<Throwable> errorHandler;

  /** Packet queue to avoid a buffer overflow. */
  protected int writeBufferThreshold;
  private final Object waitLock = new Object();
  private volatile boolean waitingForWrite;

  /**
   * Whether to force using the tcp connection when sending trough udp to the server. <br>
   * The server will then send it via udp to the real client. <br>
   * This can fix some udp packet loss issues,
   * at the cost of increased tcp band for things initially designed to be less important.
   */
  public boolean forceTcp;
  /**
   * As packet broadcasting is not a protocol breaking change, some servers may not support it. <br>
   * Server version should be checked and this field set to {@code false} if not supported. <br>
   * This field is reset before connecting and after closing the proxy.
   */
  public boolean broadcastSupported = true;

  public ProxyClient(int writeBufferSize, int objectBufferSize, NetSerializer serialization) {
    super(writeBufferSize, objectBufferSize, serialization);
    receiver = new ClientReceiver(this, NetListenerFilter.noIdleFilter);
    writeBufferThreshold = (int)(writeBufferSize * 0.95f);

    receiver.handle(Disconnect.class, _ -> {
      Throwable error = getLastProtocolError();
      if (error != null && errorHandler != null) errorHandler.get(error);
      //also close virtual connections?
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
    //TODO: add an option to prefer connecting only via tcp,
    //      as udp is not reliable for this kind of thing.
    connecting = broadcastSupported = true;
    closing = false;
    connectTimeout = timeout;
    connectHost = host;
    connectTcpPort = tcpPort;
    connectUdpPort = udpPort;
    try { super.connect(timeout, host, tcpPort, udpPort); }
    finally { connecting = false; }
  }

  @Override
  public void update(int timeout) throws IOException {
    clearStales();
    updatePing();
    super.update(timeout);
    updateIdle();

    // Only signal at idle, to avoid ping-pong
    if (isIdle() && waitingForWrite) {
      synchronized (waitLock) {
        waitLock.notifyAll();
      }
    }
  }

  /** Tries to mimic connection idling. */
  public void updateIdle() {
    for (VirtualConnection c : getConnections()) {
      c.updateIdle();
      if (c.isIdle()) c.notifyIdle0();
    }
  }

  public void updatePing() {
    if (!isConnected()) return;
    long now = System.currentTimeMillis();
    if (now - lastPing <= pingTime) return;
    lastPing = now;
    updateReturnTripTime();
  }

  @Override
  public void run() {
    shutdown = starting = false;
    try { super.run(); }
    catch (ClosedSelectorException _) { close(); }
    catch (ArcNetException _) {} // Already handled by disconnect event
    catch (Exception e) {
      if (errorHandler != null) errorHandler.get(e);
      close();
      if (errorHandler == null) throw e;
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
  public void close(DcReason reason) {
    // To avoid a recursive call if it's sending last packet before cutting tcp connection.
    // As in the case of a broken pipe, close() will be called again before removing connected state.
    if (!closing) {
      closing = true;
      closeAllConnections(reason);
    }
    super.close(reason);
    broadcastSupported = true;
    closing = false;
  }

  public void closeAllConnections(DcReason reason) {
    for (VirtualConnection c : getConnections()) {
      boolean wasConnected = c.isConnected();
      c.setConnected0(false);
      if(wasConnected) c.notifyDisconnected0(reason);
      c.resetIdle();
      if (!broadcastSupported) close(c.getID(), reason);
    }
    if (connections.any() && broadcastSupported) send(makeBroadcastClosePacket(reason));
    clearConnections();
    stales = null;
  }

  private void addStale(VirtualConnection con) {
    if (con == null) return;
    VirtualConnection[] stales = this.stales;
    this.stales = stales == null ? new VirtualConnection[] {con} : Structs.add(stales, con);
  }

  private void clearStales() {
    VirtualConnection[] stales = this.stales;
    this.stales = null;
    if (stales == null) return;
    Structs.each(this::removeConnection, stales);
  }

  private boolean isStale(VirtualConnection con) {
    if (con == null) return false;
    VirtualConnection[] stales = this.stales;
    return stales != null && Structs.contains(stales, con::equals);
  }

  protected void addConnection(VirtualConnection con) {
    connectionsMap.put(con.getID(), con);
    connections.add(con);
  }

  protected void removeConnection(VirtualConnection con) {
    connectionsMap.remove(con.getID());
    connections.remove(con);
  }

  protected void clearConnections() {
    connectionsMap.clear();
    connections.clear();
  }

  public VirtualConnection getConnection(int id) {
    return connectionsMap.get(id);
  }

  /** @return whether the connection is from this proxy. */
  public boolean hasConnection(Connection con) {
    return con != null && connectionsMap.get(con.getID()) == con;
  }

  public Iterable<VirtualConnection> getConnections() {
    return connections;
  }

  public int getConnectionsSize() {
    return connections.size;
  }

  public void eachConnections(Cons<VirtualConnection> consumer) {
    connections.each(consumer);
  }


  /**
   * Send an object to every clients connected to the room. <br>
   * This is an optimization method to avoid the host from sending the same packet to every virtual clients,
   * as the server will do it to the real ones instead. So, using this will save bandwidth.
   * <p>
   * If {@link #broadcastSupported} is {@code false},
   * this is equivalent of calling {@link #send()} for each connections.
   */
  public int broadcast(Object object, boolean tcp) {
    if (object == null) throw new IllegalArgumentException("object cannot be null.");
    if (connections.isEmpty()) return 0; // no need to broadcast is there is no clients
    if (!broadcastSupported) return connections.sum(c -> send(c, object, tcp));
    return broadcastImpl(object, tcp);
  }

  /** Doesn't checks for availability. */
  protected int broadcastImpl(Object object, boolean tcp) {
    int written = send(makeBroadcastWrapPacket(object, tcp), tcp);
    eachConnections(VirtualConnection::resetIdle);
    return written;
  }

  public int send(Object object) { return send(object, true); }
  public int send(Object object, boolean tcp) {
    if (!isConnected()) return 0;
    try {
      return tcp || forceTcp ? sendTCP(object) : sendUDP(object);
    } catch (Throwable th) {
      RuntimeException e = new RuntimeException("FATAL: Failed to send object of type @" +
                                                object.getClass().getName(), th);
      if (errorHandler == null) throw e;
      else errorHandler.get(e);
      close(DcReason.error);
      return -1;
    }
  }

  public int send(VirtualConnection con, Object object, boolean tcp) {
    if (object == null) throw new IllegalArgumentException("object cannot be null.");
    int written = send(makeConWrapPacket(con.getID(), object, tcp), tcp);
    con.resetIdle();
    return written;
  }

  /**
   * Can be used notify the server to close the connection when not created by the proxy. <br>
   * This will not trigger callbacks.
   */
  protected void close(int conId, DcReason reason) {
    send(makeConClosePacket(conId, reason));
  }

  public void close(VirtualConnection con, DcReason reason) {
    closeQuietly(con, reason);
    close(con.getID(), reason);
  }

  /** Close connection without notify the server. */
  public void closeQuietly(VirtualConnection con, DcReason reason) {
    addStale(con);
    boolean wasConnected = con.isConnected();
    con.setConnected0(false);
    if(wasConnected) con.notifyDisconnected0(reason);
    con.resetIdle();
  }

  /** @return never {@code null}. */
  protected VirtualConnection conConnected(int conId, long addressHash) {
    VirtualConnection con = getConnection(conId);
    if (con == null) {
      clearStales(); // Clear stale connections now to avoid a duplicate
      con = new VirtualConnection(this, getSerialization(), conId, addressHash);
      if (conListener != null) con.addListener(conListener);
      addConnection(con);
    }
    con.notifyConnected0();
    return con;
  }

  protected VirtualConnection conDisconnected(int conId, DcReason reason) {
    VirtualConnection con = getConnection(conId);
    if (isStale(con)) return null;
    if (con != null) closeQuietly(con, reason);
    return con;
  }

  protected VirtualConnection conReceived(int conId, Object object) {
    VirtualConnection con = getConnection(conId);
    if (isStale(con)) return null;
    if (con != null) con.notifyReceived0(object);
    return con;
  }

  protected VirtualConnection conIdle(int conId) {
    VirtualConnection con = getConnection(conId);
    if (isStale(con)) return null;
    if (con != null) con.notifyIdle0();
    return con;
  }

  protected abstract Object makeBroadcastWrapPacket(Object object, boolean tcp);
  protected abstract Object makeBroadcastClosePacket(DcReason reason);
  protected abstract Object makeConWrapPacket(int conId, Object object, boolean tcp);
  protected abstract Object makeConClosePacket(int conId, DcReason reason);


  //TODO: to test
  /**
   * Because all {@link VirtualConnection}s shares the same tcp buffer, it can be filled quickly. <br>
   * This will slow down sends when the write buffer reaches a threshold.
   */
  @Override
  public int sendTCP(Object object) {
    // Heuristic: just check for enough space assuming packets are not so big.
    // For future, override directly TcpConnection#send, to make waiting at buffer
    // overflow and retry each socket writes.
    if (getTcpWriteBufferSize() <= writeBufferThreshold) return super.sendTCP(object);
    synchronized (waitLock) {
      waitingForWrite = true;
      int waits = 20;
      while (getTcpWriteBufferSize() > writeBufferThreshold && waits-- > 0 ) {
        try { waitLock.wait(100); } // Only wait for 100ms and 20 times to avoid dead-locks
        catch (InterruptedException _) {}
      }
      waitingForWrite = false;
      return super.sendTCP(object);
    }
  }
}
