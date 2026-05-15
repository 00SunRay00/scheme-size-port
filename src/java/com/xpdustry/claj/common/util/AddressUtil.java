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

package com.xpdustry.claj.common.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import arc.net.Connection;


public class AddressUtil {
  public static long hash(Connection connection) {
    return hash(connection.getRemoteAddressTCP().getAddress());
  }

  /** Hashes the address using FNV-1a 64-bit. */
  public static long hash(InetAddress address) {
    long hash = 0xcbf29ce484222325L;
    for (byte b : address.getAddress()) {
      hash ^= b & 0xff;
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  /** Generates an IPv6 address using the hash. */
  public static InetAddress generate(long addressHash) {
    byte[] bytes = new byte[16];
    // Use IPv6 Unique Local Address (fc00::/7), specifically fd00::/8
    bytes[0] = (byte)0xfd;

    // Fill the last 8 bytes with the hash
    for (int i = 0; i < 8; i++) {
      bytes[8 + i] = (byte)((addressHash >> ((7 - i) * 8)) & 0xFF);
    }

    try { return InetAddress.getByAddress(bytes); }
    catch (Exception _) { return null; } // cannot happen
  }

  /** Hashes the address then converts it back to an address. */
  public static InetAddress obfuscate(InetAddress address) {
    return generate(hash(address));
  }

  public static InetAddress get(Connection con) {
    InetSocketAddress a = con.getRemoteAddressTCP();
    return a == null ? null : a.getAddress();
  }

  public static String getString(Connection con) {
    InetSocketAddress a = con.getRemoteAddressTCP();
    return a == null ? null : a.getAddress().getHostAddress();
  }

  public static String encodeId(Connection con) {
    return encodeId(con.getID());
  }

  public static String encodeId(int conId) {
    char[] out = new char[(Integer.SIZE >> 2) + 2];
    out[0] = '0';
    out[1] = 'x';
    for (int i=out.length-1; i>=2; i--) {
      out[i] = "0123456789abcdef".charAt(conId & 0xF);
      conId >>>= 4;
    }
    return new String(out);
  }
}
