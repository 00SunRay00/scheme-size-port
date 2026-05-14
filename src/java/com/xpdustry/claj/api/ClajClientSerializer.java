package com.xpdustry.claj.api;

import java.nio.ByteBuffer;

import arc.net.ArcNetException;
import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.packets.RawPacket;

public class ClajClientSerializer implements NetSerializer, FrameworkSerializer {

  protected final ThreadLocal<ByteBufferInput> read = Threads.local(ByteBufferInput::new);
  protected final ThreadLocal<ByteBufferOutput> write = Threads.local(ByteBufferOutput::new);

  @Override
  public Object read(ByteBuffer buffer) {
    if (!buffer.hasRemaining()) return null;

    return switch (buffer.get()) {
      case ClajNet.frameworkId -> readFramework(buffer);
      case ClajNet.oldId -> throw new ArcNetException("Received a packet from the old CLaJ protocol");
      case ClajNet.id -> readClaj(buffer);
      default -> {
        buffer.position(buffer.position() - 1);
        throw new ArcNetException("Unknown protocol type: " + buffer.get());
      }
    };
  }

  public Packet readClaj(ByteBuffer buffer) {
    Packet packet = ClajNet.newPacket(buffer.get());

    if (!packet.allow(false)) {
      throw new ArcNetException("Invalid packet type for endpoint: " + packet.getClass());
    }

    ByteBufferInput in = read.get();
    in.buffer = buffer;

    packet.read(in);
    return packet;
  }

  @Override
  public void write(ByteBuffer buffer, Object object) {

    if (object instanceof ByteBuffer buf) {
      buffer.put(buf);

    } else if (object instanceof FrameworkMessage framework) {
      writeFramework(buffer.put(ClajNet.frameworkId), framework);

    } else if (object instanceof Packet packet) {
      writeClaj(buffer, packet);

    } else {
      throw new ArcNetException("Unknown packet type: " + object.getClass());
    }
  }

  public void writeClaj(ByteBuffer buffer, Packet packet) {
    if (!(packet instanceof RawPacket)) {
      buffer.put(ClajNet.id).put(ClajNet.getId(packet));
    }

    ByteBufferOutput out = write.get();
    out.buffer = buffer;

    packet.write(out);
  }

  /*
  // Default methods sends a signed short instead of an unsigned one...
  @Override
  public void writeLength(ByteBuffer buffer, int length) {
    buffer.putChar((char)length);
  }

  @Override
  public int readLength(ByteBuffer buffer) {
    return buffer.getChar();
  }
  */
}
