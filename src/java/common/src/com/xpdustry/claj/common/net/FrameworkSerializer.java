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

import java.nio.ByteBuffer;

import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.net.FrameworkMessage.DiscoverHost;
import arc.net.FrameworkMessage.KeepAlive;
import arc.net.FrameworkMessage.Ping;
import arc.net.FrameworkMessage.RegisterTCP;
import arc.net.FrameworkMessage.RegisterUDP;


public interface FrameworkSerializer extends NetSerializer {

  default void writeFramework(ByteBuffer buffer, FrameworkMessage message) {
    if (message instanceof Ping ping) buffer.put((byte)0).putInt(ping.id).put(ping.isReply ? (byte)1 : 0);
    else if (message instanceof DiscoverHost) buffer.put((byte)1);
    else if (message instanceof KeepAlive) buffer.put((byte)2);
    else if (message instanceof RegisterUDP udp) buffer.put((byte)3).putInt(udp.connectionID);
    else if (message instanceof RegisterTCP tcp) buffer.put((byte)4).putInt(tcp.connectionID);
  }

  default FrameworkMessage readFramework(ByteBuffer buffer) {
    byte id = buffer.get();

    if (id == 0) {
      Ping p = new Ping();
      p.id = buffer.getInt();
      p.isReply = buffer.get() == 1;
      return p;
    } else if (id == 1) {
      return FrameworkMessage.discoverHost;
    } else if (id == 2) {
      return FrameworkMessage.keepAlive;
    } else if (id == 3) {
      RegisterUDP p = new RegisterUDP();
      p.connectionID = buffer.getInt();
      return p;
    } else if (id == 4) {
      RegisterTCP p = new RegisterTCP();
      p.connectionID = buffer.getInt();
      return p;
    } else {
      throw new RuntimeException("Unknown framework message!");
    }
  }

  //byte frameworkId();
}
