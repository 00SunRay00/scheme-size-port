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
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.ui.*;
import arc.scene.ui.CheckBox.CheckBoxStyle;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.layout.*;

import mindustry.Vars;
import mindustry.core.UI;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog.StringProcessor;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.client.ClajUi;
import com.xpdustry.claj.client.MindustryClajProxy;
import com.xpdustry.claj.common.util.Strings;


public class RoomSettingsDialog extends BaseDialog {
  public static final CheckBoxStyle fixedCheckBoxStyle = Core.scene.getStyle(CheckBoxStyle.class);
  public static final ImageButtonStyle fixedEmptyi = new ImageButtonStyle(Styles.emptyi);
  static {
    fixedCheckBoxStyle.checkboxOffDisabled = fixedCheckBoxStyle.checkboxOff;
    fixedCheckBoxStyle.checkboxOnDisabled =fixedCheckBoxStyle.checkboxOn;
    fixedEmptyi.imageDisabledColor = Styles.defaulti.imageDisabledColor;
  }

  final CheckBox isPublic;
  final CheckBox isProtected;
  final CheckBox autoHost;
  final Slider playerLimit;
  final Label roomName;
  final Label address;
  final Label roomId;
  final Label password;
  final Label players;
  final Label playersClaj;
  final Label ping;

  short lastPassword = 0;
  boolean passwordHidden;

  public RoomSettingsDialog() {
    super("@claj.settings.title");
    cont.defaults().width(480f);

    addCloseButton();
    buttons.button("@save", Icon.save, () -> {
      save();
      hide();
    });
    shown(this::refresh);

    // Init fields
    isPublic = new CheckBox("@claj.settings.public");
    isProtected = new CheckBox("@claj.settings.protected");
    autoHost = new CheckBox("@claj.settings.auto-host", fixedCheckBoxStyle);
    playerLimit = new Slider(0, 99, 1, false);
    roomName = new Label("My Room");
    address = new Label("---");
    roomId = new Label("---");
    password = new Label("0000");
    players = new Label("0");
    playersClaj = new Label("0");
    ping = new Label(Iconc.chartBar + " ---ms");

    cont.table(settings -> {
      // General Config
      settings.add("@claj.settings.general", Pal.accent).pad(5).growX().left().bottom().row();
      settings.image().height(3).pad(5).color(Pal.accent).growX().row();
      settings.table(general -> {
        general.left();
        Vars.ui.addDescTooltip(general.add(isPublic).padTop(5f).left().get(), "@claj.settings.public.help");
        general.row();
        general.table(inner -> {
          Vars.ui.addDescTooltip(inner.add(isProtected).left().get(), "@claj.settings.protected.help");
          inner.button(Icon.edit, fixedEmptyi, 30, () -> ClajUi.password.show(this::setPassword, false))
               .disabled(_ -> !isProtected.isChecked()).padLeft(20f).marginBottom(5).get().getImage()
               .setScale(0.9f);
        }).left().padTop(5f).row();
        Vars.ui.addDescTooltip(general.add(autoHost).left().disabled(true).padTop(5f).padBottom(5f)
                                      .get(), "@claj.settings.auto-host.help"); // WIP
        general.row();
        newTextSlider(general, playerLimit, "@claj.settings.max-players", v -> {
          v = Mathf.clamp(v, 0, 99);
          return UI.formatAmount(v == 0 ? Long.MAX_VALUE : v);
        }).tooltip("@claj.settings.max-players.help").row();
      }).grow().padBottom(20).row();

      // Room Info
      settings.add("@claj.settings.info", Pal.accent).pad(5).growX().left().bottom().row();
      settings.image().height(3).pad(5).color(Pal.accent).growX().row();
      settings.table(info -> {
        info.left();
        info.add("@name").left().padRight(15).padTop(5);
        info.add(roomName).left().padTop(5).row();
        info.add("@claj.settings.server").left().padRight(15).padTop(5);
        info.add(address).left().padTop(5).row();
        info.add("@claj.settings.room-id").left().padRight(15).padTop(5);
        info.add(roomId).left().padTop(5).row();
        Vars.ui.addDescTooltip(info.add("@claj.password.field").left().padRight(15).padTop(5)
                                   .get(), "@claj.settings.password.help");
        info.table(inner -> {
          inner.add(password).left().width(RoomPasswordDialog.DIGITS * 19);
          inner.button(Icon.eyeSmall, Styles.emptyi, 25, this::togglePassword).left().update(b -> {
            b.getStyle().imageUp = passwordHidden ? Icon.eyeSmall : Icon.eyeOffSmall;
            b.getImage().setScale(passwordHidden ? 1.1f : 1.3f, passwordHidden ? 1.1f : 1.4f);
            b.getImageCell().padTop(passwordHidden ? 7 : 15).padLeft(passwordHidden ? 5 : 0);
          }).size(40, 25).with(b -> {
            b.getImage().setScale(1.1f);
            b.getImageCell().padTop(7).padLeft(5);
          });
        }).left().row();
        info.add("@claj.settings.players").left().padRight(15).padTop(5);
        info.add(players).left().padTop(5).row();
        info.add("@claj.settings.players.claj").left().padRight(15).padTop(5);
        info.add(playersClaj).left().padTop(5).row();
        info.add(ping).left().padTop(15).row();
      }).grow().row();
    });
  }

  void togglePassword() {
    passwordHidden ^= true;                       // '\u2022'
    password.setText(passwordHidden ? Strings.repeat('\u25CF', RoomPasswordDialog.DIGITS) :
                     Strings.rJust(Integer.toString(lastPassword), RoomPasswordDialog.DIGITS, "0"));
  }

  void setPassword(short password) {
    lastPassword = (short)Mathf.clamp(password, RoomPasswordDialog.MIN_VALUE, RoomPasswordDialog.MAX_VALUE);
    if (!passwordHidden) togglePassword();
  }

  Cell<Stack> newTextSlider(Table table, Slider slider, String name, StringProcessor sp) {
    Label value = new Label("", Styles.outlineLabel);
    Table content = new Table();

    content.add(name, Styles.outlineLabel).left().growX().wrap();
    content.add(value).padLeft(10f).right();
    content.margin(3f, 33f, 3f, 33f);
    content.touchable = Touchable.disabled;
    slider.changed(() -> value.setText(sp.get((int)slider.getValue())));

    return table.stack(slider, content).width(Math.min(Core.graphics.getWidth() / 1.2f, 400f)).left();
  }

  /** {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.SliderSetting} */
  Slider newTextSlider(Table table, String name, int min, int max, int step, StringProcessor sp) {
    Slider slider = new Slider(min, max, step, false);
    newTextSlider(table, slider, name, sp);
    return slider;
  }

  public void refresh() {
    isPublic.setChecked(Core.settings.getBool("claj-room-public", false));
    isProtected.setChecked(Core.settings.getBool("claj-room-protected", false));
    autoHost.setChecked(Core.settings.getBool("claj-autohost", false));
    int playerlimit =  Vars.netServer.admins.getPlayerLimit();
    playerLimit.setValue(playerlimit);

    roomName.setText(Vars.player.name);
    ClajProxy proxy = Claj.get().proxies.get();
    if (proxy.roomCreated()) {
      ClajLink link = proxy.link();
      address.setText(link.host + ':' + link.port);
      roomId.setText(link.encodedRoomId);
    } else {
      address.setText("------------:----");
      roomId.setText("------------");
    }
    setPassword((short)Core.settings.getInt("claj-room-password", 0));
    String limit = playerlimit <= 0 ? "" : "/" + playerlimit;
    players.setText(Core.settings.getInt("totalPlayers", Groups.player.size()) + limit);
    playersClaj.setText((proxy instanceof MindustryClajProxy mp ? mp.getMindustryConnectionsSize() : "??") + limit);
    if (proxy.isConnected()) {
      ping.setText(Iconc.chartBar + " " + proxy.getReturnTripTime() + "ms");
    } else ping.setText(Iconc.chartBar + " ---ms");
  }

  public void save() {
    Core.settings.put("claj-room-public", isPublic.isChecked());
    Core.settings.put("claj-room-protected", isProtected.isChecked());
    Core.settings.put("claj-room-password", (int)lastPassword); // =/
    Core.settings.put("claj-autohost", autoHost.isChecked());
    Vars.netServer.admins.setPlayerLimit(
      (int)Mathf.clamp(playerLimit.getValue(), playerLimit.getMinValue(), playerLimit.getMaxValue()));

    // Allow state requests by default
    Claj.get().proxies.get()
        .setDefaultConfiguration(isPublic.isChecked(), isProtected.isChecked(), lastPassword, true);
  }

  public void setSettings() {
    Claj.get().proxies.get().setDefaultConfiguration(
      Core.settings.getBool("claj-room-public", false),
      Core.settings.getBool("claj-room-protected", false),
      (short)Core.settings.getInt("claj-room-password", 0),
      true
    );
  }
}
