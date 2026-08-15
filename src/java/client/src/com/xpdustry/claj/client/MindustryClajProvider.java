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

package com.xpdustry.claj.client;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

import arc.Core;
import arc.func.Cons;
import arc.net.*;
import arc.struct.ObjectMap;
import arc.util.*;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.net.ArcNetProvider;
import mindustry.net.ArcNetProvider.PacketSerializer;
import mindustry.net.Net.NetProvider;
import mindustry.net.NetworkIO;
import mindustry.net.Packets.Connect;

import com.xpdustry.claj.api.*;
import com.xpdustry.claj.common.packets.ConnectionPayloadPacket;
import com.xpdustry.claj.common.status.*;


public class MindustryClajProvider implements ClajProvider {
  public static final ArcNetProvider mindustryProvider;
  public static final Client mindustryClient;
  public static final Server mindustryServer;
  public static final NetListener mindustryServerDispatcher;
  public static final PacketSerializer mindustrySerializer;
  /** Stored in mod file. */
  public static final ClajVersion clajVersion;
  /** CLaJ type is {@code "Mindustry"} for this implementation. */
  public static final ClajType implType;

  private static ByteBuffer magicPacket;

  static {
    try {
      NetProvider provider = Reflect.get(Vars.net, "provider");
      // Loop over all providers in case of a mod already hooking it
      while (!(provider instanceof ArcNetProvider anp)) {
        provider = Reflect.get(provider, "provider");
      }
      mindustryProvider = anp;
    } catch (Exception e) { throw new RuntimeException("Unable to find the ArcNetProvider", e); }
    mindustryClient = Reflect.get(mindustryProvider, "client");
    mindustryServer = Reflect.get(mindustryProvider, "server"); // Safe, we already know that is ArcNetProvider
    mindustryServerDispatcher = Reflect.get(mindustryServer, "dispatchListener"); // Safe, never changed
    mindustrySerializer = new PacketSerializer();
    clajVersion = ClajVersion.of(Main.getMeta().version);
    implType = ClajType.of("Mindustry");

    // Hook Connect listener, so that magic join packet is send before ConnectPacket
    // We really need a method to get a listener...
    @SuppressWarnings("unchecked")
    Cons<Connect> connect = (Cons<Connect>)Reflect.<ObjectMap<Class<?>, Cons<?>>>get(Vars.net, "clientListeners")
                                                  .get(Connect.class);
    if (connect != null) {
      Vars.net.handleClient(Connect.class, p -> {
        if (magicPacket != null) Vars.net.send(magicPacket, true);
        magicPacket = null;
        connect.get(p);
      });
    }
  }

  @Override
  public void postTask(Runnable task) {
    Core.app.post(task);
  }

  @Override
  public ExecutorService getExecutor() {
    return Vars.mainExecutor;
  }

  @Override
  public ClajProxy newProxy() {
    return new MindustryClajProxy(this);
  }

  @Override
  public void handleProxyError(ClajProxy proxy, Throwable error) {
    if (proxy.roomCreated()) {
      proxy.quietErrors = true;
      postTask(() -> Vars.ui.showException("@claj.room.error", error));
    }
    Log.err("Error while hosting the room", error);
  }

  @Override
  public void handlePingerError(ClajPinger pinger, Throwable error) {
    Log.err("Error while running a pinger", error);
  }

  @Override
  public ClajType getType() {
    return implType;
  }

  @Override
  public ClajVersion getVersion() {
    return clajVersion;
  }

  @Override
  public NetListener getConnectionListener(ClajProxy proxy) {
    return mindustryServerDispatcher;
  }

  @Override
  public ByteBuffer writeRoomState(ClajProxy proxy) {
    //thread-safe to do that?
    return (ByteBuffer)NetworkIO.writeServerData().flip();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T readRoomState(long roomId, ClajType type, ByteBuffer buff) {
    return getType().equals(type) ? (T)NetworkIO.readServerData(0, "<unknown>", buff) : null;
  }

  @Override
  public void connectClient(String host, int port, Runnable success, ByteBuffer joinPacket) {
    Vars.logic.reset();
    Vars.net.reset();
    Vars.netClient.beginConnecting();
    magicPacket = joinPacket;
    Vars.net.connect(host, port, () -> Core.app.post(() -> {
      if (!Vars.net.client()) return;
      if (success != null) success.run();
      if (magicPacket != null) Vars.net.send(magicPacket, true); // In case of
      magicPacket = null;
    }));
  }

  @Override
  public ConnectionPayloadPacket.Serializer getPacketWrapperSerializer() {
    return new ConnectionPayloadPacket.Serializer() {
      @Override
      public void read(ConnectionPayloadPacket packet, ByteBufferInput read) {
        packet.object = mindustrySerializer.read(read.buffer);
      }

      @Override
      public void write(ConnectionPayloadPacket packet, ByteBufferOutput write) {
        mindustrySerializer.write(write.buffer, packet.object);
      }
    };
  }

  @Override
  public void showTextMessage(ClajProxy proxy, String text) {
    Call.sendMessage("[scarlet][[CLaJ Server]:[] " + text);
  }

  @Override
  public void showMessage(ClajProxy proxy, MessageType message) {
    Call.sendMessage("[scarlet][[CLaJ Server]:[] " +
      Core.bundle.get("claj.message." + Strings.camelToKebab(message.name())));

    Timer.schedule(() -> {
      if (!proxy.roomCreated()) return;
      proxy.closeRoom(CloseReason.serverClosed);
    }, 5);
  }

  @Override
  public void showPopup(ClajProxy proxy, String text) {
    // UI#showText places the title to the wrong side =/
    //Vars.ui.showText("[scarlet][[CLaJ Server][] ", text);
    Vars.ui.showOkText("[scarlet][[CLaJ Server][] ", text, () -> {});
  }
}
