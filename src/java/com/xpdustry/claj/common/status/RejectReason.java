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


public enum RejectReason {
  /** Rejected without indications. */
  error,
  /** The server is reporting as full. */
  serverFull,
  /** The server is closing */
  serverClosing,
  /** No room with this id, or id is {@code -1} (which is an invalid id). */
  roomNotFound,
  /** Room full. */
  roomFull,
  /** A password is required to join the room. */
  passwordRequired,
  /** The provided password is invalid. */
  invalidPassword,
  /** The CLaJ implementation of the room host is not the same as the provided one. */
  incompatible;

  public static final RejectReason[] all = values();
}
