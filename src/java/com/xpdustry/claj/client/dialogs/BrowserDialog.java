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

package com.xpdustry.claj.client.dialogs;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Time;

import mindustry.Vars;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.net.Host;
import mindustry.net.Packets.KickReason;
import mindustry.ui.MobileButton;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.PaletteDialog;
//import mindustry.ui.fragments.MenuFragment.MenuButton;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajRoom;
import com.xpdustry.claj.client.ClajServers;
import com.xpdustry.claj.client.ClajUi;
import com.xpdustry.claj.client.dialogs.CreateRoomDialog.Server;


public class BrowserDialog extends BaseDialog {
  public static float MIN_CARD_SIZE = 400f, MAX_CARD_SIZE = 500f, SCREEN_MAX = 0.9f;
  public static int MAX_COLUMNS = 6;

  boolean refreshingList;
  String serverSearch = "";
  final TextButtonStyle style;
  final Table hosts = new Table();

  final ObjectSet<Server> refreshing = new ObjectSet<>();
  final ObjectMap<Server, Seq<ClajRoom<Host>>> serverRooms = new ObjectMap<>();
  final ObjectMap<Server, Table> servers = new ObjectMap<>();

  //final BrowserDialogTest test = new BrowserDialogTest();

  public BrowserDialog() {
    super("@claj.browser.title");
    makeButtonOverlay();
    addCloseButton(Vars.mobile ? 190f : 210f);
    buttons.button("@claj.join.name", Icon.play, ClajUi.join::show);

    style = Reflect.get(Vars.ui.join, "style"); // just too lazy

    keyDown(KeyCode.f5, this::refreshAll);
    shown(() -> {
      setup();
      refreshAll();
    });
    onResize(this::rebuild);

    // Add the CLaJ browser button bellow Play > Load Save on PC, and after Quit button on mobile
    addButton();
    Events.run(EventType.ResizeEvent.class, this::addButton);
  }

  void addButton() {
    // Cannot easily get the container
    Table menu = locateMenu("buttons");
    if (menu == null) return;

    if (Vars.mobile) {
      if (Core.graphics.isPortrait()) menu.row();
      menu.add(new MobileButton(Icon.host, "@claj.browser.name", () -> checkPlay(this::show)));

    } else {
      Table submenu = locateMenu("submenu");
      if (submenu == null) return;

      // Dynamically adds the claj browser button, since this is the only way in v7
      menu.getCells().first().get().clicked(() -> {
        if (submenu.find("claj-browser") != null) return; // avoids button duplication
        submenu.button("@claj.browser.name", Icon.host, Styles.flatToggleMenut, () -> {
          checkPlay(this::show);
          Reflect.set(Vars.ui.menufrag, "currentMenu", null);
          Reflect.invoke(Vars.ui.menufrag, "fadeOutMenu");
        }).marginLeft(11f).with(b -> b.name = "claj-browser").row();
      });
    }

    // Replace by this when v7 support is dropped
    /*
    // Add the CLaJ browser button bellow Play > Load Save
    if (Vars.mobile) {
      Events.on(EventType.ResizeEvent.class, e -> {
        Table menu = Vars.ui.menuGroup.find("buttons");
        if (menu == null) return;
        if (Core.graphics.isPortrait()) menu.row();
        menu.add(new MobileButton(Icon.host, "@claj.browser.name", () -> checkPlay(this::show)));
      });
    } else {
      Seq<MenuButton> buttons = Vars.ui.menufrag.desktopButtons;
      MenuButton button = buttons.find(m -> m.text.equals("@play"));
      if (button != null && button.submenu != null) buttons = button.submenu;
      buttons.add(new MenuButton("@claj.browser.name", Icon.host, () -> checkPlay(this::show)));
    }
    */
  }

  Table locateMenu(String name) {
    Table menu = Vars.ui.menuGroup.find(name);
    if (menu == null) Log.err("Unable to place claj buttons, main container not found!");
    return menu;
  }

  public void rebuild() {
    setup();
    if (refreshingList) return;

    servers.clear();
    for (var e : ClajServers.online) {
      // For parsing
      Server temp = new Server();
      temp.name = e.key;
      temp.set(e.value);

      Server server = null;
      for (Server s : serverRooms.keys()) {
         if (s.address != null && s.address.equals(temp.address) && s.port == temp.port) {
           server = s;
           break;
         }
      }
      if (server == null) server = temp;

      Table rooms = new Table().top().left();
      servers.put(server, rooms);

      final Server server0 = server;
      section(e.key, e.value, rooms, hosts, () -> refreshServer(server0, rooms));
      if (!serverRooms.containsKey(server)) refreshServer(server, rooms);
    }

    filterRooms();
  }

  public void refreshAll() {
    if (refreshingList) return; // Avoid to re-trigger a refresh while refreshing
    refreshingList = true;
    serverRooms.clear();
    servers.clear();
    refreshing.clear();
    hosts.clear();
    Claj.get().cancelPingers(); // cancel previous pings and listing

    hosts.table(t -> {
      t.add("@claj.servers.fetching").padRight(3);
      t.label(() -> Strings.animated(Time.time, 4, 11, ".")).color(Pal.accent);
    }).center().growX().padTop(5).padBottom(5);

    ClajServers.refreshOnline/*test.mockRefreshOnline*/(() -> {
      refreshingList = false;
      hosts.clear();

      for (var e : ClajServers.online) {
        Server server = new Server();
        server.name = e.key;
        server.set(e.value);

        Table rooms = new Table().top().left();
        section(e.key, e.value, rooms, hosts, () -> refreshServer(server, rooms));
        refreshServer(server, rooms);
      }
    }, e -> {
      refreshingList = false;
      hosts.clear();
      hosts.add("@claj.servers.check-internet");
      Vars.ui.showException("@claj.servers.fetch-failed", e);
    });
  }

  public void refreshServer(Server server, Table table) {
    // Don't refresh if hidden
    if (Core.settings.getBool("claj-collapsed-" + server.name, false)) return;
    if (refreshingList || !refreshing.add(server)) return;
    servers.put(server, table);
    pingAndListServer(server, table, () -> refreshing.remove(server), _ -> refreshing.remove(server));
  }

  public void pingAndListServer(Server server, Table dest, Runnable done, Cons<Exception> error) {
    dest.clear();
    dest.table(inner -> {
      Table label = new Table().center();
      Table ping = new Table().left();
      inner.stack(label, ping).growX().row();

      label.add("@claj.browser.listing").padTop(5).padBottom(5);
      label.label(() -> Strings.animated(Time.time, 4, 11, ".")).pad(5, 3, 5, 0).color(Pal.accent);

      Claj.get().pingHost/*test.mockPingHost*/(server.address, server.port, s -> {
        server.compatible = s.version == Claj.get().provider.getVersion().majorVersion;
        server.outdated = s.version < Claj.get().provider.getVersion().majorVersion;
        if (server.compatible) {
          ping.image(Icon.ok, Color.green).padRight(7).left();
        } else {
          ping.clear();
          label.clear();
          ping.image(Icon.warning, Color.yellow).padBottom(3).left().get().scaleBy(-0.22f);
          label.add("@claj.browser.incompatible");

        }
        if (Vars.mobile) ping.row();
        ping.add(s.ping + "ms", Color.lightGray, 0.91f).left();
        if (server.compatible) listRooms(server, dest, done, error);
        else done.run();
      }, e -> {
        ping.clear();
        label.clear();
        ping.image(Icon.cancel, Color.red).left();
        label.add("@claj.browser.timeout").padTop(5).padBottom(5);
        error.get(e);
      });
    }).padLeft(10).padRight(10).growX().row();
  }

  public void listRooms(Server server, Table dest, Runnable done, Cons<Exception> error) {
    int columns = columns();
    Claj.get().<Host>serverRooms/*test.mockServerRooms*/(server.address, server.port, r -> {
      serverRooms.put(server, r);
      setRooms(dest, r, columns);
      done.run();
    }, e -> {
      dest.clear();
      dest.table(inner -> {
        Table label = new Table().center();
        Table ping = new Table().left();
        inner.stack(label, ping).growX().row();

        ping.image(Icon.cancel, Color.red).left();
        label.add("@claj.browser.timeout").padTop(5).padBottom(5);
      }).padLeft(10).padRight(10).growX().row();
      error.get(e);
    });
  }

  protected void setRooms(Table dest, Seq<ClajRoom<Host>> rooms, int columns) {
    dest.clear();
    if (rooms.isEmpty()) {
      dest.table(t -> t.add("@claj.browser.no-rooms")).padTop(5).padBottom(5).growX().row();
      return;
    }
    boolean[] hasResult = {false};
    rooms.each(room -> {
      if (isHidden(room.state)) return;
      hasResult[0] = true;
      addRoom(dest, room);
      if (dest.getChildren().size % columns == 0) dest.row();
    });
    if (hasResult[0]) return;
    dest.table(t -> t.add("@claj.browser.no-result")).padTop(5).padBottom(5).growX().row();
  }

  public boolean isHidden(Host host) {
    if (serverSearch.isEmpty()) return false;
    return host == null
        || !Strings.stripColors(host.name).toLowerCase().contains(serverSearch)
        && !Strings.stripColors(host.mapname).toLowerCase().contains(serverSearch)
        && !(host.modeName != null && Strings.stripColors(host.modeName).toLowerCase().contains(serverSearch))
        && !(host.mode != null && host.mode.name().toLowerCase().contains(serverSearch));
  }

  public void addRoom(Table dest, ClajRoom<Host> room) {
    Host host = room.state;
    float w = targetWidth(), tw = w - 20f;
    String versionString = getVersionString(host);

    dest.button(inner -> {
      inner.table(Tex.whiteui, title -> {
        title.setColor(Pal.gray);
        if (host == null) title.add("...", Pal.accent).padLeft(10).padTop(5).growX().left();
        else {
          title.add(host.name, Styles.outlineLabel).padLeft(10).width(tw).growX().left().ellipsis(true)
               .self(c -> c.padTop(versionString.isEmpty() ? 5 : 3));
          if (!versionString.isEmpty())
            title.row().add(versionString, Styles.outlineLabel).padLeft(10).width(tw).growX().left().ellipsis(true);
        }
      }).height(40f).growX().row();

      inner.stack(new Table(Tex.whitePane, desc -> {
        desc.setColor(Pal.gray);
        if (host == null) {
          desc.add("").left().row();
          desc.add("@claj.browser.no-room-data").center().row();
          desc.add("").padBottom(10).left().row();
          return;
        }
        desc.top().left();
        desc.add("[lightgray]" + (Core.bundle.format(
          "players" + (host.players == 1 && host.playerLimit <= 0 ? ".single" : ""),
            (host.players == 0 ? "[lightgray]" : "[accent]") + host.players +
            (host.playerLimit > 0 ? "[lightgray]/[accent]" + host.playerLimit : "") + "[lightgray]")
        )).left().ellipsis(true).row();
        desc.add("[lightgray]" +
                 Core.bundle.format("save.map", "[accent]" + host.mapname) +
                 "[lightgray] / [accent]" +
                 (host.modeName == null ? host.mode.toString() : host.modeName)
        ).width(tw - 10).left().ellipsis(true).row();
        desc.add("[lightgray]" + Core.bundle.format("save.wave", "[accent]" + host.wave)).left()
            .ellipsis(true).padBottom(10).row();

      }), new Table(t -> {
        t.bottom().right();
        t.table(Tex.whiteui, foot -> {
          foot.setColor(Pal.gray);
          if (room.isProtected)
            foot.image(Icon.lock).padLeft(10).padBottom(7).padRight(-5).left().size(20).get().setScale(0.55f);
          foot.add(room.link.encodedRoomId, Color.lightGray, 0.8f).padLeft(5).padBottom(-2).padRight(3).growX().right().labelAlign(Align.right);
        }).minWidth(/*w*/ MIN_CARD_SIZE / 3f).height(20f).pad(5).right();
      })).grow().row();
    }, style, () -> preJoin(room)).width(w).padBottom(7f).padRight(4f).top().left().growY();
  }

  public void preJoin(ClajRoom<Host> room) {
    if (room.state != null) Events.fire(new EventType.ClientPreConnectEvent(room.state));
    if (!Core.settings.getBool("server-disclaimer", false)) {
      Vars.ui.showCustomConfirm("@warning", "@servers.disclaimer", "@ok", "@back", () -> {
        Core.settings.put("server-disclaimer", true);
        safeJoin(room);
      }, () -> Core.settings.put("server-disclaimer", false));
    } else safeJoin(room);
  }

  public void safeJoin(ClajRoom<Host> room) {
    if (room.state != null) {
      int version = room.state.version;
      if(version != Version.build && Version.build != -1 && version != -1){
          Vars.ui.showInfo("[scarlet]" +
                           (version > Version.build ? KickReason.clientOutdated : KickReason.serverOutdated) +
                           "\n[]" + Core.bundle.format("server.versions", Version.build, version));
          return;
      }
    }
    //TODO: warn if no state received?
    join(room);
  }

  public void join(ClajRoom<Host> room) {
    ClajUi.join.joinRoom(room.link, room.isProtected);
  }

  public void section(String name, String host, Table src, Table dest, Runnable refresh) {
    Collapser coll = new Collapser(src, Core.settings.getBool("claj-collapsed-" + name, false));
    dest.table(head -> {
      if (Vars.mobile) {
        head.table(inner -> {
          inner.left().bottom();
          inner.add(name, Pal.accent).left().row();
          inner.add('(' + host + ')', Pal.lightishGray).left();
        }).pad(5).padLeft(10).growX();
      } else {
        head.add(name, Pal.accent).pad(5).padLeft(10).left().bottom();
        head.add('(' + host + ')', Pal.lightishGray).pad(5).growX().left().bottom();
      }

      boolean[] firstRefresh = {true};
      if (refresh != null){
        Vars.ui.addDescTooltip(head.button(Icon.refresh, Styles.emptyi, () -> {
          if (!coll.isCollapsed()) firstRefresh[0] = false;
          refresh.run();
        }).size(40f).padRight(3).right().get(), "@claj.browser.refresh-rooms");
      }
      Button button = head.button(Icon.downOpen, Styles.emptyi, () -> {
        coll.toggle(false);
        Core.settings.put("claj-collapsed-" + name, coll.isCollapsed());
        // If it was hidden, refresh at first shown.
        if (!coll.isCollapsed() && firstRefresh[0]) {
          if (refresh != null) refresh.run();
          firstRefresh[0] = false;
        }
      }).size(40f).padRight(11).right()
        .update(i -> i.getStyle().imageUp = coll.isCollapsed() ? Icon.downOpen : Icon.upOpen).get();
      Tooltip tip = new Tooltip(t ->
        t.background(Styles.black8).margin(4f)
         .label(() -> "@claj.browser.rooms." + (coll.isCollapsed() ? "show" : "hide")).color(Color.lightGray));
      tip.allowMobile = true;
      button.addListener(tip);
    }).padTop(10).growX().row();
    dest.image().pad(5, 10, 5, 16).height(3).color(Pal.accent).growX().row();
    dest.add(coll).pad(5).padRight(0).padBottom(0).growX().row();
  }

  public void checkPlay(Runnable run) {
    if (!Vars.mods.hasContentErrors()) run.run();
    else Vars.ui.showInfo("@mod.noerrorplay");
  }

  public void filterRooms() {
    int columns = columns();
    for(var e : servers) {
      Seq<ClajRoom<Host>> rooms = serverRooms.get(e.key);
      if (rooms == null) continue;
      setRooms(e.value, rooms, columns);
    }
  }

  void setup() {
    float width = targetWidth();
    int columns = columns();

    hosts.clear();
    //since the buttons are an overlay, make room for that
    hosts.marginBottom(70f);

    cont.clear();
    cont.top();
    cont.table(name -> {
      name.add("@name").padRight(5);
      name.field(Core.settings.getString("name"), text -> {
        Vars.player.name(text);
        Core.settings.put("name", text);
      }).maxTextLength(Vars.maxNameLength).grow().pad(8);

      name.button(Tex.whiteui, Styles.squarei, 36, () -> {
        new PaletteDialog().show(color -> {
          Vars.player.color().set(color);
          Core.settings.put("color-0", color.rgba8888());
        });
      }).size(50f).update(b -> b.getStyle().imageUpColor = Vars.player.color());
    }).width(columns == 1 ? width : MIN_CARD_SIZE * Math.min(1.5f, columns)).height(70f).pad(4).row();

    cont.table(search -> {
      search.add("@search").padRight(5);
      search.field(serverSearch, text -> {
        serverSearch = text.trim().toLowerCase();
        filterRooms();
      }).grow().pad(8);
      search.button(Icon.zoom, Styles.emptyi, this::refreshAll).size(50f);
      Vars.ui.addDescTooltip(search.button(Icon.refresh, Styles.emptyi, this::refreshAll).size(50f)
                                   .get(), "@servers.refresh");
    }).width(columns == 1 ? width : MIN_CARD_SIZE * Math.min(2, columns)).height(50f).pad(4).padBottom(25).row();

    cont.pane(hosts).width((width + 5) * columns + 33).pad(0).get().setScrollingDisabled(true, false);
    cont.row();
  }

  /** {@link mindustry.ui.dialogs.JoinDialog#getVersionString} without the new line. */
  public String getVersionString(Host host) {
    if (host == null) {
      return "";
    } else if (host.version == -1) {
      return Core.bundle.format("server.version", Core.bundle.get("server.custombuild"), "");
    } else if (host.version == 0) {
      return Core.bundle.get("server.outdated");
    } else if (host.version < Version.build && Version.build != -1) {
      return Core.bundle.get("server.outdated") + "   " + Core.bundle.format("server.version", host.version, "");
    } else if (host.version > Version.build && Version.build != -1) {
      return Core.bundle.get("server.outdated.client") + "   " + Core.bundle.format("server.version", host.version, "");
    } else if (host.version == Version.build && Version.type.equals(host.versionType)) {
      return ""; // not important
    } else {
      return Core.bundle.format("server.version", host.version, host.versionType);
    }
  }

  /** 90% of the screen width. */
  public float maxWidth() {
    return  Core.graphics.getWidth() / Scl.scl() * SCREEN_MAX;
  }

  /** adaptative card size. */
  public float targetWidth() {
    return Math.min(maxWidth() / columns(), MAX_CARD_SIZE);
  }

  public int columns() {
    return Mathf.clamp((int)(maxWidth() / MIN_CARD_SIZE), 1, MAX_COLUMNS);
  }

}
