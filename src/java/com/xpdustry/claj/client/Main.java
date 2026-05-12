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

package com.xpdustry.claj.client;

import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.util.Time;

import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.KickCallPacket2;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.net.Packets.KickReason;

import com.xpdustry.claj.api.Claj;


public class Main extends Mod {
  public static MindustryClajProvider provider;

  @Override
  public void init() {
    provider = new MindustryClajProvider();
    Claj.init(provider);
    ClajUpdater.schedule();
    initEvents();
    ClajUi.init();
  }

  /** Automatically closes the rooms when quitting the game. */
  public void initEvents() {
    // Pretty difficult to know when the player quits the game,
    // there is no event and StateChangeEvent is not reliable for that...
    Vars.ui.paused.hidden(() -> {
      if (Vars.net.active() && !Vars.state.isMenu()) return;
      Claj.get().closeRooms();
    });
    Vars.ui.restart.hidden(() -> {
      if (Vars.state.isCampaign()) return;
      Time.run(7f, () -> {
        if (Vars.net.active() && !Vars.state.isMenu()) return;
        Claj.get().closeRooms();
      });
    });

    Events.run(EventType.HostEvent.class, this::stopClaj);
    Events.run(EventType.ClientPreConnectEvent.class, this::stopClaj);

    // Hooks NetClient#kick() packet to reconnect to the room
    Vars.net.handleClient(KickCallPacket2.class, p -> {
      p.handleClient();
      if (p.reason != KickReason.serverRestarting) return;
      ClajUi.join.rejoinRoom();
    });

    // Need to revert player limit in case of the mod is removed
    int originalPlayerLimit = Vars.netServer.admins.getPlayerLimit();
    Core.app.addListener(new ApplicationListener() {
      public void dispose() { Vars.netServer.admins.setPlayerLimit(originalPlayerLimit); }
    });
  }

  public void stopClaj() {
    Claj.get().closeRooms();
    Claj.get().cancelPingers();
    ClajUi.join.resetLastLink(); // Avoid reconnect to a room after connecting to a normal server
  }

  /** @return the mod meta, using this class. */
  public static Mods.ModMeta getMeta() {
    Mods.LoadedMod load = Vars.mods.getMod(Main.class);
    if(load == null) throw new IllegalArgumentException("Mod is not loaded yet (or missing)!");
    return load.meta;
  }
}
