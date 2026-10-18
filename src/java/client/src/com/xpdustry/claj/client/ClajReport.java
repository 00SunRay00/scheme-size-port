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

import java.net.URLEncoder;
import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.struct.Seq;
import arc.util.Align;

import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.common.util.Strings;


public class ClajReport {
  public static final String FORM_URL = "https://claj.xpdustry.com/form";
  protected static final Seq<Throwable> lastErrors = new Seq<>();
  /** Dummy {@link Label} to help with bundle parsing. */
  protected static final Label dummy = new Label("");

  public static void openSuggestionForm() {
    Core.app.openURI(FORM_URL + getParams("Suggestion"));
  }

  public static void openFeedbackForm() {
    Core.app.openURI(FORM_URL + getParams("Feedback"));
  }

  public static void openBugForm() { openBugForm(null); }
  public static void openBugForm(String error) {
    dummy.setText(error);
    error = dummy.getText().toString();
    String message = (!error.isEmpty() ? error + "\n\n\n" : "") + formatLastErrors();
    clearErrors();
    Core.app.openURI(FORM_URL + getParams("Bug Report", Strings.wordTruncate(message, 64, "..."), message));
  }

  public static void addError(Throwable err) {
    lastErrors.add(err);
  }

  public static void clearErrors() {
    lastErrors.clear();
  }

  public static String formatLastErrors() {
    return lastErrors.toString("\n\n", Strings::neatError);
  }

  protected static String getParams(String type) { return getParams(type, null, null); }
  protected static String getParams(String type, String name, String message) {
    String locale = Core.settings.getString("locale", "");
    String version = Claj.get().provider.getVersion().toString();
    return "?"
         + (locale.equals("default") || locale.isEmpty() ? "" : "lang=" + locale + "&")
         + (type != null && !type.isEmpty() ? "type=" + URLEncoder.encode(type, Strings.ascii) + "&" : "")
         + (version != null && !version.isEmpty() ? "version=" + URLEncoder.encode(version, Strings.ascii) + "&" : "")
         + (name != null && !name.isEmpty() ? "name=" + URLEncoder.encode(name, Strings.ascii) + "&" : "")
         + (message != null && !message.isEmpty() ? "message=" + URLEncoder.encode(message, Strings.ascii) + "&" : "");
  }


  /** From {@link mindustry.core.UI} but modified to handle CLaJ error reporting. */
  public static void showErrorMessage(String text) {
    new Dialog("") {{
      setFillParent(true);
      cont.margin(15f);
      cont.add("@error.title").colspan(2);
      cont.row();
      cont.image().width(300f).pad(2).colspan(2).height(4f).color(Color.scarlet);
      cont.row();
      cont.add(text).pad(2f).colspan(2).growX().wrap().get().setAlignment(Align.center);
      cont.row();
      cont.button("@ok", this::hide).size(110, 60).pad(4).right();
      cont.button("@claj.form.report", () -> confirmReport(() -> {
        openBugForm(text);
        hide();
      })).size(180, 60).pad(4).left();
      closeOnBack();
    }}.show();
  }

  public static void reportException(Throwable exc) { reportException(null, exc); }
  public static void reportException(String text, Throwable exc) {
    confirmReport(() -> {
      addError(exc);
      openBugForm(text);
    });
  }

  public static void confirmReport(Runnable confirmed) {
    Vars.ui.loadfrag.hide();
    Vars.ui.showCustomConfirm("@claj.form.report", "@claj.form.confirm", "@yes", "@no",
                              confirmed, ClajReport::clearErrors);
  }

  /**
   * Filter a little bit internet connectivity related issues, to avoid useless submissions.
   * <p>
   * From {@link mindustry.net.Net#showError}.
   */
  public static boolean filterException(Throwable e) {
    String error = Strings.getFinalMessage(e);
    error = error == null ? "" : error.toLowerCase();
    String type = Strings.getFinalCause(e).getClass().toString().toLowerCase();
    return !(
      error.contains("port out of range") || error.contains("invalid argument") ||
      (error.contains("invalid") && error.contains("address")) ||
      Strings.neatError(e).contains("address associated") || error.contains("connection refused") ||
      error.contains("route to host") || type.contains("unknownhost") || type.contains("timeout") ||
      error.equals("alreadyconnected") || error.contains("connection is closed")
    );
  }

  public static void openSuggestionPopup() { openSuggestionPopup(true); }
  public static void openSuggestionPopup(boolean dismissible) {
    BaseDialog dialog = new BaseDialog("@claj.form.suggest.title");
    dialog.setFillParent(false);
    dialog.cont.add("@claj.form.suggest").width(Vars.mobile ? 400f : 600f).wrap().pad(4f).padBottom(20)
               .get().setAlignment(Align.center, Align.center);
    dialog.cont.row();
    CheckBox hide = dismissible ? dialog.cont.check("@dontshowagain", null).get() : null;
    dialog.buttons.defaults().size(110f, 54f).pad(4f);
    dialog.buttons.button("@yes", () -> {
      dialog.hide();
      ClajReport.openSuggestionForm();
      if (dismissible && hide.isChecked()) Core.settings.put("claj-showpopup", false);
    });
    dialog.buttons.button("@no", () -> {
      dialog.hide();
      if (dismissible && hide.isChecked()) Core.settings.put("claj-showpopup", false);
    });
    dialog.closeOnBack();
    dialog.show();
  }

  public static boolean shouldSuggest() {
    return Core.settings.getBool("claj-showpopup", true);
  }
}
