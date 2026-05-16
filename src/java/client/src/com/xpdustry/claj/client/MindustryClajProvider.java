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
import arc.net.NetListener;
import arc.net.Server;
import arc.util.*;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.net.ArcNetProvider.PacketSerializer;
import mindustry.net.Net.NetProvider;
import mindustry.net.NetworkIO;

import com.xpdustry.claj.api.*;
import com.xpdustry.claj.common.packets.ConnectionPacketWrapPacket;
import com.xpdustry.claj.common.status.*;


public class MindustryClajProvider implements ClajProvider {
  public static final NetProvider mindustryProvider;
  public static final Server mindustryServer;
  public static final NetListener mindustryServerDispatcher;
  public static final PacketSerializer mindustrySerializer;
  /** Stored in mod file. */
  public static final ClajVersion clajVersion;
  /** CLaJ type is {@code "Mindustry"} for this implementation. */
  public static final ClajType implType;

  static {
    NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider");
    mindustryProvider = provider;
    mindustryServer = Reflect.get(mindustryProvider, "server");
    mindustryServerDispatcher = Reflect.get(mindustryServer, "dispatchListener");
    mindustrySerializer = new PacketSerializer();
    clajVersion = ClajVersion.of("2.4.0");
    implType = ClajType.of("Mindustry");
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
      Vars.ui.showException("@claj.room.error", error);
      proxy.quietErrors = true;
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
    return NetworkIO.writeServerData();
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
    Vars.net.connect(host, port, () -> {
      if (!Vars.net.client()) return;
      if (success != null) success.run();
      //TODO: Override Connect listener to send it before ConnectPacket?
      if (joinPacket != null) Vars.net.send(joinPacket, true);
    });
  }

  @Override
  public ConnectionPacketWrapPacket.Serializer getPacketWrapperSerializer() {
    return new ConnectionPacketWrapPacket.Serializer() {
      @Override
      public void read(ConnectionPacketWrapPacket packet, ByteBufferInput read) {
        packet.object = mindustrySerializer.read(read.buffer);
      }

      @Override
      public void write(ConnectionPacketWrapPacket packet, ByteBufferOutput write) {
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
