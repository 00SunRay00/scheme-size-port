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

package com.xpdustry.claj.server.plugin;

import arc.files.Fi;
import arc.util.CommandHandler;

import com.xpdustry.claj.server.ClajVars;
import com.xpdustry.claj.server.util.JsonSettings;


public interface Plugin {
  /** 
   * The plugin is automatically determined using the caller class. <br>
   * Calls are expensive, please cache the result.
   * @return a new logger for this plugin. 
   *          
   */
  static PluginLogger getPluginLogger() {
    return ClajVars.plugins.getLogger();
  }

  /**
   * The plugin is automatically determined using the caller class. <br>
   * Calls are expensive, please cache the result.
   * @return a new logger for this plugin with the specified {@code topicClass}.
   */
  static PluginLogger getPluginLogger(Class<?> topicClass) {
    return ClajVars.plugins.getLogger(topicClass);
  }

  /**
   * The plugin is automatically determined using the caller class. <br>
   * Calls are expensive, please cache the result.
   * @return a new logger for this plugin with the specified {@code topic}.
   */
  static PluginLogger getPluginLogger(String topic) {
    return ClajVars.plugins.getLogger(topic);
  }

  /** @return a new logger for this plugin. */
  default PluginLogger getLogger() {
    return ClajVars.plugins.getLogger(this);
  }

  /** @return a new logger for this plugin with the specified {@code topicClass}. */
  default PluginLogger getLogger(Class<?> topicClass) {
    return ClajVars.plugins.getLogger(this, topicClass);
  }

  /** @return a new logger for this plugin with the specified {@code topic}. */
  default PluginLogger getLogger(String topic) {
    return ClajVars.plugins.getLogger(this, topic);
  }
  
  /** @return the folder where configuration files for this plugin should go. */
  default Fi getConfigFolder() {
    return ClajVars.plugins.getConfigFolder(this);
  }

  /** @return a settings handle for this plugin, of file {@code 'plugins/<plugin-name>/config.json'}. */
  default JsonSettings getConfig() {
    return ClajVars.plugins.getConfig(this);
  }

  /** @return the meta data of this plugin .*/
  default Plugins.PluginMeta getMeta() {
    return ClajVars.plugins.getMeta(this);
  }

  /** Called after all plugins have been created and commands have been registered. */
  default void init() {}

  /** Register any commands. */
  default void registerCommands(CommandHandler handler) {}

  /** Dispose any resources created by the plugin. */
  default void dispose() {}
}
