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

import java.net.InetSocketAddress;

import arc.net.ArcNetException;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.EndPoint;
import arc.net.NetListener;

import com.xpdustry.claj.common.net.DispatchListener;
import com.xpdustry.claj.common.util.AddressUtil;


/**
 * A connection that doesn't have a socket and buffer behind. <br>
 * Every writes is done to the proxy, and listeners are triggered by him. <br>
 * And some internal {@link Connection} states are exposed for more control.
 * <p>
 * This must not be used like a standard {@link Connection},
 * as some internal states like listeners cannot be fully overridden.
 */
public class VirtualConnection extends Connection {
  /** The real client, aka the proxy. */
  public final ProxyClient proxy;

  protected final int id;
  protected final DispatchListener dispatcher = new DispatchListener(true);
  /**
   * Fake address calculated using the hash of the real client address provided by the server. <br>
   * TCP and UDP ports are always the same, so no need to have two InetSocketAddress.
   */
  protected final InetSocketAddress remoteAddress;
  protected String name;

  /**
   * A virtual connection is always connected until we closing it. <br>
   * The proxy will notify the server to close this connection too,
   * or will quietly close it if requested by the server.
   */
  private volatile boolean isConnected = true;
  /** Not thread-safe for a little bit of random. */
  private boolean isIdling = true, sentThisCycle = false;

  public VirtualConnection(ProxyClient proxy, int id, long addressHash) {
    this.proxy = proxy;
    this.id = id;
    this.remoteAddress = new InetSocketAddress(AddressUtil.generate(addressHash), proxy.connectTcpPort);
  }

  @Override
  public int sendTCP(Object object) {
    resetIdle();
    return proxy.send(this, object, true);
  }
  @Override
  public int sendUDP(Object object) {
    resetIdle();
    return proxy.send(this, object, false);
  }
  @Override
  public void close(DcReason reason) {
    resetIdle();
    proxy.close(this, reason);
  }
  /**
   * Close the connection without notify the server about that. <br>
   * Common use is when the server itself is saying to close the connection.
   */
  public void closeQuietly(DcReason reason) {
    resetIdle();
    proxy.closeQuietly(this, reason);
  }

  public NetListener[] getListeners() { return dispatcher.getListeners(); }
  /** Only used when sending world data */
  @Override
  public void addListener(NetListener listener) { dispatcher.addListener(listener); }
  /** Only used when sending world data */
  @Override
  public void removeListener(NetListener listener) { dispatcher.removeListener(listener); }

  public void notifyConnected0() { dispatcher.connected(this); }
  public void notifyDisconnected0(DcReason reason) { dispatcher.disconnected(this, reason); }
  public void notifyIdle0() { dispatcher.idle(this); }
  public void notifyReceived0(Object object) { dispatcher.received(this, object); }

  public void updateIdle() {
    isIdling = !sentThisCycle;
    sentThisCycle = false;
  }
  public void resetIdle() {
    sentThisCycle = true;
    isIdling = false;
  }
  public void setConnected0(boolean connected) { isConnected = connected; }

  @Override
  public int getID() { return id; }
  @Override
  public boolean isConnected() { return isConnected; }
  @Override
  public ArcNetException getLastProtocolError() { return proxy.getLastProtocolError(); }
  @Override
  public void updateReturnTripTime() { proxy.updateReturnTripTime(); }
  @Override
  public int getReturnTripTime() { return proxy.getReturnTripTime(); }
  @Override
  public void setKeepAliveTCP(int keepAliveMillis) {} // never used
  @Override
  public void setTimeout(int timeoutMillis) {} // never used
  @Override
  public EndPoint getEndPoint() { return proxy.getEndPoint(); } // never used
  @Override
  public InetSocketAddress getRemoteAddressTCP() { return isConnected() ? remoteAddress : null; }
  @Override
  public InetSocketAddress getRemoteAddressUDP() { return isConnected() ? remoteAddress : null;  }
  @Override
  public void setName(String name) { this.name = name; } // never used
  @Override
  public int getTcpWriteBufferSize() { return proxy.getTcpWriteBufferSize(); } // never used
  /** The server will notify if the client is idling. */
  @Override
  public boolean isIdle() { return isIdling; }
  @Override
  public void setIdleThreshold(float idleThreshold) {} // never used
  @Override
  public String toString() { return name != null ? name : "Connection " + id; }
}
