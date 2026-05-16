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

import arc.func.Cons2;

import com.xpdustry.claj.client.dialogs.*;


public class ClajUi {
  public static JoinDialog join;
  public static CreateRoomDialog create;
  /** Must no be used directly, please use helper methods instead. */
  public static AddServerDialog add;
  public static RoomSettingsDialog settings;
  public static RoomPasswordDialog password;
  public static BrowserDialog browser;

  public static void init() {
    join = new JoinDialog();
    create = new CreateRoomDialog();
    add = new AddServerDialog();
    settings = new RoomSettingsDialog();
    password = new RoomPasswordDialog();
    browser = new BrowserDialog();
  }

  public static void addServer(Cons2<String, String> done) {
    new AddServerDialog().show(done);
  }

  public static void renameServer(String currentName, String currentHost, Cons2<String, String> done) {
    new AddServerDialog().show(currentName, currentHost, done);
  }
}
