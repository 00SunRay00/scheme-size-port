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

import arc.Core;
import arc.net.Connection;
import arc.util.Ratekeeper;

import mindustry.Vars;
import mindustry.net.*;
import mindustry.net.Packets.KickReason;

import com.xpdustry.claj.api.ClajProvider;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.common.packets.ConnectionJoinPacket;
import com.xpdustry.claj.common.util.Structs;


public class MindustryClajProxy extends ClajProxy {
  //TODO: still useful?
  /** No-op rate-keeper to prevent the local mindustry server from life blacklisting the claj server. */
  private static final Ratekeeper noopRate = new Ratekeeper() {
    @Override
    public boolean allow(long spacing, int cap) {
      return true;
    }
  };

  public MindustryClajProxy(ClajProvider provider) {
    super(provider);

    // Modify listener to set the noop rate
    receiver.handle(ConnectionJoinPacket.class, p -> {
      NetConnection net = toMindustryConnection(getConnection(p.conID));
      if (net == null) return;
      // Change the packet rate and chat rate to a no-op version to avoid a potential life blacklisting
      net.packetRate = noopRate;
      net.chatRate = noopRate;
    });
  }

  public Iterable<NetConnection> getMindustryConnections() {
    return Structs.generator(getConnections(),
                             MindustryClajProxy::isMindustryConnection,
                             MindustryClajProxy::toMindustryConnection);
  }

  public int getMindustryConnectionsSize() {
    return Structs.count(getConnections(), MindustryClajProxy::isMindustryConnection);
  }

  public static boolean isMindustryConnection(Connection connection) {
    return connection.getArbitraryData() instanceof NetConnection;
  }

  public static NetConnection toMindustryConnection(Connection connection) {
    return connection != null && connection.getArbitraryData() instanceof NetConnection nc ? nc : null;
  }

  public void kickAllConnections(KickReason reason) {
    for (NetConnection con : getMindustryConnections())
      con.kick(reason);
  }

  @Override
  public void closeRoom() {
    // Kick players before
    kickAllConnections(KickReason.serverClose);
    super.closeRoom();
  }

  public Host getState() {
    // Not very efficient
    Host host = NetworkIO.readServerData(0, "localhost", NetworkIO.writeServerData());
    host.port = Core.settings.getInt("port", Vars.port);
    return host;
  }
}
