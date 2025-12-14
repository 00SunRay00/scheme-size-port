package scheme.claj.client.dialogs;

import scheme.claj.client.Claj;
import scheme.claj.client.ClajLink;
import scheme.claj.client.ClajServers;

import arc.graphics.Color;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;

import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;


public class CreateClajRoomDialog extends BaseDialog {
  ClajLink link;
  Server selected, renaming;
  int renamingOldKey;
  Button bselected;
  Dialog add;
  Table custom, online;
  boolean customShown = true, onlineShown = true, refreshingOnline;

  public CreateClajRoomDialog() {
    super("@claj.manage.name");
    arc.Events.run(mindustry.game.EventType.HostEvent.class, this::closeRoom);
    
    cont.defaults().width(Vars.mobile ? 480f : 850f);
    
    makeButtonOverlay();
    addCloseButton();
    buttons.button("@claj.manage.create", Icon.add, this::createRoom).disabled(b -> !Claj.isRoomClosed() || selected == null);
    if (Vars.mobile) buttons.row();
    buttons.button("@claj.manage.delete", Icon.cancel, this::closeRoom).disabled(b -> Claj.isRoomClosed());
    buttons.button("@claj.manage.copy", Icon.copy, this::copyLink).disabled(b -> link == null);
    
    shown(() -> {
      // Just to give time to this dialog to open
      arc.util.Time.run(7f, () -> {
        refreshCustom();
        refreshOnline();  
      });
    });

    // Add custom server dialog
    Server tmp = new Server();
    String[] last = {"", ""};
    TextField[] field = {null, null};
    add = new BaseDialog("@joingame.title");
    add.buttons.defaults().size(140f, 60f).pad(4f);
    add.cont.table(table -> {
      table.add("@claj.manage.server-name").padRight(5f).right();
      field[0] = table.field(last[0], text -> last[0] = text).size(320f, 54f).maxTextLength(100).left().get();
      table.row().add("@joingame.ip").padRight(5f).right();
      field[1] = table.field(last[1], tmp::set).size(320f, 54f).valid(t -> tmp.set(last[1] = t))
                      .maxTextLength(100).left().get();
      table.row().add();
      table.label(() -> tmp.error).width(320f).left().row();
    }).row();
    add.buttons.button("@cancel", () -> {
      if (renaming != null) {
        renaming = null;
        last[0] = last[1] = "";
      }
      add.hide();
    });
    add.buttons.button("@ok", () -> {
      if (renaming != null) {
        ClajServers.custom.removeIndex(renamingOldKey);
        ClajServers.custom.insert(renamingOldKey, last[0], last[1]);
        renaming = null;
        renamingOldKey = -1;
      } else ClajServers.custom.put(last[0], last[1]);
      ClajServers.saveCustom();
      refreshCustom();
      add.hide();
      last[0] = last[1] = "";
    }).disabled(b -> !tmp.wasValid || last[0].isEmpty() || last[1].isEmpty());
    add.shown(() -> {
      add.title.setText(renaming != null ? "@server.edit" : "@server.add");
      if(renaming != null) {
        field[0].setText(renaming.name);
        field[1].setText(renaming.get());
        last[0] = renaming.name;
        last[1] = renaming.get();
      } else {
        field[0].clearText();
        field[1].clearText();
      }
    });

    cont.pane(hosts -> {
      //CLaJ description
      hosts.labelWrap("@claj.manage.tip").left().pad(0, 5, 24, 5).growX().row();
      
      // Custom servers
      hosts.table(table -> {
        table.add("@claj.manage.custom-servers").pad(10).padLeft(0).color(Pal.accent).growX().left();
        table.button(Icon.add, Styles.emptyi, add::show).size(40f).right().padRight(3);
        table.button(Icon.refresh, Styles.emptyi, this::refreshCustom).size(40f).right().padRight(3);
        table.button(Icon.downOpen, Styles.emptyi, () -> customShown = !customShown)
             .update(i -> i.getStyle().imageUp = !customShown ? Icon.upOpen : Icon.downOpen)
             .size(40f).right();
      }).pad(0, 5, 0, 5).growX().row();
      hosts.image().pad(5).height(3).color(Pal.accent).growX().row();
      hosts.collapser(table -> custom = table, false, () -> customShown).pad(0, 5, 10, 5).growX();
      hosts.row();
      
      // Online Public servers
      hosts.table(table -> {
        table.add("@claj.manage.public-servers").pad(10).padLeft(0).color(Pal.accent).growX().left();
        table.button(Icon.refresh, Styles.emptyi, this::refreshOnline).size(40f).right().padRight(3);
        table.button(Icon.downOpen, Styles.emptyi, () -> onlineShown = !onlineShown)
             .update(i -> i.getStyle().imageUp = !onlineShown ? Icon.upOpen : Icon.downOpen)
             .size(40f).right();
      }).pad(0, 5, 0, 5).growX().row();
      hosts.image().pad(5).height(3).color(Pal.accent).growX().row();
      hosts.collapser(table -> online = table, false, () -> onlineShown).pad(0, 5, 10, 5).growX();
      hosts.row();
      
      hosts.marginBottom(Vars.mobile ? 140f : 70f);
    }).get().setScrollingDisabled(true, false);

    // Adds the 'Create a CLaJ room' button
    Vars.ui.paused.shown(() -> {
      Table root = Vars.ui.paused.cont;
      @SuppressWarnings("rawtypes")
      arc.struct.Seq<arc.scene.ui.layout.Cell> buttons = root.getCells();

      if (Vars.mobile) {
        root.row().buttonRow("@claj.manage.name", Icon.planet, this::show)
                  .disabled(button -> !Vars.net.server()).row();
        return;
        
      // Makes a compatilibity for foo's client users, by
      // checking the hosting button, it's colspan is normally 2 on vanilla.
      // Also there is no way to get this property, so we need reflection.
      } else if (arc.util.Reflect.<Integer>get(buttons.get(buttons.size-2), "colspan") == 2) 
        root.row().button("@claj.manage.name", Icon.planet, this::show).colspan(2).width(450f)
                  .disabled(button -> !Vars.net.server()).row();   
      
      // Probably the foo's client, use a normal button
      else 
        root.row().button("@claj.manage.name", Icon.planet, this::show)
                  .disabled(button -> !Vars.net.server()).row(); 
      
      // move the claj button above the quit button
      buttons.swap(buttons.size-1, buttons.size-2);     
      
    });
  }
  
  void refreshCustom() {
    ClajServers.loadCustom();
    setupServers(ClajServers.custom, custom, true, () -> {
      ClajServers.saveCustom();
      refreshCustom(); 
    });
  }
  
  void refreshOnline() {
    if (refreshingOnline) return;
    refreshingOnline = true;
    ClajServers.refreshOnline(() -> {
      refreshingOnline = false;
      setupServers(ClajServers.online, online, false, null);
    }, e -> {
      refreshingOnline = false;
      Vars.ui.showException("@claj.manage.fetch-failed", e);
    }); 
  }
  
  void setupServers(arc.struct.ArrayMap<String, String> servers, Table table, boolean editable, Runnable deleted) {
    selected = null;// in case of
    table.clear();
    for (int i=0; i<servers.size; i++) {
      Server server = new Server();
      server.name = servers.getKeyAt(i);
      server.set(servers.getValueAt(i));
      
      Button button = new Button(); 
      button.getStyle().checkedOver = button.getStyle().checked = button.getStyle().over;
      button.setProgrammaticChangeEvents(true);
      button.clicked(() -> {
        selected = server;
        bselected = button;
      });
      table.add(button).checked(b -> bselected == b).growX().padTop(5).padBottom(5).row();

      Stack stack = new Stack();
      Table inner = new Table();
      inner.setColor(Pal.gray);
   
      button.clearChildren();
      button.add(stack).growX().row();
      
      Table ping = inner.table(t -> {}).margin(0).pad(0).left().fillX().get();
      inner.add().expandX();
      Table label = new Table().center();
      // Cut in two line for mobiles or if the name is too long
      if (Vars.mobile || (servers.getKeyAt(i) + " (" + servers.getValueAt(i) + ')').length() > 54) {
        label.add(servers.getKeyAt(i)).pad(5, 5, 0, 5).expandX().row();
        label.add(" [lightgray](" + servers.getValueAt(i) + ')').pad(5, 0, 5, 5).expandX();
      } else label.add(servers.getKeyAt(i) + " [lightgray](" + servers.getValueAt(i) + ')') .pad(5).expandX();
      
      stack.add(label);
      stack.add(inner);
      
      if (editable) {
        final int i0 = i;
        if (Vars.mobile) {
          inner.button(Icon.pencil, Styles.emptyi, () -> {
            renaming = server;
            renamingOldKey = i0;
            add.show();
          }).size(30f).pad(2, 5, 2, 5).right();
          
          inner.button(Icon.trash, Styles.emptyi, () -> {
            Vars.ui.showConfirm("@confirm", "@server.delete", () -> {
              servers.removeKey(server.name);
              if (deleted != null) deleted.run();
            });
          }).size(30f).pad(2, 5, 2, 5).right();
          
        } else {
          inner.button(Icon.pencilSmall, Styles.emptyi, () -> {
            renaming = server;
            renamingOldKey = i0;
            add.show();
          }).pad(4).right();
          
          inner.button(Icon.trashSmall, Styles.emptyi, () -> {
            Vars.ui.showConfirm("@confirm", "@server.delete", () -> {
              servers.removeKey(server.name);
              if (deleted != null) deleted.run();
            });
          }).pad(2).right();
        }
      }

      ping.label(() -> Strings.animated(Time.time, 4, 11, ".")).pad(2).color(Pal.accent).left();
      Claj.pingHost(server.ip, server.port, ms -> {
        ping.clear();
        ping.image(Icon.ok).color(Color.green).padLeft(5).padRight(5).left();
        if (Vars.mobile) 
             ping.row().add(ms+"ms").color(Color.lightGray).padLeft(5).padRight(5).left();
        else ping.add(ms+"ms").color(Color.lightGray).padRight(5).left();
        
      }, e -> {
        ping.clear();
        ping.image(Icon.cancel).color(Color.red).padLeft(5).padRight(5).left();
      });
    }
  }

  public void createRoom() {
    if (selected == null) return;
    
    Vars.ui.loadfrag.show("@claj.manage.creating-room");
    link = null;
    // Disconnect the client if the room is not created until 7 seconds
    Timer.Task t = Timer.schedule(Claj::closeRoom, 7);
    Claj.createRoom(selected.ip, selected.port, l -> {
      Vars.ui.loadfrag.hide();
      t.cancel();
      link = l;
    }, e -> {
      Vars.net.handleException(e);
      t.cancel();
    }, () -> {
      Vars.ui.loadfrag.hide();
      t.cancel();
      if (link == null) Vars.ui.showErrorMessage("@claj.manage.room-creation-failed");
      else link = null;
    });
  }
  
  public void closeRoom() {
    Claj.closeRoom();
    link = null;
  }
  
  public void copyLink() {
    if (link == null) return;

    arc.Core.app.setClipboardText(link.toString());
    Vars.ui.showInfoFade("@copied");
  }
  
  
  static class Server {
    public String ip, name, error, last;
    public int port;
    public boolean wasValid;
    
    public synchronized boolean set(String ip){
      if (ip.equals(last)) return wasValid;
      this.ip = this.error = null;
      this.port = 0;
      last = ip;
      
      if (ip.isEmpty()){
        this.error = "@claj.manage.missing-host";
        return wasValid = false;
      }
      try{
        boolean isIpv6 = Strings.count(ip, ':') > 1;
        if(isIpv6 && ip.lastIndexOf("]:") != -1 && ip.lastIndexOf("]:") != ip.length() - 1){
          int idx = ip.indexOf("]:");
          this.ip = ip.substring(1, idx);
          this.port = Integer.parseInt(ip.substring(idx + 2));
          if (port < 0 || port > 0xFFFF) throw new Exception();
        }else if(!isIpv6 && ip.lastIndexOf(':') != -1 && ip.lastIndexOf(':') != ip.length() - 1){
          int idx = ip.lastIndexOf(':');
          this.ip = ip.substring(0, idx);
          this.port = Integer.parseInt(ip.substring(idx + 1));
          if (port < 0 || port > 0xFFFF) throw new Exception();
        }else{
          this.error = "@claj.manage.missing-port";
          return wasValid = false;
        }
        return wasValid = true;
      }catch(Exception e){
        this.error = "@claj.manage.invalid-port";
        return wasValid = false;
      }
    }

    public String get(){
      if(!wasValid){
        return "";
      }else if(Strings.count(ip, ':') > 1){
        return "[" + ip + "]:" + port;
      }else{
        return ip +  ":" + port;
      }
    }
  }
}
