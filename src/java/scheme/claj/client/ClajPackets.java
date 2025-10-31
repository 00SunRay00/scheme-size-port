package scheme.claj.client;

import arc.func.Prov;
import arc.net.DcReason;
import arc.struct.ArrayMap;
import arc.util.ArcRuntimeException;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


/** @implNote This class must be the same for client and server. */
public class ClajPackets {
  /** Identifier for CLaJ packets */
  public static final byte id = -4; /*doesn't uses other claj packet identifier to avoid problems*/
  
  protected static final ArrayMap<Class<?>, Prov<? extends Packet>> packets = new ArrayMap<>();
  
  static {
    register(ConnectionPacketWrapPacket::new);
    register(ConnectionClosedPacket::new);
    register(ConnectionJoinPacket::new);
    register(ConnectionIdlingPacket::new);
    register(RoomCreationRequestPacket::new);
    register(RoomClosureRequestPacket::new);
    register(RoomClosedPacket::new);
    register(RoomLinkPacket::new);
    register(RoomJoinPacket::new);
    register(ClajMessagePacket::new);
    register(ClajMessage2Packet::new);
    register(ClajPopupPacket::new);
  }

  
  public static <T extends Packet> void register(Prov<T> cons) {
    packets.put(cons.get().getClass(), cons);
  }

  public static byte getId(Packet packet) {
    int id = packets.indexOfKey(packet.getClass());
    if(id == -1) throw new ArcRuntimeException("Unknown packet type: " + packet.getClass());
    return (byte)id;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Packet> T newPacket(byte id) {
    if (id < 0 || id >= packets.size) throw new ArcRuntimeException("Unknown packet id: " + id);
    return ((Prov<T>)packets.getValueAt(id)).get();
  }

  /****************************/
  
  public static abstract class Packet {
    public void read(ByteBufferInput read) {};
    public void write(ByteBufferOutput write) {};
  }
  
  public static abstract class ConnectionWrapperPacket extends Packet {
    public int conID = -1;
 
    public void read(ByteBufferInput read) {
      conID = read.readInt();
      read0(read);
    }
    
    public void write(ByteBufferOutput write) {
      write.writeInt(conID);
      write0(write);
    }
    
    protected void read0(ByteBufferInput read) {};
    protected void write0(ByteBufferOutput write) {};
  }

  /** Special packet for connection packet wrapping. */
  public static class ConnectionPacketWrapPacket extends ConnectionWrapperPacket {
    /** serialization will be done by the proxy */
    public Object object;
    /** only for server usage */
    public java.nio.ByteBuffer buffer;
    
    public boolean isTCP;
    
    protected void read0(ByteBufferInput read) {
      isTCP = read.readBoolean();
    }
    
    protected void write0(ByteBufferOutput write) {
      write.writeBoolean(isTCP);
    }
  }
  
  public static class ConnectionClosedPacket extends ConnectionWrapperPacket {
    private static final DcReason[] reasons = DcReason.values();

    public DcReason reason;

    protected void read0(ByteBufferInput read) {
      reason = reasons[read.readByte()];
    }

    protected void write0(ByteBufferOutput write) {
      write.writeByte(reason.ordinal());
    }
  }
  
  public static class ConnectionJoinPacket extends ConnectionWrapperPacket {
    public long roomId = -1;

    protected void read0(ByteBufferInput read) {
      roomId = read.readLong();
    }
    
    protected void write0(ByteBufferOutput write) {
      write.writeLong(roomId);
    }
  }
  
  public static class ConnectionIdlingPacket extends ConnectionWrapperPacket {
  }
  
  public static class RoomCreationRequestPacket extends Packet {
    public String version;
    
    public void read(ByteBufferInput read) {
      if (read.buffer.hasRemaining()) {
        try { version = read.readUTF(); }
        catch (Exception e) { throw new RuntimeException(e); }  
      }
    }
    
    public void write(ByteBufferOutput write) {
      try { write.writeUTF(version); }
      catch (Exception e) { throw new RuntimeException(e); }
    }
  }
  
  public static class RoomClosureRequestPacket extends Packet {
  }
  
  public static class RoomClosedPacket extends Packet {
    public static enum CloseReason {
      /** Closed without reason */
      closed,
      /** Incompatible claj client */
      obsoleteClient, 
      /** Old claj version */
      outdatedVersion,
      /** Server is shutting down */
      serverClosed;
      
      public static final CloseReason[] all = values();
    }
    
    
    public CloseReason reason;
    
    public void read(ByteBufferInput read) {
      reason = CloseReason.all[read.readByte()]; 
    }
    
    public void write(ByteBufferOutput write) {
      write.writeByte(reason.ordinal());
    }
  }
  
  public static class RoomLinkPacket extends Packet {
    public long roomId = -1;
    
    public void read(ByteBufferInput read) {
      roomId = read.readLong();
    }
    
    public void write(ByteBufferOutput write) {
      write.writeLong(roomId);
    }
  }
  
  public static class RoomJoinPacket extends RoomLinkPacket {
  }
  
  public static class ClajMessagePacket extends Packet {
    public String message;
    
    public void read(ByteBufferInput read) {
      try { message = read.readUTF(); }
      catch (Exception e) { throw new RuntimeException(e); }
    }
    
    public void write(ByteBufferOutput write) {
      try { write.writeUTF(message); }
      catch (Exception e) { throw new RuntimeException(e); }
    }
  }
  
  public static class ClajMessage2Packet extends Packet {
    public static enum MessageType {
      serverClosing,
      packetSpamming,
      alreadyHosting,
      roomClosureDenied,
      conClosureDenied;
      
      public static final MessageType[] all = values();
    }
    
    
    public MessageType message;
    
    public void read(ByteBufferInput read) {
      message = MessageType.all[read.readByte()]; 
    }
    
    public void write(ByteBufferOutput write) {
      write.writeByte(message.ordinal());
    }
  }
  
  public static class ClajPopupPacket extends ClajMessagePacket {
  }
}
