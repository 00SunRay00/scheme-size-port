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

import java.util.Iterator;
import java.util.NoSuchElementException;

import arc.Core;
import arc.net.Connection;
import arc.net.DcReason;
import mindustry.Vars;
import mindustry.net.*;
import mindustry.net.Packets.KickReason;

import com.xpdustry.claj.api.ClajProvider;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.api.net.VirtualConnection;


public class MindustryClajProxy extends ClajProxy {
  public MindustryClajProxy(ClajProvider provider) {
    super(provider);
    // Try to fix some issues with entities not loading when receiving world
    forceTcp = true;
  }

  public static boolean isMindustryConnection(Connection con) {
    return con.getArbitraryData() instanceof NetConnection;
  }

  public static NetConnection toMindustryConnection(Connection con) {
    return con != null && con.getArbitraryData() instanceof NetConnection nc ? nc : null;
  }

  public static VirtualConnection toVirtualConnection(NetConnection con) {
    return con instanceof ArcNetProvider.ArcConnection acon &&
           acon.connection instanceof VirtualConnection vcon ? vcon : null;
  }

  public Iterable<NetConnection> getMindustryConnections() {
    return () -> new Iterator<>() {
      final Iterator<VirtualConnection> it = getConnections().iterator();
      NetConnection next;

      @Override
      public boolean hasNext() {
        if (next != null) return true;
        while (it.hasNext()) {
          next = toMindustryConnection(it.next());
          if (next != null) return true;
        }
        return false;
      }

      @Override
      public NetConnection next() {
        if (!hasNext()) throw new NoSuchElementException();
        NetConnection value = next;
        next = null;
        return value;
      }
    };
  }

  public int getMindustryConnectionsSize() {
    int total = 0;
    for (VirtualConnection t : getConnections()) {
      if (isMindustryConnection(t)) total++;
    }
    return total;
  }

  /** @return the associated virtual connection from this proxy only. */
  public VirtualConnection getConnection(NetConnection con) {
    VirtualConnection vcon = toVirtualConnection(con);
    return hasConnection(vcon) ? vcon : null;
  }

  public boolean hasConnection(NetConnection con) {
    return hasConnection(toVirtualConnection(con));
  }

  public void kickAllConnections(KickReason reason) {
    // No way to broadcast kick here
    for (NetConnection con : getMindustryConnections()) con.kick(reason);
  }

  @Override
  public void closeAllConnections(DcReason reason) {
    // Kick players before, if we can
    if (isConnected()) kickAllConnections(KickReason.serverClose);
    super.closeAllConnections(reason);
  }

  public Host getState() {
    // Not very efficient
    Host host = NetworkIO.readServerData(0, "localhost", NetworkIO.writeServerData());
    host.port = Core.settings.getInt("port", Vars.port);
    return host;
  }
}
