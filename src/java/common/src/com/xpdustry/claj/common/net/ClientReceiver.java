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

package com.xpdustry.claj.common.net;

import arc.func.Cons;
import arc.net.*;
import arc.struct.ObjectMap;
import arc.util.Log;

import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.net.stream.StreamPacket;
import com.xpdustry.claj.common.net.stream.StreamReceiver;
import com.xpdustry.claj.common.packets.Packet;


/** A client listener that can delegate packet decoding and reception to the main app. */
public class ClientReceiver implements NetListener {
  protected final ObjectMap<Class<?>, Cons<?>> listeners = new ObjectMap<>(32);
  protected Cons<Throwable> errorHandler;
  protected NetListenerFilter filter;

  public ClientReceiver(EndPoint client) { this(client, NetListenerFilter.defaultFilter); }
  public ClientReceiver(EndPoint client, NetListenerFilter filter) { this(client, filter, Log::err); }
  public ClientReceiver(EndPoint client, NetListenerFilter filter, Cons<Throwable> errorHandler) {
    setFilter(filter);
    setErrorHandler(errorHandler);
    client.addListener(this);
  }

  public void setFilter(NetListenerFilter filter) {
    if (filter == null) throw new NullPointerException("filter");
    this.filter = filter;
  }

  public NetListenerFilter getFilter() {
    return filter;
  }

  public void setErrorHandler(Cons<Throwable> errorHandler) {
    if (errorHandler == null) throw new NullPointerException("errorHandler");
    this.errorHandler = errorHandler;
  }

  @Override
  public void connected(Connection connection) {
    if (!filter.allowConnected(connection)) return;
    receive(Connect.instance);
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    if (!filter.allowDisconnected(connection, reason)) return;
    receive(Disconnect.get(reason));
  }

  @Override
  public void received(Connection connection, Object object) {
    if (!filter.allowReceived(connection, object)) return;
    if (!(object instanceof Packet packet)) return;
    receive(packet);
  }

  @Override
  public void idle(Connection connection) {
    if (!filter.allowIdle(connection)) return;
    receive(Idle.instance);
  }

  public <T extends Packet> void handle(Class<T> type, Runnable listener) {
    handle(type, _ -> listener.run());
  }

  public <T extends Packet> void handle(Class<T> type, Cons<T> listener) {
    Cons<T> old = getListener(type);
    if (old != null) {
      Cons<T> current = listener;
      listener = p -> {
        old.get(p);
        current.get(p);
      };
    }
    listeners.put(type, listener);
  }

  public <T extends Packet> void handleReplace(Class<T> type, Cons<T> listener) {
    listeners.put(type, listener);
  }

  @SuppressWarnings("unchecked")
  public <T extends Packet> Cons<T> getListener(Class<T> type) {
    return (Cons<T>)listeners.get(type);
  }

  @SuppressWarnings("unchecked")
  public void receive(Packet packet) {
    if (!packet.allow(false)) return; // Throw away unwanted packets

    try {
      if (packet instanceof StreamPacket stream) {
        packet = StreamReceiver.received(stream);
        if (packet != null) receive(packet);
        return;
      }

      var listener = (Cons<Packet>)listeners.get(packet.getClass());
      if (listener != null) listener.get(packet);
      else packet.handleClient();
    } catch (Throwable e) { errorHandler.get(e); }
  }
}
