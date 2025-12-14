package scheme.claj.client.dialogs;

import scheme.claj.client.Claj;
import scheme.claj.client.ClajLink;

import mindustry.Vars;


public class JoinViaClajDialog extends mindustry.ui.dialogs.BaseDialog {
  String lastLink = "claj://";
  boolean valid;
  String output;

  public JoinViaClajDialog() {
    super("@claj.join.name");

    cont.defaults().width(Vars.mobile ? 350f : 550f);
    
    
    cont.labelWrap("@claj.join.note").padBottom(10f).left().row();
    cont.table(table -> {
      table.add("@claj.join.link").padRight(5f).left();
      table.field(lastLink, this::setLink).maxTextLength(100).valid(this::setLink).height(54f).growX().row();
      table.add();
      table.labelWrap(() -> output).left().growX().row();
    }).row();

    buttons.defaults().size(140f, 60f).pad(4f);
    buttons.button("@cancel", this::hide);
    buttons.button("@ok", this::joinRoom).disabled(button -> !valid || lastLink.isEmpty() || Vars.net.active());
    
    //Adds the 'Join via CLaJ' button
    if (!Vars.steam && !Vars.mobile) {
      Vars.ui.join.buttons.button("@claj.join.name", mindustry.gen.Icon.play, this::show).row();
      Vars.ui.join.buttons.getCells().swap(Vars.ui.join.buttons.getCells().size-1/*6*/, 4);
    } else {
      // adds in a new line for mobile players
      Vars.ui.join.buttons.row().add().growX().width(-1);
      Vars.ui.join.buttons.button("@claj.join.name", mindustry.gen.Icon.play, this::show).row();
    }
  }

  public void joinRoom() {
    if (Vars.player.name.trim().isEmpty()) {
      Vars.ui.showInfo("@noname");
      return;
    }
    
    ClajLink link;
    try { link = ClajLink.fromString(lastLink); } 
    catch (Exception e) {
      valid = false;
      Vars.ui.showErrorMessage(arc.Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage());
      return;
    }

    Vars.ui.loadfrag.show("@connecting");
    Vars.ui.loadfrag.setButton(() -> {
      Vars.ui.loadfrag.hide();
      Vars.netClient.disconnectQuietly();
    });
    
    arc.util.Time.runTask(2f, () -> 
      Claj.joinRoom(link, () -> {
        Vars.ui.join.hide();
        hide();
      })
    );
  }
  
  public boolean setLink(String link) {
    if (lastLink.equals(link)) return valid;

    lastLink = link;
    try { 
      ClajLink.fromString(lastLink); 
      output = "@claj.join.valid";
      return valid = true;
      
    } catch (Exception e) {
      output = arc.Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage();
      return valid = false;
    }
  }
}
