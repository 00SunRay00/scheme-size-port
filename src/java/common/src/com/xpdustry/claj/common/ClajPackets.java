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

package com.xpdustry.claj.common;

import com.xpdustry.claj.common.net.stream.*;
import com.xpdustry.claj.common.packets.*;


public class ClajPackets {
  public static void init() {
    ClajNet.register(ConnectionJoinPacket::new);
    ClajNet.register(ConnectionClosedPacket::new);
    ClajNet.register(ConnectionPacketWrapPacket::new);
    ClajNet.register(ConnectionIdlingPacket::new);
    ClajNet.register(RoomCreationRequestPacket::new); // <-- should be the 5th
    ClajNet.register(RoomClosureRequestPacket::new);  // These two MUST not be moved.
    ClajNet.register(RoomClosedPacket::new);          // They are here for compatibility reason.
    ClajNet.register(RoomJoinPacket::new);            // <-- should be the 8th
    ClajNet.register(RoomJoinRequestPacket::new);
    ClajNet.register(RoomJoinAcceptedPacket::new);
    ClajNet.register(RoomJoinDeniedPacket::new);
    ClajNet.register(RoomLinkPacket::new);
    ClajNet.register(RoomConfigPacket::new);
    ClajNet.register(RoomStateRequestPacket::new);
    ClajNet.register(RoomStatePacket::new);
    ClajNet.register(RoomInfoRequestPacket::new);
    ClajNet.register(RoomInfoDeniedPacket::new);
    ClajNet.register(RoomInfoPacket::new);
    ClajNet.register(RoomListRequestPacket::new);
    ClajNet.register(RoomListPacket::new);
    ClajNet.register(ServerInfoPacket::new);
    ClajNet.register(ClajTextMessagePacket::new);
    ClajNet.register(ClajMessagePacket::new);
    ClajNet.register(ClajPopupPacket::new);
    ClajNet.register(StreamHead::new);
    ClajNet.register(StreamChunk::new);
  }


  /** Generic client connection event. */
  public static class Connect implements Packet {
    public String address;
  }

  /** Generic client disconnection event. */
  public static class Disconnect implements Packet {
    public arc.net.DcReason reason;
  }

  /** Generic client idle event. */
  public static class Idle implements Packet {
    public static final Idle instance = new Idle();
  }
}
