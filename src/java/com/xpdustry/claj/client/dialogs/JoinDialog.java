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

import arc.Core;
import arc.input.KeyCode;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;

import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.api.ClajPinger;
import com.xpdustry.claj.client.ClajUi;
import com.xpdustry.claj.common.status.RejectReason;


public class JoinDialog extends BaseDialog {
  boolean valid;
  String output;
  final TextField linkField = new TextField("claj://");
  ClajLink lastLink;
  short lastPassword = ClajPinger.NO_PASSWORD;

  public JoinDialog() {
    super("@claj.join.name");

    addCloseListener();
    keyDown(KeyCode.enter, () -> {
      if (!valid || linkField.getText().isEmpty() || Vars.net.active()) return;
      joinRoom();
    });
    shown(linkField::requestKeyboard);

    cont.defaults().width(Vars.mobile ? 450f : 550f);
    cont.labelWrap("@claj.join.note").padBottom(10f).left().row();
    cont.table(table -> {
      table.add("@claj.join.link").padRight(10f).left();
      table.add(linkField).maxTextLength(64).valid(this::setLink).height(50f).growX().get();
      Vars.ui.addDescTooltip(table.button(Icon.paste, Styles.emptyi, this::importLink).size(50f).padLeft(5)
                                  .get(), "@schematic.copy.import");
      table.row().add();
      table.labelWrap(() -> output).left().growX().padTop(5).row();
    }).row();

    buttons.defaults().size(140f, 60f).pad(4f);
    buttons.button("@cancel", this::hide);
    buttons.button("@ok", this::joinRoom)
           .disabled(_ -> !valid || linkField.getText().isEmpty() || Vars.net.active());

    //Add the 'Join via CLaJ' button in the join dialog
    addButton();
  }

  /** @deprecated i keep that until the next release. */
  @Deprecated
  void addButton() {
    if (Vars.mobile) {
      // adds in a new line for mobile players
      Vars.ui.join.buttons.row().add().growX().width(-1);
      Vars.ui.join.buttons.button("@claj.browser.name", Icon.play, this::show);
      // correct margin
      Runnable addMargin = () -> Reflect.<Table>get(Vars.ui.join, "hosts").marginBottom(140f);
      Vars.ui.join.shown(addMargin);
      Vars.ui.join.resized(addMargin);
    } else {
      Vars.ui.join.buttons.button("@claj.browser.name", Icon.play, this::show);
      Vars.ui.join.buttons.getCells().insert(Vars.steam ? 3 : 4, Vars.ui.join.buttons.getCells().pop());
    }
  }

  public void importLink() {
    String text = Core.app.getClipboardText();
    if (text == null || (text = text.trim()).isEmpty()) return;
    linkField.setText(text);
    Vars.ui.showInfoFade("@claj.join.import");
  }

  public void joinRoom() {
    if (Vars.player.name.trim().isEmpty()) {
      Vars.ui.showInfo("@noname");
      return;
    }

    try { joinRoom(ClajLink.fromString(linkField.getText())); }
    catch (Exception e) {
      valid = false;
      Vars.ui.showErrorMessage(Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage());
    }
  }

  public void joinRoom(ClajLink link, boolean isProtected) {
    if (isProtected) ClajUi.password.show(pass -> joinRoom(link, pass));
    else joinRoom(link);
  }

  public void joinRoom(ClajLink link) { joinRoom(link, ClajPinger.NO_PASSWORD); }
  public void joinRoom(ClajLink link, short password) {
    boolean[] ignore = {false};
    Vars.ui.loadfrag.show("@connecting");
    Vars.ui.loadfrag.setButton(() -> {
      Vars.ui.loadfrag.hide();
      ignore[0] = true;
      Vars.netClient.disconnectQuietly();
    });

    Time.runTask(2f, () -> {
      if (ignore[0]) return;
      Claj.get().joinRoom(lastLink = link, lastPassword = password, () -> {
        // No need to run pingers anymore, it's just waste...
        Timer.schedule(() -> {
          if (Vars.net.client()) Claj.get().stopPingers();
        }, 60);

        Vars.ui.join.hide();
        ClajUi.browser.hide();
        hide();
      }, r -> joinError(link, r),
      e -> {
        if (ignore[0]) return;
        String msg = e.getMessage();
        if (msg != null && msg.contains("timed out")) joinError(link, RejectReason.roomNotFound);
        else Vars.net.handleException(e);
      });
    });
  }

  public void joinError(ClajLink link, RejectReason reason) {
    Vars.ui.loadfrag.hide();
    switch (reason) {
      case passwordRequired:
      case invalidPassword:
        ClajUi.password.show(pass -> joinRoom(link, pass));
        if (reason == RejectReason.passwordRequired) return;
        break;
      case serverClosing:
        hide();
        //$FALL-THROUGH$
      default:
    }
    Vars.ui.showErrorMessage("@claj.reject." + Strings.camelToKebab(reason.name()));
  }

  public void rejoinRoom() {
    if (lastLink == null) return;
    Vars.ui.loadfrag.show("@reconnecting");
    Timer.Task[] ping = {null}; // i love java lambdas...
    ping[0] = Timer.schedule(() -> {
      Claj.get().pingHost(lastLink.host, lastLink.port, _ -> {
        if(!ping[0].isScheduled()) return;
        ping[0].cancel();
        joinRoom(lastLink, lastPassword);
      }, _ -> {});
    }, 1, 1);

    Vars.ui.loadfrag.setButton(() -> {
        Vars.ui.loadfrag.hide();
        if(!ping[0].isScheduled()) return;
        ping[0].cancel();
    });
  }

  public void resetLastLink() {
    lastLink = null;
  }

  public boolean setLink(String link) {
    try {
      ClajLink.fromString(linkField.getText());
      output = "@claj.join.valid";
      return valid = true;
    } catch (Exception e) {
      output = Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage();
      return valid = false;
    }
  }
}
