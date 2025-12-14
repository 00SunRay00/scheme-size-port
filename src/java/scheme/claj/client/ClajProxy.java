package scheme.claj.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import arc.func.Cons;
import arc.net.ArcNetException;
import arc.net.Client;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Ratekeeper;
import arc.util.Reflect;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import mindustry.Vars;
import mindustry.gen.Call;
import scheme.Main;


public class ClajProxy extends Client implements NetListener {
  public static int defaultTimeout = 5000; //ms
  /** No-op rate keeper, to avoid the player's server from life blacklisting the claj server . */
  private static final Ratekeeper noopRate = new Ratekeeper() {
    @Override
    public boolean allow(long spacing, int cap) {
      return true;
    }
  };
  
  /** For faster get */
  private final IntMap<VirtualConnection> connections = new IntMap<>();
  /** For faster iteration */
  private final Seq<VirtualConnection> orderedConnections = new Seq<>(false);
  private final arc.net.Server server;
  private final NetListener serverDispatcher;
  private Cons<Long> roomCreated;
  private Runnable roomClosed;
  private long roomId = -1;
  private volatile boolean shutdown;

  public ClajProxy() {
    super(32768, 16384, new Serializer());
    addListener(this);
    
    mindustry.net.Net.NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider");

    server = Reflect.get(provider, "server");
    //connections = Reflect.get(provider, "connections");
    serverDispatcher = Reflect.get(server, "dispatchListener");
  }

  /** This method must be used instead of others connect methods */
  public void connect(String host, int udpTcpPort, Cons<Long> roomCreatedCallback, 
                     Runnable roomClosedCallback) throws java.io.IOException {
    roomCreated = roomCreatedCallback;
    roomClosed = roomClosedCallback;
    connect(defaultTimeout, host, udpTcpPort, udpTcpPort);
  }
  
  /** 
   * Redefine {@link #run()} and {@link #stop()} to handle exceptions and restart update loop if needed. <br>
   * And to handle connection idling.
   */
  @Override
  public void run() {
    shutdown = false;
    while(!shutdown) {
      try { 
        update(250); 
        // update idle
        for (int i=0; i<orderedConnections.size; i++) {
          VirtualConnection con = orderedConnections.get(i);
          if (con.isConnected() && con.isIdle())
            con.notifyIdle0();
        }
      } catch (IOException ex) { 
        close(); 
      } catch (ArcNetException ex) {
        if (roomId == -1) {
          close();
          Reflect.set(Client.class, this, "lastProtocolError", ex);
          throw ex;
        }
      }
    }
  }
  
  /** Redefine {@link #run()} and {@link #stop()} to handle exceptions and restart update loop if needed. */
  @Override
  public void stop() {
    if(shutdown) return;
    close();
    shutdown = true;
    Reflect.<java.nio.channels.Selector>get(Client.class, this, "selector").wakeup();
  }
  
  public long roomId() {
    return roomId;
  }
  
  public void closeRoom() {
    roomId = -1;
    if (isConnected()) sendTCP(new ClajPackets.RoomCloseRequestPacket());
    close();
  }

  @Override
  public void connected(Connection connection) {
    // Request the room link
    ClajPackets.RoomCreateRequestPacket p = new ClajPackets.RoomCreateRequestPacket();
    p.version = Main.getVersion(); // TODO: find a better way to gets mod version
    sendTCP(p);
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    roomId = -1;
    if (roomClosed != null) roomClosed.run();
    // We cannot communicate with the server anymore, so close all virtual connections
    orderedConnections.each(c -> c.closeQuietly(reason));
    connections.clear();
    orderedConnections.clear();
  }

  @Override
  public void received(Connection connection, Object object) {
    if (!(object instanceof ClajPackets.Packet)) return;

    else if (object instanceof ClajPackets.ClajMessagePacket) {
      Call.sendMessage("[scarlet][[CLaJ Server]:[] " + ((ClajPackets.ClajMessagePacket)object).message);
    
    } else if (object instanceof ClajPackets.RoomLinkPacket) {
      // Ignore if the room id is received twice
      if (roomId != -1) return;
      
      roomId = ((ClajPackets.RoomLinkPacket)object).roomId;
      // -1 is not allowed since it's used to specify an uncreated room
      if (roomId != -1 && roomCreated != null) roomCreated.get(roomId);
      
    } else if (object instanceof ClajPackets.ConnectionWrapperPacket) {
      // Ignore packets until the room id is received
      if (roomId == -1) return;
      
      int id = ((ClajPackets.ConnectionWrapperPacket)object).conID;
      VirtualConnection con = connections.get(id);
      
      if (con == null) {
        
        // Create a new connection
        if (object instanceof ClajPackets.ConnectionJoinPacket) {
          // Check if the link is the right
          if (((ClajPackets.ConnectionJoinPacket)object).roomId != roomId) {
            ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
            p.conID = id;
            p.reason = DcReason.error;
            sendTCP(p);
            return;
          }

          addConnection(con = new VirtualConnection(this, id));
          con.notifyConnected0();
          // Change the packet rate and chat rate to a no-op version
          ((mindustry.net.NetConnection)con.getArbitraryData()).packetRate = noopRate;
          ((mindustry.net.NetConnection)con.getArbitraryData()).chatRate = noopRate;
        }

      } else if (object instanceof ClajPackets.ConnectionPacketWrapPacket) {
        con.notifyReceived0(((ClajPackets.ConnectionPacketWrapPacket)object).object);

      } else if (object instanceof ClajPackets.ConnectionIdlingPacket) {
        con.setIdle();

      } else if (object instanceof ClajPackets.ConnectionClosedPacket) {
        con.closeQuietly(((ClajPackets.ConnectionClosedPacket)object).reason);
      }
    }
  }
  
  protected void addConnection(VirtualConnection con) {
    connections.put(con.id, con);
    orderedConnections.add(con);
  }
  
  protected void removeConnection(VirtualConnection con) {
    connections.remove(con.id);
    orderedConnections.remove(con);
  }

  
  public static class Serializer extends mindustry.net.ArcNetProvider.PacketSerializer {
    @Override
    public Object read(ByteBuffer buffer) {
      if (buffer.get() == ClajPackets.id) {
        ClajPackets.Packet p = ClajPackets.newPacket(buffer.get());
        p.read(new ByteBufferInput(buffer));
        if (p instanceof ClajPackets.ConnectionPacketWrapPacket) // This one is special
          ((ClajPackets.ConnectionPacketWrapPacket)p).object = super.read(buffer);
        return p;
      }

      buffer.position(buffer.position()-1);
      return super.read(buffer);
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ClajPackets.Packet) {
        ClajPackets.Packet p = (ClajPackets.Packet)object;
        buffer.put(ClajPackets.id).put(ClajPackets.getId(p));
        p.write(new ByteBufferOutput(buffer));
        if (p instanceof ClajPackets.ConnectionPacketWrapPacket) // This one is special
          super.write(buffer, ((ClajPackets.ConnectionPacketWrapPacket)p).object);
        return;
      }

      super.write(buffer, object);
    }
  }
 
  
  /** We can safely remove things, only {@link #sendTCP(Object)} and {@link #sendUDP(Object)} are useful. */
  public static class VirtualConnection extends Connection {
	final Seq<NetListener> listeners = new Seq<>();
    final int id;
    /** 
     * A virtual connection is always connected until we closing it, 
     * so the proxy will notify the server to close the connection in turn,
     * or when the server notifies that the connection has been closed.
     */
    volatile boolean isConnected = true;
    /** The server will notify if the client is idling */
    volatile boolean isIdling = true;
    ClajProxy proxy;
    
    public VirtualConnection(ClajProxy proxy, int id) {
      this.proxy = proxy;
      this.id = id;
      addListener(proxy.serverDispatcher);
    }
 
    @Override
    public int sendTCP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");
      isIdling = false;

      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = id;
      p.isTCP = true;
      p.object = object;
      return proxy.sendTCP(p);
    }

    @Override
    public int sendUDP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");
      isIdling = false;

      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = id;
      p.isTCP = false;
      p.object = object;
      return proxy.sendUDP(p);
    }

    @Override
    public void close(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = isIdling = false;
      if(wasConnected) {
        ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
        p.conID = id;
        p.reason = reason;
        proxy.sendTCP(p);

        notifyDisconnected0(reason);
      }
    }
    
    /** 
     * Close the connection without notify the server about that. <br>
     * Common use is when the server itself saying to close the connection.
     */
    public void closeQuietly(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = isIdling = false;
      if(wasConnected) 
        notifyDisconnected0(reason);
    }
  
    @Override
    public int getID() { return id; }
    @Override
    public boolean isConnected() { return isConnected; }
    @Override
    public void setKeepAliveTCP(int keepAliveMillis) {} // never used
    @Override
    public void setTimeout(int timeoutMillis) {} // never used
    @Override 
    public InetSocketAddress getRemoteAddressTCP() { return isConnected() ? proxy.getRemoteAddressTCP() : null; } 
    @Override
    public InetSocketAddress getRemoteAddressUDP() { return isConnected() ? proxy.getRemoteAddressUDP() : null; }
    @Override
    public int getTcpWriteBufferSize() { return 0; } // never used
    @Override
    public boolean isIdle() { return isIdling; }
    @Override
    public void setIdleThreshold(float idleThreshold) {} // never used
    @Override
    public String toString() { return "Connection " + id; }
    
    /** Only used when sending world data */
    public void addListener(NetListener listener) {
      if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
      listeners.add(listener);
    }
    
    /** Only used when sending world data */
    public void removeListener(NetListener listener) {
      if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
      listeners.remove(listener);
    }
    
    public void notifyConnected0() {
      listeners.each(l -> l.connected(this));
    }
  
    public void notifyDisconnected0(DcReason reason) {
      proxy.removeConnection(this);
      listeners.each(l -> l.disconnected(this, reason));
    }
    
    public void setIdle() {
      isIdling = true;
    }
    
    public void notifyIdle0() {
      listeners.each(l -> isIdle(), l -> l.idle(this));
    }
    
    public void notifyReceived0(Object object) {
      listeners.each(l -> l.received(this, object));
    }
  }
}
