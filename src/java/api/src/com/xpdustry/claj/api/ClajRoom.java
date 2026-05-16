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

package com.xpdustry.claj.api;

import com.xpdustry.claj.common.status.ClajType;


public class ClajRoom<T> {
  public final long roomId;
  /** Note that for privacy, the server will never notify this. */
  public boolean isPublic = true;
  /** Whether the room needs a password to join, or not. */
  public boolean isProtected;
  /** Only presents if the room is public and the server has retrieved his state. */
  public T state;
  /** The link to the room. */
  public ClajLink link;
  /** Room implementation type. */
  public ClajType type;
  /** Current number of client in the room. */
  public int clients;
  /** Maximum number of CLaJ client allowed in this room. {@code 0} means no limit. */
  public int maxClients;

  public ClajRoom(long roomId) {
    this.roomId = roomId;
  }

  public ClajRoom(long roomId, boolean isPublic, boolean isProtected, T state, ClajLink link, ClajType type, 
                  int clients, int maxClients) {
    this.roomId = roomId;
    this.isPublic = isPublic;
    this.isProtected = isProtected;
    this.state = state;
    this.link = link;
    this.type = type;
    this.clients = clients;
    this.maxClients = maxClients;
  }
}
