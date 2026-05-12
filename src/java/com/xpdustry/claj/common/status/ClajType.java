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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;


/**
 * Must be in ASCII, {@code 16} <u>BYTES</u> max, and without spaces. <br>
 * Type will be truncated if greater than {@link #SIZE}, and spaces will be replaced by {@code '_'}.
 */
public class ClajType {
  public static final Charset ASCII = Charset.forName("ascii");
  /** In bytes. */
  public static final int SIZE = 16;

  protected final byte[] rawType;
  protected final String type;
  protected final int hash;

  /** @see #encode(String) */
  public ClajType(String str) {
    if (str == null || (str = str.trim()).isEmpty())
      throw new IllegalArgumentException("no type specified");
    rawType = encode(str);
    type = decode(rawType);
    hash = Arrays.hashCode(rawType);
  }

   /** @see #decode(byte[]) */
  public ClajType(byte[] data) {
    if (data.length < 1) throw new IllegalArgumentException("no type specified");
    rawType = truncate(data, SIZE, true);
    type = decode(data);
    hash = Arrays.hashCode(rawType);
  }

  protected ClajType(String str, byte[] data) {
    type = str;
    rawType = data;
    hash = Arrays.hashCode(rawType);
  }

  public String type() {
    return type;
  }

  public int typeSize() {
    return rawType.length;
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public boolean equals(Object object) {
    return this == object || object instanceof ClajType other && Arrays.equals(rawType, other.rawType);
  }

  @Override
  public String toString() {
    return type;
  }

  // IO utilities

  public ByteBuffer write() {
    ByteBuffer out = ByteBuffer.allocate(1 + rawType.length);
    write(out);
    return out;
  }

  public void write(ByteBuffer out) {
    out.put((byte)rawType.length);
    out.put(rawType);
  }

  /** @see #decode(byte[]) */
  public static ClajType read(ByteBuffer in) {
    byte size = in.get();
    if (size < 1) throw new IllegalArgumentException("no type specified");
    byte[] data = new byte[size];
    in.get(data);
    return new ClajType(decode(data), truncate(data, SIZE, false));
  }

  // Encoding utilities

  /** Data will be truncated if length is greater than {@link #SIZE}. */
  public static String decode(byte[] data) {
    return new String(data, 0, Math.min(data.length, SIZE), ASCII).replace(' ', '_');
  }

  /** {@code str} will be truncated if length is greater than {@link #SIZE}. */
  public static byte[] encode(String str) {
    return truncate(str.replace(' ', '_').getBytes(ASCII), SIZE, false);
  }

  public static byte[] truncate(byte[] data, int max, boolean copy) {
    if (copy) data = Arrays.copyOf(data, Math.min(data.length, max));
    else if (data.length > SIZE) data = Arrays.copyOf(data, max);
    return data;
  }

  // Maker

  /** @return the parsed {@link ClajType}, or {@code null} if it's invalid. */
  public static ClajType of(String str) {
    byte[] t = str.replace(' ', '_').getBytes(ASCII);
    return t.length < 1 || t.length > SIZE ? null : new ClajType(t);
  }
}
