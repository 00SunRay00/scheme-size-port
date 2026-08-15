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

package com.xpdustry.claj.client;

import java.io.IOException;
import arc.func.Cons;
import arc.net.*;
import arc.net.Server.ServerConnectFilter;
import arc.struct.Seq;
import arc.util.Threads;

import mindustry.net.Host;
import mindustry.net.Net.NetProvider;
import mindustry.net.NetConnection;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajProxy;


/** Modified {@link NetProvider} that handles CLaJ broadcast system. */
public class ClajNetProvider implements NetProvider {
  private static final ThreadLocal<Seq<NetConnection>>
    clajConnections = Threads.local(Seq::new), mindustryConnections = Threads.local(Seq::new);

  public final NetProvider provider;
  // In case of mods also using these fields via reflection without checking for the right class
  public final Client client;
  public final Server server;

  public ClajNetProvider(NetProvider provider, Client client, Server server) {
    this.provider = provider;
    this.client = client;
    this.server = server;
  }

  public MindustryClajProxy getProxy() {
    ClajProxy proxy = Claj.get().proxies.get(); // Mindustry implementation only uses the first proxy
    return proxy.roomCreated() && proxy instanceof MindustryClajProxy mproxy ? mproxy : null;
  }

  public void broadcast(Iterable<NetConnection> connections, NetConnection except, Object object, boolean reliable) {
    MindustryClajProxy proxy = getProxy();

    if (proxy == null) {
      if (connections == null) {
        if (except == null) provider.sendAllServer(object, reliable);
        else provider.sendExceptServer(except, object, reliable);
      } else {
        if (except == null) provider.sendAllServer(object, connections, reliable);
        // No dedicated method for this path, but it is never used anyways....
        else for (NetConnection con : connections) {
          if (con != except) con.send(object, reliable);
        }
      }
      return;
    }

    // Sort CLaJ connections
    Seq<NetConnection> cc = clajConnections.get().clear();
    Seq<NetConnection> mc = mindustryConnections.get().clear();
    for (NetConnection c : connections == null ? getConnections() : connections) {
      if (except == c) continue;
      (proxy.hasConnection(c) ? cc : mc).add(c);
    }

    if (mc.any()) provider.sendAllServer(object, mc, reliable);
    // Just check the size to avoid a double iteration
    if (cc.size == proxy.getConnectionsSize()) proxy.broadcast(object, reliable);
    else if (cc.any()) provider.sendAllServer(object, cc, reliable);
  }

  public void sendAllServer(Object object, Iterable<NetConnection> connections, boolean reliable) { broadcast(connections, null, object, reliable); }
  public void sendAllServer(Object object, boolean reliable) { broadcast(null, null, object, reliable); }
  public void sendExceptServer(NetConnection except, Object object, boolean reliable) { broadcast(null, except, object, reliable); }
  public void connectClient(String ip, int port, Runnable success) throws IOException { provider.connectClient(ip, port, success); }
  public void sendClient(Object object, boolean reliable) { provider.sendClient(object, reliable); }
  public void disconnectClient() { provider.disconnectClient(); }
  public void discoverServers(Cons<Host> callback, Runnable done) { provider.discoverServers(callback, done); }
  public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed) { provider.pingHost(address, port, valid, failed); }
  public void hostServer(int port) throws IOException { provider.hostServer(port); }
  public Iterable<? extends NetConnection> getConnections() { return provider.getConnections(); }
  public void closeServer() { provider.closeServer(); }
  public void dispose() { provider.dispose(); }
  public void setConnectFilter(ServerConnectFilter connectFilter) { provider.setConnectFilter(connectFilter); }
  public ServerConnectFilter getConnectFilter() { return provider.getConnectFilter(); }
}
