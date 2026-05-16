/**
 * This file is part of MoreCommands. The plugin that adds a bunch of commands to your server.
 * Copyright (c) 2025  ZetaMap
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

package com.xpdustry.claj.client.util;

import arc.func.Cons;
import arc.func.ConsT;
import arc.util.Http;
import arc.util.Http.HttpResponse;
import arc.util.Log;
import arc.util.serialization.Jval;

import mindustry.Vars;
import mindustry.mod.Mod;
import mindustry.mod.Mods;

import com.xpdustry.claj.common.util.Strings;


public class VersionChecker {
  public static String versionKey = "tag_name";
  public static String releaseAssetsKey = "assets";
  public static String downloadUrlKey = "browser_download_url";
  public static String latestVersionName = "latest";
  public static String repoLinkFormat = "https://github.com/@/releases/@";
  public static String repoApiLinkFormat = Vars.ghApi + "/repos/@/releases/@";

  public static <T extends Mod> UpdateState checkFor(T mod) { return checkFor(mod, true); }
  public static <T extends Mod> UpdateState checkFor(T mod, boolean promptStatus) {
    return checkFor(getMeta(mod), promptStatus);
  }

  public static UpdateState checkFor(Mods.ModMeta mod) { return checkFor(mod, true); }
  /**
   * Check for update using the "{@code version}" and "{@code repo}" properties
   * in the mod/plugin definition ({@code <plugin/mod>.[h]json}).
   * <p>
   * The github repo must be formatted like that "{@code <username>/<repo-name>}".<br>
   * The version must be formatted like that "{@code 146.2}" and can starts with "{@code v}",
   * but must not contains letters, like "{@code beta}" or "{@code -dev}".
   *
   * @return the update state of the mod
   */
  public static UpdateState checkFor(Mods.ModMeta mod, boolean promptStatus) {
    return checkFor(mod.repo, mod.version, mod.displayName, promptStatus);
  }

  public static UpdateState checkFor(String repo, String version, String name, boolean promptStatus) {
    UpdateState invalid = checkMeta(repo, version, name, promptStatus);
    if (invalid != null) return invalid;

    UpdateState[] status = {UpdateState.errorNoContent};
    Cons<Throwable> fail = failure(s -> status[0] = s, name, promptStatus);
    Http.get(Strings.format(repoApiLinkFormat, repo)).timeout(5000).error(fail)
        .block(process(s -> status[0] = s, repo, version, name, promptStatus));

    return status[0];
  }

  public static <T extends Mod> void checkAsyncFor(T mod, Cons<UpdateState> finished) {
    checkAsyncFor(mod, true, finished);
  }

  public static <T extends Mod> void checkAsyncFor(T mod, boolean promptStatus, Cons<UpdateState> finished) {
    checkAsyncFor(getMeta(mod), promptStatus, finished);
  }

  public static void checkAsyncFor(Mods.ModMeta mod, Cons<UpdateState> finished) {
    checkAsyncFor(mod, true, finished);
  }

  public static void checkAsyncFor(Mods.ModMeta mod, boolean promptStatus, Cons<UpdateState> finished) {
    checkAsyncFor(mod.repo, mod.version, mod.displayName, promptStatus, finished);
  }

  public static void checkAsyncFor(String repo, String version, String name, boolean promptStatus,
                                   Cons<UpdateState> finished) {
    UpdateState invalid = checkMeta(repo, version, name, promptStatus);
    if (invalid != null) {
      finished.get(invalid);
      return;
    }

    Cons<Throwable> fail = failure(finished, name, promptStatus);
    Http.get(Strings.format(repoApiLinkFormat, repo, latestVersionName)).timeout(5000).error(fail)
        .submit(process(finished, repo, version, name, promptStatus));
  }

  private static <T extends Mod> Mods.ModMeta getMeta(T mod) {
    Mods.LoadedMod load = Vars.mods.getMod(mod.getClass());
    if(load == null) throw new IllegalArgumentException("Mod is not loaded yet (or missing)!");
    return load.meta;
  }

  private static UpdateState checkMeta(String repo, String version, String name, boolean promptStatus) {
    if (promptStatus) Log.info("&fb&ly[" + name + "]&fr Checking for updates...");

    if (repo == null || repo.isEmpty() || repo.indexOf('/') == -1) {
      if (promptStatus) Log.warn("&fb&ly[" + name + "]&fr No repo found for an update.");
      return UpdateState.missingRepo;
    } else if (version == null || version.isEmpty()) {
      if (promptStatus) Log.warn("&fb&ly[" + name + "]&fr No current version found for an update.");
      return UpdateState.missingVersion;
    }
    return null;
  }

  private static Cons<Throwable> failure(Cons<UpdateState> finished, String name, boolean promptStatus) {
    return failure -> {
      String error = "&fb&ly[" + name + "]&fr Unable to check for updates: @";

      if (failure instanceof NullPointerException || failure instanceof UnsupportedOperationException) {
        if (promptStatus) Log.err(error, failure.getLocalizedMessage());
        finished.get(new UpdateState.Error(failure, false, true));
        return;
      }

      String message = failure.getMessage();
      if (message != null) {
        if (message.equals("no content")) {
          if (promptStatus) Log.err(error, "no content received.");
          finished.get(UpdateState.errorNoContent);
          return;
        }
        if (promptStatus) Log.err(error, failure.getLocalizedMessage());
      } else if (promptStatus) Log.err(error, failure.toString());

      finished.get(new UpdateState.Error(failure, false, false));
    };
  }

  private static ConsT<HttpResponse, Exception> process(Cons<UpdateState> finished, String repo, String version,
                                                        String name, boolean promptStatus) {
    return success -> {
      String content = success.getResultAsString().trim();
      if (content.isEmpty()) throw new RuntimeException("no content");

      Jval json = Jval.read(content);
      String foundVersion = json.getString(versionKey, null);
      if (foundVersion == null) throw new NullPointerException("'" + versionKey + "' key is missing");
      Jval assets = json.get(releaseAssetsKey);
      if (assets == null) throw new NullPointerException("'" + releaseAssetsKey + "' key is missing");
      Jval asset = assets.asArray().find(a -> a.getString("name").endsWith(".jar"));
      String url = asset == null ? null : asset.getString(downloadUrlKey);

      if (promptStatus) Log.info("&fb&ly[" + name + "]&fr Found version: @. Current version: @", foundVersion, version);
      if (Strings.isVersionAtLeast(version, foundVersion)) {
        if (promptStatus) Log.info("&fb&ly[" + name + "]&fr Check out this link to upgrade @: @", name,
                                   Strings.format(repoLinkFormat, repo, latestVersionName));
        finished.get(new UpdateState.Outdated(version, foundVersion, url));
      } else {
        if (promptStatus) Log.info("&fb&ly[" + name + "]&fr Already up-to-date, no need to update.");
        finished.get(UpdateState.upToDate);
      }
    };
  }


  public interface UpdateState {
    Missing missingRepo = new Missing(true, false);
    Missing missingVersion = new Missing(false, true);
    Error errorNoContent = new Error(null, true, false);
    Error errorInvalidJson = new Error(null, false, true);
    UpToDate upToDate = new UpToDate();

    /** "version" or/and "repo" properties are missing in the mod/plugin definition. */
    public static record Missing(boolean repo, boolean version) implements UpdateState {}
    /** An error occurred while checking for updates or no content received. */
    public static record Error(Throwable error, boolean noContent, boolean invalidJson) implements UpdateState {}
    /** No new updates found, it's the latest version. */
    public static record UpToDate() implements UpdateState {}
    /**
     * An update was found, the mod/plugin needs to be upgraded.
     * {@code downloadLink} will be the first asset file ending with {@code .jar}, or {@code null} if none is found.
     */
    public static record Outdated(String currentVersion, String foundVersion, String downloadLink)
    implements UpdateState {}
  }
}
