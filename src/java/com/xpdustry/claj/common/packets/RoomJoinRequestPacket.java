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

package com.xpdustry.claj.common.packets;

import com.xpdustry.claj.common.status.ClajType;

/**
 * Only exists for compatibility with older versions. <br>
 * This packet is used to validate join request, so the connection will not be added to the room. <br>
 * {@link RoomJoinPacket} will be sent by the actual client connection and added to the room if all right. <br>
 * But no reply are sent, so no reason know what was wrong, if the provided values are invalid.
 */
public class RoomJoinRequestPacket extends RoomJoinPacket {
  /** Max packet size: roomId + withPassword + password + type */
  public static final int SIZE = Long.BYTES + 1 + Short.BYTES + ClajType.SIZE;

  public RoomJoinPacket toJoinPacket() {
    RoomJoinPacket p = new RoomJoinPacket();
    p.roomId = roomId;
    p.withPassword = withPassword;
    p.password = password;
    p.type = type;
    return p;
  }
}
