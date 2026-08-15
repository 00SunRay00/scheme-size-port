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

package com.xpdustry.claj.client.dialogs;

import java.util.Comparator;

import arc.Core;
import arc.Events;
import arc.func.*;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;

import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.client.*;
import com.xpdustry.claj.common.status.CloseReason;


public class CreateRoomDialog extends BaseDialog {
  /** {@link Styles#togglet} but {@link TextButtonStyle#checked} is {@link Tex#buttonOver}
   *  instead of {@link Tex#buttonDown}. */
  public static final TextButton.TextButtonStyle fixedToglet = new TextButton.TextButtonStyle(Styles.togglet);
  static { fixedToglet.checked = fixedToglet.over; }

  ClajLink link;
  Server selected;
  final Table custom = new Table(), online = new Table();
  final Section customSection = new Section("custom", custom);
  final Section onlineSection = new Section("online", online);
  boolean refreshingOnline;


  public CreateRoomDialog() {
    super("@claj.manage.name");
    Events.run(EventType.HostEvent.class, this::closeRoom);

    cont.defaults().width(Vars.mobile ? 480f : 800f);

    makeButtonOverlay();
    addCloseButton();
    buttons.button("@claj.manage.create", Icon.add, this::createRoom)
           .disabled(_ -> Claj.get().proxies.isRoomCreated() || selected == null);
    if (Vars.mobile) buttons.row();
    buttons.button("@claj.manage.delete", Icon.cancel, this::closeRoom)
           .disabled(_ -> Claj.get().proxies.isRoomClosed());
    buttons.button("@copylink", Icon.copy, this::copyLink).disabled(_ -> link == null);

    keyDown(KeyCode.f5, this::refreshAll);
    shown(() -> Time.run(7f, this::refreshAll)); // Gives time to this dialog to display

    cont.top();
    Cell<?> p = cont.pane(inner -> {
      inner.table(hosts -> {
        // Description
        hosts.table(table -> {
          table.labelWrap("@claj.manage.tip").left().growX();
          Vars.ui.addDescTooltip(
            table.button(Icon.settings, () -> ClajUi.settings.show()).right().padLeft(10).growY().get(),
            "@claj.settings.title"
          );
        }).padBottom(24).growX().row();

        // Custom servers
        section("@claj.manage.custom-servers", customSection, hosts, () -> ClajUi.add.show((n, h) -> {
          ClajServers.custom.put(n, h);
          ClajServers.saveCustom();
          pingServer(customSection.add(n, h), customSection);
        }), this::refreshCustom);

        // Public servers
        section("@claj.manage.public-servers", onlineSection, hosts, null, this::refreshOnline);
      }).padRight(5).grow();

      // Give extra space for buttons
      inner.marginBottom(Vars.mobile ? 140f : 70f);
    }).with(s -> {
      s.setForceScroll(false, true);
      s.setScrollingDisabled(true, false);
    });

    if (Vars.mobile) onResize(() -> {
      p.width(Core.graphics.isPortrait() ? 480f : 800f);
      customSection.layout();
      onlineSection.layout();
    });

    // Add the 'Manage CLaJ room' button in pause menu
    addButton();
  }

  void addButton() {
    Vars.ui.paused.shown(() -> {
      Table root = Vars.ui.paused.cont;
      root.row();
      @SuppressWarnings("rawtypes")
      Seq<Cell> buttons = root.getCells();

      if (Vars.mobile) {
        root.buttonRow("@claj.manage.name", Icon.planet, this::show).disabled(_ -> !Vars.net.server());
        if(root.getCells().size % root.getColumns() == 0) root.row();
        return;

      // Makes it compatible with foo's client by checking the hosting button.
      // 'colspan' is normally at 2 on vanilla.
      // Also there is no way to get this property, so we need reflection.
      } else if (Reflect.<Integer>get(buttons.get(buttons.size-2), "colspan") == 2)
        root.button("@claj.manage.name", Icon.planet, this::show).colspan(2).width(450f)
            .disabled(_ -> !Vars.net.server()).row();

      // Probably the foo's client, use a normal button
      else
        root.button("@claj.manage.name", Icon.planet, this::show).disabled(_ -> !Vars.net.server()).row();

      // move the claj button above the quit button
      buttons.swap(buttons.size-1, buttons.size-2);
    });
  }

  public Server getSelected() {
    return selected;
  }

  public void refreshAll() {
    Claj.get().cancelPingers(); // cancel previous pings
    refreshCustom(false);
    refreshOnline(false);
  }

  public void refreshCustom() { refreshCustom(true); }
  public void refreshCustom(boolean cancelPrevious) {
    if (cancelPrevious) Claj.get().cancelPingers();
    selected = null;
    ClajServers.loadCustom();
    customSection.set(ClajServers.custom,
      s -> addServer(s, customSection),
      s -> ClajUi.add.show(s.name, s.get(), (n, a) -> {
        int index = ClajServers.custom.indexOfKey(s.name);
        ClajServers.custom.setKey(index, n);
        ClajServers.custom.setValue(index, a);
        ClajServers.saveCustom();
        s.set(n, a);
        customSection.layout();
        s.ping = -1;
        pingServer(s, customSection);
      }),
      s -> Vars.ui.showConfirm("@confirm", "@server.delete", () -> {
        ClajServers.custom.removeKey(s.name);
        ClajServers.saveCustom();
        if (selected == s) selected = null;
        customSection.remove(s);
      })
    );
  }

  public void refreshOnline() { refreshOnline(true); }
  public void refreshOnline(boolean cancelPrevious) {
    if (refreshingOnline) return; // Avoid to re-trigger a refresh while refreshing
    if (cancelPrevious) Claj.get().cancelPingers();
    refreshingOnline = true;
    selected = null;

    online.clear();
    online.button(b ->
      b.table(t -> {
        t.add("@claj.servers.fetching").padRight(3);
        t.label(() -> Strings.animated(Time.time, 4, 11, ".")).color(Pal.accent);
      }).center(), () -> {}
    ).growX().padTop(5).padBottom(5);

    ClajServers.refreshOnline(() -> {
      refreshingOnline = false;
      if (ClajServers.online.isEmpty()) {
        online.clear();
        online.button("@claj.servers.empty", () -> {}).growX().padTop(5).padBottom(5).row();
      } else onlineSection.set(ClajServers.online, s -> addServer(s, onlineSection), null, null);
    }, e -> {
      refreshingOnline = false;
      online.clear();
      online.button("@claj.servers.check-internet", () -> {}).growX().padTop(5).padBottom(5).row();
      Vars.ui.showException("@claj.servers.fetch-failed", e);
    });
  }

  public void section(String label, Section sec, Table dest, Runnable add, Runnable refresh) {
    Collapser coll = new Collapser(sec.table, Core.settings.getBool("claj-tcollapsed-" + sec.name, false));
    dest.table(head -> {
      head.add(label, Pal.accent).pad(5).growX().left().bottom();

      if (add != null)
        Vars.ui.addDescTooltip(head.button(Icon.add, Styles.emptyi, add).size(40f).padRight(3).right()
                                   .get(), "@server.add");
      if (refresh != null)
        Vars.ui.addDescTooltip(head.button(Icon.refresh, Styles.emptyi, refresh).size(40f).padRight(3).right()
                                   .get(),"@servers.refresh");

      tooltip(
        head.button(Icon.downOpen, Styles.emptyi, () -> {
          coll.toggle();
          Core.settings.put("claj-tcollapsed-" + sec.name, coll.isCollapsed());
        }).size(40f).padRight(3).right()
          .update(i -> i.getStyle().imageUp = coll.isCollapsed() ? Icon.downOpen : Icon.upOpen).get(),
        () -> "@claj.browser.rooms." + (coll.isCollapsed() ? "show" : "hide")
      );

      tooltip(
        head.button(Icon.filter, Styles.emptyi, sec::nextSort).size(40f).padRight(5).right().get(),
        () -> "@claj.manage.sort." + sec.order.next().name().toLowerCase()
      );
    }).growX().row();
    dest.image().pad(5).height(3).color(Pal.accent).growX().row();
    dest.add(coll).padBottom(10).growX().row();
  }

  void tooltip(Element elem, Prov<CharSequence> text) {
    Vars.ui.addDescTooltip(elem, text.get().toString());
    Tooltip tip = (Tooltip)elem.getListeners().find(Tooltip.class::isInstance);
    if (tip == null) return;
    Label label = (Label)tip.container.find(Label.class::isInstance);
    if (label == null) return;
    label.setText(text);
  }

  public void addServer(Server server, Section section) {
    Table table = section.table;
    Cons<Server> edit = section.edit, delete = section.delete;
    table.button(b -> {
      b.stack(new Table(label -> {
        label.center();
        // Cut in two line for mobiles or if the name is too long
        if ((Vars.mobile && Core.graphics.isPortrait()) || (server.name + " (" + server.last + ')').length() > 54) {
          label.add(server.name).pad(5, 5, 0, 5).expandX().row();
          label.add("[lightgray](" +server.last + ')').pad(5, 0, 5, 5).expandX();
        } else label.add(server.name + " [lightgray](" + server.last + ')').pad(5).expandX();

      }), new Table(inner -> {
        inner.setColor(Pal.gray);
        inner.table(ping -> pingServer(server, ping, section)).margin(0).padLeft(5).padRight(5).left().fillX();
        inner.add().expandX();

        if (edit != null) {
          Cell<ImageButton> button =
            inner.button(Vars.mobile ? Icon.pencil : Icon.pencilSmall, Styles.emptyi, () -> edit.get(server)).right();
          Vars.ui.addDescTooltip(button.get(), "@server.edit");
          if (!Vars.mobile) button.pad(5f).padBottom(0);
          else button.size(30f).pad(2).padBottom(7);
          button.get().getImage().scaleBy(Vars.mobile ? -0.1f : 0.2f);
        }

        if (delete != null) {
          Cell<ImageButton> button =
            inner.button(Vars.mobile ? Icon.trash : Icon.trashSmall, Styles.emptyi, () -> delete.get(server)).right();
          Vars.ui.addDescTooltip(button.get(), "@server.del");
          if (!Vars.mobile) button.pad(5f).padBottom(0);
          else button.size(30f).pad(2, 5, 7, 2);
          button.get().getImage().scaleBy(Vars.mobile ? -0.1f : 0.2f);
        }

      })).growX().row();
    }, fixedToglet, () -> selected = server).update(b -> b.setChecked(selected == server))
                                            .growX().padTop(5).padBottom(5).row();
  }

  public void pingServer(Server server, Section section) {
    pingServer(server, server.table, section);
  }
  public void pingServer(Server server, Table dest, Section section) {
    pingServer(server, dest, section, null, null);
  }
  /** Ignore if already pinged or a ping is already in flight. */
  public void pingServer(Server server, Table dest, Section section, Runnable done, Runnable failed) {
    server.table = dest;

    if (server.ping == Integer.MIN_VALUE) return;
    if (server.ping >= 0) {
      displayPing(server, dest);
      return;
    }

    dest.clear();
    dest.label(() -> Strings.animated(Time.time, 4, 11, ".")).pad(2).color(Pal.accent).left();

    server.ping = Integer.MIN_VALUE;
    Claj.get().pingHost(server.address, server.port, s -> {
      server.compatible = s.version == Claj.get().provider.getVersion().majorVersion;
      server.outdated = s.version < Claj.get().provider.getVersion().majorVersion;
      server.ping = s.ping;
      if (section.order == SortType.PING) section.layout();
      else displayPing(server, server.table);
      if (done != null) done.run();
    }, _ -> {
      server.ping = Integer.MAX_VALUE; // to be sorted at bottom
      if (section.order == SortType.PING) section.layout();
      else displayPing(server, server.table);
      if (failed != null) failed.run();
    });
  }

  void displayPing(Server server, Table dest) {
    dest.clear();

    if (server.ping == Integer.MAX_VALUE) {
      dest.image(Icon.cancel, Color.red).left();
      return;
    }
    if (server.compatible) dest.image(Icon.ok, Color.green).padRight(7).left();
    else dest.image(Icon.warning, Color.yellow).padBottom(3).left().get().scaleBy(-0.22f);
    if (Vars.mobile && Core.graphics.isPortrait()) dest.row();
    dest.add(server.ping + "ms", Color.lightGray, 0.91f).left();
  }

  public void createRoom() {
    if (selected == null) return;
    link = null;

    // Pre-check
    if (!selected.compatible) {
      showError(selected.outdated ? CloseReason.outdatedServer : CloseReason.outdatedClient);
      return;
    }

    Vars.ui.loadfrag.show("@claj.manage.creating-room");
    // Disconnect the client if the room is not created after 10 seconds
    Timer.Task t = Timer.schedule(this::closeRoom, 10);

    ClajUi.settings.setSettings();
    Claj.get().createRoom(selected.address, selected.port, l -> {
      Vars.ui.loadfrag.hide();
      t.cancel();
      link = l;
    }, c -> {
      Vars.ui.loadfrag.hide();
      t.cancel();
      switch (c) {
        case null:
          break;
        case error, closed:
          if (link == null) {
            Vars.ui.showErrorMessage("@claj.manage.room-creation-failed");
            break;
          }
          //$FALL-THROUGH$
        default:
          showError(c);
      }
      link = null;
    }, e -> {
      Vars.net.handleException(e);
      t.cancel();
    });
  }

  public void showError(CloseReason reason) {
    if (reason == null) return;
    String key = "claj.room." + Strings.camelToKebab(reason.name());
    switch (reason) {
      case afk:
        // Show also in chat in case of
        Claj.get().provider.showTextMessage(Claj.get().proxies.get(), Core.bundle.get(key));
        //$FALL-THROUGH$
      case closed, serverClosed:
        Vars.ui.showText("", '@'+key);
        break;
      default:
        Vars.ui.showErrorMessage('@'+key);
    }
  }

  public void closeRoom() {
    Claj.get().proxies.closeRoom();
    //link = null;
  }

  public void copyLink() {
    if (link == null) return;
    Core.app.setClipboardText(link.toString());
    Vars.ui.showInfoFade("@copied");
  }


  public static class Server {
    public Table table;
    public String address, name, error, last;
    public int port, ping = -1;
    public boolean wasValid, compatible = true, outdated;

    public void set(String name, String host) {
      this.name = name;
      set(host);
    }

    public boolean set(String host) {
      if (host.equals(last)) return wasValid;
      address = error = null;
      port = 0;
      last = host;

      if (host.isEmpty()){
        error = "@claj.manage.missing-host";
        return wasValid = false;
      }
      try{
        boolean isIpv6 = Strings.count(host, ':') > 1;
        if(isIpv6 && host.lastIndexOf("]:") != -1 && host.lastIndexOf("]:") != host.length() - 1){
          int idx = host.indexOf("]:");
          address = host.substring(1, idx);
          port = Integer.parseInt(host.substring(idx + 2));
          if (port < 0 || port > 0xFFFF) throw new Exception();
        }else if(!isIpv6 && host.lastIndexOf(':') != -1 && host.lastIndexOf(':') != host.length() - 1){
          int idx = host.lastIndexOf(':');
          address = host.substring(0, idx);
          port = Integer.parseInt(host.substring(idx + 1));
          if (port < 0 || port > 0xFFFF) throw new Exception();
        }else{
          error = "@claj.manage.missing-port";
          return wasValid = false;
        }
        return wasValid = true;
      }catch(Exception e){
        error = "@claj.manage.invalid-port";
        return wasValid = false;
      }
    }

    public String get(){
      if(!wasValid){
        return "";
      }else if(Strings.count(address, ':') > 1){
        return "[" + address + "]:" + port;
      }else{
        return address +  ":" + port;
      }
    }
  }

  public enum SortType {
    DEFAULT(_ -> 0),
    NAME(s -> s.name.toLowerCase()),
    PING(s -> s.ping);

    /** Sort by ping by default */
    public static SortType FALLBACK_SORT = PING;//DEFAULT;
    public static SortType[] all = values();

    public final Comparator<Server> sorter;
    <U extends Comparable<? super U>>SortType(Func<Server, ? extends U> keyExtractor) {
      sorter = Structs.comparing(keyExtractor);
    }

    /** @return the parsed type or {@link #DEFAULT}. */
    public static SortType get(String name) {
      name = name.toUpperCase();
      for (SortType type : all) {
        if (type.name().equals(name)) return type;
      }
      return DEFAULT;
    }

    public static SortType fromSettings(String name) {
      if (!Core.settings.has("claj-tsort-" + name)) return FALLBACK_SORT;
      return SortType.get(Core.settings.getString("claj-tsort-" + name));
    }

    public void save(String name) {
      Core.settings.put("claj-tsort-" + name, name());
    }

    /** Circular */
    public SortType next() {
      return all[(ordinal() + 1) % all.length];
    }
  }

  public static class Section {
    public final String name;
    public final Table table;
    public final Seq<Server> servers = new Seq<>();
    public SortType order;
    Cons<Server> add = _ -> {}, edit = add, delete = add;

    public Section(String name, Table table) {
      this.name = name;
      this.table = table;
      this.order = SortType.fromSettings(name);
    }

    public void set(ArrayMap<String, String> map, Cons<Server> add, Cons<Server> edit, Cons<Server> delete) {
      this.add = add;
      this.edit = edit;
      this.delete = delete;

      servers.clear();
      for (ObjectMap.Entry<String, String> e : map) {
        Server s = new Server();
        s.name = e.key;
        s.set(e.value);
        servers.add(s);
      }
      layout();
    }

    public Server add(String name, String host) {
      Server server = new Server();
      server.name = name;
      server.set(host);
      add(server);
      return server;
    }

    public void add(Server server) {
      servers.add(server);
      layout();
    }

    public void remove(Server server) {
      servers.remove(server, true);
      layout();
    }

    public void nextSort() {
      setSort(order.next());
    }

    public void setSort(SortType order) {
      this.order = order;
      order.save(name);
      layout();
    }

    public void layout() {
      table.clear();
      //optimize?
      Seq<Server> ordered = order == SortType.DEFAULT ? servers : servers.copy().sort(order.sorter);
      for (Server s : ordered) add.get(s);
    }
  }
}
