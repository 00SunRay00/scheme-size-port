/*
 * This file is part of Anti-VPN-Service (AVS). The plugin securing your server against VPNs.
 *
 * MIT License
 *
 * Copyright (c) 2024-2025 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.xpdustry.claj.server.plugin;

import arc.util.Log;
import arc.util.Log.LogLevel;

import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.ClajVars;


/** Log messages to console with topics */
public class PluginLogger {
  protected static final Object[] empty = {};

  public static String pluginTopicFormat = "&lc[@&lc]";
  public static String classTopicFormat = "&ly[@&ly]";

  /** If {@code true}, plugin and class topics will not be displayed. */
  public boolean noTopic = false;
  public final String plugin, topic;
  protected final String computedTag;

  public PluginLogger(boolean noTopic) {
    this.noTopic = noTopic;
    plugin = topic = null;
    computedTag = "";
  }

  public PluginLogger() { this((String)null, (String)null); }

  public PluginLogger(Class<? extends Plugin> plugin) { this(plugin, (String)null); }
  public PluginLogger(Plugin plugin) { this(plugin, (String)null); }
  public PluginLogger(String plugin) { this(plugin, (String)null); }

  public PluginLogger(Class<? extends Plugin> plugin, Class<?> clazz) { this(plugin, Strings.formattedClassName(clazz)); }
  public PluginLogger(Plugin plugin, Class<?> clazz) { this(plugin, Strings.formattedClassName(clazz)); }
  public PluginLogger(String plugin, Class<?> clazz) { this(plugin, Strings.formattedClassName(clazz)); }

  public PluginLogger(Class<? extends Plugin> plugin, String topic) { this(ClajVars.plugins.getMeta(plugin).displayName, topic); }
  public PluginLogger(Plugin plugin, String topic) { this(ClajVars.plugins.getMeta(plugin).displayName, topic); }
  public PluginLogger(String plugin, String topic) {
    this.plugin = plugin;
    this.topic = topic != null && !(topic = topic.trim()).isEmpty() ? topic : null;

    String tag = "";
    if (plugin != null) tag += Strings.format(pluginTopicFormat, plugin) + ' ';
    if (topic != null) tag += Strings.format(classTopicFormat, topic) + ' ';
    computedTag = Log.format(tag + "&fr", empty);
  }

  protected void logImpl(LogLevel level, String text, Throwable th, Object... args) {
    if (Log.level.ordinal() > level.ordinal()) return;

    String tag = noTopic ? "" : computedTag;
    if (text != null) {
      text = Log.format(text, args);
      if (th != null) text += ": " + Strings.getStackTrace(th);
    } else if (th != null) text = Strings.getStackTrace(th);
    if (text == null) text = "";

    //// Avoid log mixing
    //synchronized (Log.logger) {
      int i = 0, nl = text.indexOf('\n');
      while (nl >= 0) {
        Log.logger.log(level, tag + text.substring(i, nl));
        i = nl + 1;
        nl = text.indexOf('\n', i);
      }
      Log.logger.log(level, tag + (i == 0 ? text : text.substring(i)));
    //}
  }

  public void log(LogLevel level, String text, Throwable th, Object... args) { logImpl(level, text, th, args); }
  public void log(LogLevel level, String text, Object... args) { log(level, text, null, args); }
  public void log(LogLevel level, String text) { log(level, text, empty); }

  public void debug(String text, Object... args) { log(LogLevel.debug, text, args); }
  public void debug(Object object) { debug(String.valueOf(object), empty); }

  public void info(String text, Object... args) { log(LogLevel.info, text, args); }
  public void info(Object object) { info(String.valueOf(object), empty); }

  public void warn(String text, Object... args) { log(LogLevel.warn, text, args); }
  public void warn(String text) { warn(text, empty); }

  public void err(String text, Throwable th, Object... args) { log(LogLevel.err, text, th, args); }
  public void err(String text, Object... args) { err(text, null, args); }
  public void err(String text, Throwable th) { err(text, th, empty); }
  public void err(String text) { err(text, null, empty); }
  public void err(Throwable th) { err(null, th, empty); }

  /** Log an empty "info" line. */
  public void ln() { log(LogLevel.info, null, empty); }
}
