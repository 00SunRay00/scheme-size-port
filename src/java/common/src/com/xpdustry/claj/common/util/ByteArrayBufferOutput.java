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

import java.nio.ByteBuffer;
import java.io.*;

import arc.util.io.ByteBufferOutput;
import arc.util.io.FastDeflaterOutputStream;
import arc.util.io.ReusableByteOutStream;


/**
 * Writes data into a {@link ByteArrayOutputStream}, optionally compressible,
 * and maintains a {@link ByteBuffer} to the data.
 * @apiNote Don't forget to {@link #close} or {@link #flush} after writing!
 */
public class ByteArrayBufferOutput extends ByteBufferOutput implements Closeable, Flushable {
  public final ReusableByteOutStream back;
  public final DataOutputStream stream;
  public final boolean compressed;

  public ByteArrayBufferOutput() { this(512, false); }
  public ByteArrayBufferOutput(int initialCapacity) { this(initialCapacity, false); }
  public ByteArrayBufferOutput(int initialCapacity, boolean compress) {
    back = new ReusableByteOutStream(initialCapacity);
    stream = new DataOutputStream(compress ? new FastDeflaterOutputStream(back) : back);
    buffer = ByteBuffer.wrap(back.getBytes());
    compressed = compress;
  }

  @Override
  public void write(int i) {
    try { stream.write(i); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void write(byte[] b) {
    try { stream.write(b); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void write(byte[] b, int off, int len) {
    try { stream.write(b, off, len); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeBoolean(boolean v) {
    try { stream.writeBoolean(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeByte(int v) {
    try { stream.writeByte(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeShort(int v) {
    try { stream.writeShort(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeChar(int v) {
    try { stream.writeChar(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeInt(int v) {
    try { stream.writeInt(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeLong(long v) {
    try { stream.writeLong(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeFloat(float v) {
    try { stream.writeFloat(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeDouble(double v) {
    try { stream.writeDouble(v); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeBytes(String s) {
    try { stream.writeBytes(s); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeChars(String s) {
    try { stream.writeChars(s); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

  @Override
  public void writeUTF(String s) {
    try { stream.writeUTF(s); }
    catch (IOException e) { throw new RuntimeException(e); }
    //updateBuffer();
  }

 @Override
  public void flush() {
    try { stream.flush(); }
    catch (IOException e) { throw new RuntimeException(e); }
    updateBuffer();
  }

  @Override
  public void close() {
    try { stream.close(); }
    catch (IOException e) { throw new RuntimeException(e); }
    updateBuffer();
  }

  /** Will update the backed {@link ByteBuffer} if needed. */
  public void updateBuffer() {
    if (back.getBytes() != buffer.array()) buffer = ByteBuffer.wrap(back.getBytes());
    buffer.position(back.size());
  }
}
