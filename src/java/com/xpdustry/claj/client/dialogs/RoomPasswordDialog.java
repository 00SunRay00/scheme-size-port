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

import arc.func.Cons;
import arc.input.KeyCode;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.TextField.TextFieldFilter;
import arc.util.Time;

import mindustry.gen.Icon;
import mindustry.ui.dialogs.BaseDialog;

import com.xpdustry.claj.api.ClajPinger;
import com.xpdustry.claj.common.util.Strings;


public class RoomPasswordDialog extends BaseDialog {
  public static int DIGITS = 4;
  public static int MIN_VALUE = 0;
  public static int MAX_VALUE = 9999;

  final TextField passwordField = new TextField();
  final TextButton connectButton;
  short password = ClajPinger.NO_PASSWORD;
  boolean valid;
  Cons<Short> confirm;

  public RoomPasswordDialog() {
    super("");
    cont.defaults().width(350f);
    buttons.defaults().size(140f, 60f).pad(4f);

    cont.table(table -> {
      table.add("@claj.password.field").padRight(5f).right();
      table.add(passwordField).maxTextLength(DIGITS).valid(this::validate).size(180f, 54f).left().row();
      passwordField.setFilter(TextFieldFilter.digitsOnly);
      passwordField.update(passwordField::requestKeyboard);
    }).padBottom(75f).row();

    cont.table(table -> {
      table.defaults().size(74, 64).pad(5);
      int i = 0;
      for (char c : "123456789 0".toCharArray()) {
        if (i++ % 3 == 0) table.row();
        if (c != ' ') {
          String cs = String.valueOf(c);
          table.button(cs, () -> addLetter(cs)).get().getLabel().setFontScale(1.3f);
        } else table.add();
      }
      table.button(Icon.undo, this::popLetter);
    });

    connectButton = buttons.button("@claj.password.connect", this::confirm).disabled(_ -> !valid).get();
    buttons.button("@cancel", this::hide);

    hidden(() -> {
      Time.run(7f, passwordField::clearText); // delay clear for visual
      password = ClajPinger.NO_PASSWORD;
    });
    addCloseListener();
    keyDown(KeyCode.enter, connectButton::fireClick);
  }

  protected void addLetter(String l) {
    passwordField.appendText(l);
  }

  protected void popLetter() {
    // No easy way to pop a letter
    passwordField.setText(passwordField.getText().substring(0, Math.max(0, passwordField.getText().length()-1)));
    passwordField.setCursorPosition(passwordField.getMaxLength());
  }

  protected void confirm() {
    short p = password;
    hide();
    confirm.get(p);
  }

  protected boolean validate(String password) {
    valid = password.length() == DIGITS && Strings.matches(password, Character::isDigit);
    if (valid) {
      int parsed = Strings.parseInt(password, -1);
      this.password = parsed < MIN_VALUE || parsed > MAX_VALUE ? ClajPinger.NO_PASSWORD : (short)parsed;
    }
    return valid;
  }

  public void show(Cons<Short> confirmed) { show(confirmed, true); }
  /**
   * @param connectMode Defines whether this dialog should be displayed
   *        as a prompt to connect to a room ({@code true}) or to set his password ({@code false}).
   */
  public void show(Cons<Short> confirmed, boolean connectMode) {
    confirm = confirmed;
    title.setText(connectMode ? "@claj.password.required" : "@claj.password.define");
    connectButton.getLabel().setText(connectMode ? "@connect" : "@confirm");
    show();
  }
}
