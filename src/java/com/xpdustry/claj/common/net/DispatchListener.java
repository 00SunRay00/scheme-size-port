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

import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;

import com.xpdustry.claj.common.util.Structs;


//TODO: is locking useful?
public class DispatchListener implements NetListener {
  protected NetListener[] listeners = {};
  /** Whether {@link #idle(Connection)} calls stop if the connection has stopped to be idle. */
  public boolean cutIdle;

  public DispatchListener() {}
  public DispatchListener(boolean cutIdle) { this.cutIdle = cutIdle; }

  public /*synchronized*/ NetListener[] getListeners() {
    return listeners;
  }

  public synchronized void addListener(NetListener listener) {
    if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
    if (Structs.contains(listeners, listener)) return;
    // listeners are always inserted at the start instead of the end. Idk why...
    //listeners = Structs.add(listeners, listener);
    listeners = Structs.insert(listeners, 0, listener);
  }

  public synchronized void removeListener(NetListener listener) {
    if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
    listeners = Structs.remove(listeners, listener);
  }

  @Override
  public void connected(Connection connection) {
    for (NetListener l : getListeners()) l.connected(connection);
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    for (NetListener l : getListeners()) l.disconnected(connection, reason);
  }

  @Override
  public void received(Connection connection, Object object) {
    for (NetListener l : getListeners()) l.received(connection, object);
  }

  @Override
  public void idle(Connection connection) {
    for (NetListener l : getListeners()) {
      l.idle(connection);
      if (cutIdle && !connection.isIdle()) break;
    }
  }
}
