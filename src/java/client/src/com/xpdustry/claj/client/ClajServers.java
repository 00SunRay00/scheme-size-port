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

import arc.Core;
import arc.func.Cons;
import arc.struct.ArrayMap;
import arc.struct.ObjectMap;
import arc.util.Http;
import arc.util.serialization.Jval;


public class ClajServers {
  public static final String publicServersLink =
      "https://github.com/xpdustry/claj/blob/main/public-servers.hjson?raw=true";
  public static final ArrayMap<String, String> online = new ArrayMap<>(),
                                               custom = new ArrayMap<>();

  public static void refreshOnline(Runnable done, Cons<Throwable> failed) {
    // Public list
    Http.get(publicServersLink, result -> {
      Jval.JsonMap list = Jval.read(result.getResultAsString()).asObject();
      synchronized (online) {
        online.clear();
        for (ObjectMap.Entry<String, Jval> e : list)
          online.put(e.key, e.value.asString());
      }
      Core.app.post(done);
    }, t -> Core.app.post(() -> failed.get(t)));
  }

  @SuppressWarnings("unchecked")
  public static void loadCustom() {
    custom.clear();
    custom.putAll(Core.settings.getJson("claj-custom-servers", ArrayMap.class, String.class, ArrayMap::new));
  }

  public static void saveCustom() {
    Core.settings.putJson("claj-custom-servers", String.class, custom);
  }
}
