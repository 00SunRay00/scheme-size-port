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

package com.xpdustry.claj.server;

import java.net.InetAddress;

import arc.net.Connection;
import arc.net.DcReason;
import arc.util.Log;
import arc.util.Ratekeeper;

import com.xpdustry.claj.common.net.stream.PreparedStream;
import com.xpdustry.claj.common.net.stream.StreamSender;
import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.util.AddressUtil;


public class ClajConnection {
  public final Connection connection;
  public final InetAddress address;
  public final String saddress;
  public final int id;
  /** hex version of {@link #id}. */
  public final String sid;
  public final Ratekeeper packetRate;
  protected ClajRoom room;

  public ClajConnection(Connection connection) {
    if (connection == null) throw new NullPointerException("connection is null");
    this.connection = connection;
    address = AddressUtil.get(connection);
    if (address == null) throw new IllegalArgumentException("no address found for this connection");
    saddress = AddressUtil.getString(connection);
    id = connection.getID();
    sid = AddressUtil.encodeId(connection);
    packetRate = new Ratekeeper();
  }

  /** The room where the connection is right now. */
  public ClajRoom currentRoom() {
    return room;
  }

  public boolean isRoomHost() {
    return room != null && room.host == this;
  }

  public boolean isConnected() {
    return connection.isConnected();
  }

  /** Send via TCP. */
  public void send(Object object) { send(object, true); }
  public void send(Object object, boolean reliable) {
    if (!isConnected()) return;
    try {
      if(reliable) connection.sendTCP(object);
      else connection.sendUDP(object);
    } catch (Exception e) { // Should not happen
      Log.err(e);
      Log.err("Error sending packet to connection @. Disconnecting invalid client!", sid);
      close(DcReason.error);
    }
  }

  public void sendStream(Packet packet) {
    StreamSender.send(connection, packet);
  }

  public void sendStream(PreparedStream stream) {
    stream.send(connection);
  }

  public void close() { close(DcReason.closed); }
  public void close(DcReason reason) {
    connection.close(reason);
  }
}
