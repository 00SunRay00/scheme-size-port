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

package com.xpdustry.claj.server;

import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.backend.headless.HeadlessApplication;
import arc.mock.MockSettings;
import arc.util.Log;

import com.xpdustry.claj.common.ClajPackets;
import com.xpdustry.claj.common.status.ClajVersion;
import com.xpdustry.claj.server.plugin.Plugins;
import com.xpdustry.claj.server.util.Autosaver;


public class Main implements ApplicationListener {
  public static String[] args;
  public static HeadlessApplication app;
  public static boolean isLoading;

  public static void main(String[] args) {
    isLoading = true;
    Main.args = args;

    ClajVars.initLogger();
    if (!loadEnv(args)) System.exit(1);

    app = new HeadlessApplication(new Main(), t -> {
      //TODO: crash handler
      Throwable disposeError = null, saveError = null;
      // Try to dispose properly
      try { app.dispose(); }
      catch (Exception e) { disposeError = e; }
      try { ClajConfig.save(); }
      catch (Exception e) { saveError = e; }
      Autosaver.forceSave();
      if (isLoading) Log.err("Failed to load server", t);
      else {
        Log.err(t);
        Log.err("Server closed with error(s).");
      }
      if (disposeError != null || saveError != null) {
        Log.err("############### ALERT! ###############");
        Log.err("The CLaJ server has crashed and was unable to dispose properly.");
        Log.err("There is a possible data corruption.");
        if (disposeError != null) {
          Log.err("");
          Log.err("Failed to dispose components", disposeError);
        }
        if (saveError != null) {
          Log.err("");
          Log.err("Failed to save settings", saveError);
        }
        Log.err("############### ALERT! ###############");
      }
      System.exit(1);
    });
  }

  @Override
  public void init() {
    // We do not uses default settings system
    Core.settings = new MockSettings();

    ClajConfig.load();
    Log.level = ClajConfig.debug.get() ? Log.LogLevel.debug : Log.LogLevel.info; // set log level
    ClajPackets.init();
    Autosaver.init(app);

    app.addListener(ClajVars.control = new ClajControl());
    app.addListener(ClajVars.plugins = new Plugins(ClajVars.pluginsDirectory, ClajVars.control));
    app.addListener(ClajVars.relay = new ClajRelay(ClajVars.networkSpeed));

    app.post(() -> {
      isLoading = false;
      Events.fire(new ClajEvents.ServerLoadedEvent());
      Log.info("Server loaded and hosted on port @. Type @ for help.", ClajVars.port, "'help'");
    });
  }

  public static boolean loadEnv(String[] args) {
    // Parse server port
    if (args.length == 0) {
      Log.err("Need a port as an argument!");
      return false;
    }
    int port = Integer.parseInt(args[0]);
    if (port < 0 || port > 0xffff) {
      Log.err("Invalid port range");
      return false;
    }
    ClajVars.port = port;

    // Get the server version from manifest or command line property
    String version = null;
    try {
      version = new java.util.jar.Manifest(Main.class.getResourceAsStream("/META-INF/MANIFEST.MF"))
                                 .getMainAttributes().getValue("Claj-Version");
    } catch (Exception e) {
      Log.err("Unable to locate manifest properties", e);
      return false;
    }
    // Fallback to argument property
    String versionOverride = System.getProperty("Claj-Version");
    if (version == null && versionOverride == null) {
      Log.err("The 'Claj-Version' property is missing in the jar manifest.");
      return false;
    }

    try {
      ClajVars.version = ClajVersion.of(version == null ? versionOverride : version);
    } catch (Exception e) {
      Log.err("Invalid CLaJ version", e);
      return false;
    }

    return true;
  }
}
