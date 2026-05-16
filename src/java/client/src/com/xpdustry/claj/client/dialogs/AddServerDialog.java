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

import arc.func.Cons2;
import arc.scene.ui.TextField;
import arc.util.Strings;


public class AddServerDialog extends mindustry.ui.dialogs.BaseDialog {
  final TextField nameField = new TextField(), addressField = new TextField();
  Cons2<String, String> callback;
  String lastError;
  String lastHost;

  public AddServerDialog() {
    super("");
    buttons.defaults().size(140f, 60f).pad(4f);

    cont.table(table -> {
      table.add("@name").padRight(5f).right();
      table.add(nameField).maxTextLength(64).size(320f, 54f).left().row();
      table.add("@joingame.ip").padRight(5f).right();
      table.add(addressField).maxTextLength(64).valid(this::validate).size(320f, 54f).left().row();
      table.add();
      table.label(() -> lastError).width(320f).left();
    }).row();

    buttons.button("@cancel", this::hide);
    buttons.button("@ok", () -> {
      if(callback != null) callback.get(nameField.getText(), addressField.getText());
      hide();
    }).disabled(_ -> lastError != null || nameField.getText().isEmpty() || addressField.getText().isEmpty());

    hidden(() -> {
      lastError = null;
      lastHost = null;
      callback = null;
      nameField.clearText();
      addressField.clearText();
    });
  }

  public void show(Cons2<String, String> done) {
    callback = done;
    title.setText("@server.add");
    show();
  }

  public void show(String currentName, String currentHost, Cons2<String, String> done) {
    callback = done;
    title.setText("@server.edit");
    nameField.setText(currentName);
    addressField.setText(currentHost);
    show();
  }

  boolean validate(String host) {
    if (host.equals(lastHost)) return lastError == null;
    lastHost = host;
    lastError = null;

    if (host.isEmpty()) {
      lastError = "@claj.manage.missing-host";
      return false;
    }

    try {
      int port = 0;
      boolean ipv6 = Strings.count(host, ':') > 1;
      if (ipv6 && host.contains("]:") && !host.endsWith("]:"))
        port = Integer.parseInt(host.substring(host.indexOf("]:") + 2));
      else if (!ipv6 && host.contains(":") && !host.endsWith(":"))
        port = Integer.parseInt(host.substring(host.lastIndexOf(':') + 1));
      else {
        lastError = "@claj.manage.missing-port";
        return false;
      }

      if (port < 0 || port > 0xFFFF) {
        lastError = "@claj.manage.invalid-port";
        return false;
      }

      return true;
    } catch (Exception e) {
      lastError = "@claj.manage.invalid-port";
      return false;
    }
  }
}
