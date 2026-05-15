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
import com.xpdustry.claj.common.util.AddressUtil;


/** A client listener that can delegate packet decoding and reception to the main app. */
public class ClientReceiver implements NetListener {
  protected final ObjectMap<Class<?>, Cons<?>> listeners = new ObjectMap<>(32);
  protected Cons<Runnable> delegator;
  protected Cons<Throwable> errorHandler;
  protected NetListenerFilter filter;

  /** Receive will not be delegated. */
  public ClientReceiver(EndPoint server) { this(server, null); }
  public ClientReceiver(EndPoint server, Cons<Runnable> delegator) {
    this.delegator = delegator;
    this.filter = NetListenerFilter.defaultFilter;
    this.errorHandler = Log::err;
    server.addListener(this);
  }

  public void setFilter(NetListenerFilter filter) {
    if (filter == null) throw new NullPointerException("filter");
    this.filter = filter;
  }

  public void setErrorHandler(Cons<Throwable> errorHandler) {
    if (errorHandler == null) throw new NullPointerException("errorHandler");
    this.errorHandler = errorHandler;
  }

  @Override
  public void connected(Connection connection) {
    if (!filter.connected(connection)) return;
    Connect packet = new Connect();
    packet.address = AddressUtil.getString(connection);
    delegateReceive(packet);
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    if (!filter.disconnected(connection, reason)) return;
    Disconnect packet = new Disconnect();
    packet.reason = reason;
    delegateReceive(packet);
  }

  @Override
  public void received(Connection connection, Object object) {
    if (!filter.received(connection, object)) return;
    if (!(object instanceof Packet packet)) return;
    delegateReceive(packet);
  }

  @Override
  public void idle(Connection connection) {
    if (!filter.idle(connection)) return;
    delegateReceive(Idle.instance);
  }

  /** Whether packet reception is delegated to the main thread or not. */
  public boolean delegated() {
    return delegator != null;
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

  @SuppressWarnings("unchecked")
  public <T extends Packet> Cons<T> getListener(Class<T> type) {
    return (Cons<T>)listeners.get(type);
  }

  /** Send packet reception to the main thread or not according to {@link #delegated}. */
  public void delegateReceive(Packet packet) {
    if (delegated()) delegator.get(() -> received(packet));
    else received(packet);
  }

  @SuppressWarnings("unchecked")
  public void received(Packet packet) {
    if (!packet.allow(false)) return; // Throw away unwanted packets

    try {
      packet.handled();

      if (packet instanceof StreamPacket stream) {
        packet = StreamReceiver.received(stream);
        if (packet != null) received(packet);
        return;
      }

      var listener = (Cons<Packet>)listeners.get(packet.getClass());
      if (listener != null) listener.get(packet);
      else packet.handleClient();
    } catch (Throwable e) { errorHandler.get(e); }
  }
}
