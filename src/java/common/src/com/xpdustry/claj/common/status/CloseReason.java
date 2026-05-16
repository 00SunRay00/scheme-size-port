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

package com.xpdustry.claj.common.status;


public enum CloseReason {
  /** Connection closed or an error occurred while hosting a room. */
  error,
  /** Closed without reason. */
  closed,
  /** Incompatible CLaJ client. */
  obsoleteClient,
  /** Old CLaJ client. */
  outdatedClient,
  /** Old CLaJ server. */
  outdatedServer,
  /** Server is shutting down. */
  serverClosed,
  /** The server is notifying as full. */
  serverFull,
  /** The CLaJ server doesn't allows the provided implementation. */
  blacklisted,
  /**
   * The room as been closed by the server because it considered the room as AFK. <br>
   * Here, AFK means that no CLaJ clients has joined the room for a long time.
   * Even if in reality, there are client connected but with another way than CLaJ.
   */
  afk,
  /** The host is sending too many packets. */
  spam;

  public static final CloseReason[] all = values();
}
