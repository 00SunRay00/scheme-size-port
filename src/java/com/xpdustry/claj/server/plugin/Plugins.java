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

import java.net.URLClassLoader;
import java.util.Locale;

import arc.ApplicationListener;
import arc.files.Fi;
import arc.files.ZipFi;
import arc.func.Cons;
import arc.func.Cons2;
import arc.func.Prov;
import arc.struct.*;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.OS;
import arc.util.Strings;
import arc.util.Structs;
import arc.util.Time;
import arc.util.serialization.Json;
import arc.util.serialization.Jval;

import com.xpdustry.claj.server.ClajVars;
import com.xpdustry.claj.server.util.JarLoader;
import com.xpdustry.claj.server.util.JsonSettings;


/** Simplified {@link mindustry.mod.Mods} that only handles plugins. */
public class Plugins implements ApplicationListener {
  private static final String[] metaFiles = {"plugin.json", "plugin.hjson"};

  public final Fi directory;
  public final CommandHandler commands;
  public final PluginClassLoader mainLoader = new PluginClassLoader(getClass().getClassLoader());
  public final Json json = new Json();
  /** Ordered plugins cache. Set to null to invalidate. */
  private @Nullable Seq<LoadedPlugin> lastOrderedPlugins = new Seq<>();

  private final Seq<LoadedPlugin> plugins = new Seq<>();
  private final ObjectMap<Class<?>, CacheEntry> pluginCache = new ObjectMap<>();
  private final ObjectMap<Class<?>, PluginMeta> metas = new ObjectMap<>();
  /** Used to find a plugin without specifying it. */
  private final ObjectMap<ClassLoader, Class<?>> loaders = new ObjectMap<>();

  public Plugins(Fi directory, CommandHandler commands) {
    this.directory = directory;
    this.commands = commands;
  }

  /** @return the folder where configuration files for this plugin should go. Call this in init(). */
  public Fi getConfigFolder(Plugin plugin) {
    return getConfigFolder(plugin.getClass());
  }

  /** @return the folder where configuration files for this plugin should go. Call this in init(). */
  public Fi getConfigFolder(Class<? extends Plugin> plugin) {
    return pluginCache.get(plugin, CacheEntry::new).getConfig(() -> {
      Fi result = directory.child(getMeta(plugin).name);
      result.mkdirs();
      return result;      
    });
  }

  /** @return a settings handle of {@code 'plugins/<plugin-name>/config.json'}. Call this in init(). */
  public JsonSettings getConfig(Plugin plugin) {
    return getConfig(plugin.getClass());
  }

  /** @return a settings handle of {@code 'plugins/<plugin-name>/config.json'}. Call this in init(). */
  public JsonSettings getConfig(Class<? extends Plugin> plugin) {
    return pluginCache.get(plugin, CacheEntry::new).getSettings(() -> 
      new JsonSettings(getConfigFolder(plugin).child("config.json")));
  }

  /** @return the plugin meta data. Call this in init(). */
  public PluginMeta getMeta(Plugin plugin) {
    return getMeta(plugin.getClass());
  }

  /** @return the plugin meta. Call this in init(). */
  public PluginMeta getMeta(Class<? extends Plugin> plugin) {
    PluginMeta load = metas.get(plugin);
    if(load == null) throw new IllegalArgumentException("Plugin is not loaded yet (or missing)!");
    return load;
  }

  /** @return a new main logger for the plugin. */
  public PluginLogger getLogger(Plugin plugin) {
    return getLogger(plugin.getClass(), (Void)null);
  }

  /** @return a new main logger for the plugin. */
  public PluginLogger getLogger(Class<? extends Plugin> plugin, Void aVoid) {
    return pluginCache.get(plugin, CacheEntry::new)
                      .getLogger(() -> new PluginLogger(plugin));
  }

  /** @return a new logger for the plugin with the specified {@code topicClass}. */
  public PluginLogger getLogger(Plugin plugin, Class<?> topicClass) {
    return getLogger(plugin.getClass(), topicClass);
  }

  /** @return a new logger for the plugin with the specified {@code topicClass}. */
  public PluginLogger getLogger(Class<? extends Plugin> plugin, Class<?> topicClass) {
    return pluginCache.get(plugin.getClass(), CacheEntry::new)
                      .getLogger(topicClass, () -> new PluginLogger(plugin, topicClass));
  }

  /** @return a new logger for the plugin with the specified {@code topic}. */
  public PluginLogger getLogger(Plugin plugin, String topic) {
    return getLogger(plugin.getClass(), topic);
  }

  /** @return a new logger for the plugin with the specified {@code topic}. */
  public PluginLogger getLogger(Class<? extends Plugin> plugin, String topic) {
    return pluginCache.get(plugin.getClass(), CacheEntry::new)
                      .getLogger(topic, () -> new PluginLogger(plugin, topic));
  }

  /** Same as {@link #getConfigFolder(Class)} but tries to detect the plugin by the class calling this method. */
  public Fi getConfigFolder() {
    Class<Plugin> clazz = detectCallingPlugin();
    return clazz == null ? null : getConfigFolder(clazz);
  }

  /** Same as {@link #getConfig(Class)} but tries to detect the plugin by the class calling this method. */
  public JsonSettings getConfig() {
    Class<Plugin> clazz = detectCallingPlugin();
    return clazz == null ? null : getConfig(clazz);
  }

  /** Same as {@link #getMeta(Class)} but tries to detect the plugin by the class calling this method. */
  public PluginMeta getMeta() {
    Class<Plugin> clazz = detectCallingPlugin();
    return clazz == null ? null : getMeta(clazz);
  }

  /** Same as {@link #getLogger(Plugin)} but tries to detect the plugin by the class calling this method. */
  public PluginLogger getLogger() {
    Class<Plugin> clazz = detectCallingPlugin();
    return clazz == null ? null : getLogger(clazz, (Void)null);
  }

  /** Same as {@link #getLogger(Plugin, Class)} but tries to detect the plugin by the class calling this method. */
  public PluginLogger getLogger(Class<?> topicClass) {
    Class<Plugin> clazz = detectCallingPlugin();
    return clazz == null ? null : getLogger(clazz, topicClass);
  }

   /** Same as {@link #getLogger(Plugin, String)} but tries to detect the plugin by the class calling this method. */
  public PluginLogger getLogger(String topic) {
    Class<Plugin> clazz = detectCallingPlugin();
    return clazz == null ? null : getLogger(clazz, topic);
  }

  /**
   * Inspect the current thread stack to find which plugin (based on its ClassLoader) is calling the method.
   * @return the plugin main class or {@code null}.
   */
  @SuppressWarnings("unchecked")
  public <T extends Plugin> Class<T> detectCallingPlugin() {
    //costly, find another way?
    for (StackTraceElement s : Thread.currentThread().getStackTrace()) {
      for (ObjectMap.Entry<ClassLoader, Class<?>> e : loaders) {
        try {
          Class.forName(s.getClassName(), false, e.key);
          return (Class<T>)e.value;
        } catch (Throwable _) {}
      }
    }
    return null;
  }

  /** Returns a list of files per plugin subdirectory. */
  public void listFiles(String directory, Cons2<LoadedPlugin, Fi> cons) {
    eachEnabled(plugin -> {
      Fi file = plugin.root.child(directory);
      if (file.exists()) {
        for (Fi child : file.list())
          cons.get(plugin, child);
      }
    });
  }

  /** @return the loaded plugin found by name, or null if not found. */
  public @Nullable LoadedPlugin getPlugin(String name) {
    return plugins.find(m -> m.name.equals(name));
  }

  /** @return the loaded plugin found by class, or null if not found. */
  public @Nullable LoadedPlugin getPlugin(Class<? extends Plugin> type) {
    return plugins.find(m -> m.main != null && m.main.getClass() == type);
  }

  @Override
  public void init() {
    // Load plugins
    directory.mkdirs(); // ensure folder is created
    load();

    // Register commands
    eachClass(p -> p.registerCommands(commands));

    // Check loaded plugins
    if (!orderedPlugins().isEmpty()) Log.info("@ plugins loaded.", orderedPlugins().size);
    int unsupported = plugins.count(p -> !p.enabled());
    if (unsupported > 0) {
      Log.err("There were errors loading @ plugin" + (unsupported > 1 ? "s:" : ":"), unsupported);
      plugins.each(p -> !p.enabled(), p -> Log.err("- @ &ly(" + p.state + ")", p.meta.name));
    }

    // Finish plugins loading
    eachClass(Plugin::init);
  }

  @Override
  public void dispose() {
    eachClass(Plugin::dispose);
  }

  /** Loads all plugins from the folder, but does not call any methods on them.*/
  public void load() {
    Seq<Fi> candidates = new Seq<>();

    // Add local plugins
    Seq.with(directory.list())
       .retainAll(f -> f.extEquals("jar") || f.extEquals("zip") ||
                      (f.isDirectory() && Structs.contains(metaFiles, meta -> resolveRoot(f).child(meta).exists())))
       .each(candidates::add);

    ObjectMap<String, Fi> mapping = new ObjectMap<>();
    Seq<PluginMeta> metas = new Seq<>();

    for (Fi file : candidates) {
      PluginMeta meta = null;

      try {
        meta = findMeta(resolveRoot(file.isDirectory() ? file : new ZipFi(file)));
      } catch (Throwable _) {}

      if (meta == null || meta.name == null) continue;
      metas.add(meta);
      mapping.put(meta.name, file);
    }

    OrderedMap<String, PluginState> resolved = resolveDependencies(metas);
    for (ObjectMap.Entry<String, PluginState> entry : resolved) {
      Fi file = mapping.get(entry.key);

      Log.debug("Loading plugin @", file);
      try {
        LoadedPlugin plugin = loadPlugin(file, false, entry.value == PluginState.enabled);
        plugin.state = entry.value;
        plugins.add(plugin);
        //invalidate ordered plugins cache
        lastOrderedPlugins = null;

      } catch (Throwable e) {
        Log.err("Failed to load plugin file @. Skipping.", file);
        Log.err(e);
      }
    }

    // Resolve the state
    plugins.each(this::updateDependencies);      
    sortPlugins();
  }

  /** Sort plugins to make sure servers handle them properly and they appear correctly in the dialog */
  private void sortPlugins() {
    plugins.sort(Structs.comps(Structs.comparingInt(m -> m.state.ordinal()), Structs.comparing(m -> m.name)));
  }

  private void updateDependencies(LoadedPlugin plugin) {
    plugin.dependencies.clear();
    plugin.missingDependencies.clear();
    plugin.missingSoftDependencies.clear();
    plugin.dependencies = plugin.meta.dependencies.map(this::locatePlugin);
    plugin.softDependencies = plugin.meta.softDependencies.map(this::locatePlugin);

    for (int i=0; i<plugin.dependencies.size; i++) {
      if(plugin.dependencies.get(i) == null)
        plugin.missingDependencies.add(plugin.meta.dependencies.get(i));
    }
    for (int i=0; i<plugin.softDependencies.size; i++) {
      if (plugin.softDependencies.get(i) == null)
        plugin.missingSoftDependencies.add(plugin.meta.softDependencies.get(i));
    }
  }

  /** @return plugins ordered in the correct way needed for dependencies. */
  public Seq<LoadedPlugin> orderedPlugins() {
    //update cache if it's "dirty"/empty
    if (lastOrderedPlugins == null) {
      //only enabled plugins participate; this state is resolved in load()
      Seq<LoadedPlugin> enabled = plugins.select(LoadedPlugin::enabled);
      ObjectMap<String, LoadedPlugin> mapping = enabled.asMap(m -> m.name);
      lastOrderedPlugins = resolveDependencies(enabled.map(m -> m.meta)).orderedKeys().map(mapping::get);
    }
    return lastOrderedPlugins;
  }

  public LoadedPlugin locatePlugin(String name) {
    return plugins.find(p -> p.enabled() && p.name.equals(name));
  }

  /** @return a list of plugins and versions, in the format {@code name:version}. */
  public Seq<String> getPluginStrings() {
    return plugins.select(LoadedPlugin::enabled).map(p -> p.name + ':' + p.meta.version);
  }

  /**
   * @return The plugins that the client is missing, in the format {@code name:version}. <br>
   *         The inputed array must be in the same format,
   *         and is changed to contain the extra plugins that the client has but the server doesn't.
   */
  public Seq<String> getIncompatibility(Seq<String> out) {
    return getPluginStrings().removeAll(out::remove);
  }

  public Seq<LoadedPlugin> list() {
    return plugins;
  }

  /** Iterates through each plugin with a main class. */
  public void eachClass(Cons<Plugin> cons) {
    orderedPlugins().each(p -> p.main != null, p -> contextRun(p, () -> cons.get(p.main)));
  }

  /** Iterates through each enabled plugin. */
  public void eachEnabled(Cons<LoadedPlugin> cons) {
    orderedPlugins().each(LoadedPlugin::enabled, cons);
  }

  public void contextRun(LoadedPlugin plugin, Runnable run) {
    try { run.run(); }
    catch (Throwable t) { throw new RuntimeException("Error loading plugin " + plugin.meta.name, t); }
  }

  /** Tries to find the config file of a plugin. */
  public @Nullable PluginMeta findMeta(Fi file) {
    Fi metaFile = null;
    for (String name : metaFiles) {
      if ((metaFile = file.child(name)).exists()) break;
    }
    if (metaFile == null || !metaFile.exists()) return null;

    PluginMeta meta = json.fromJson(PluginMeta.class, Jval.read(metaFile.readString()).toString(Jval.Jformat.plain));
    meta.cleanup();
    return meta;
  }

  /** Resolves the loading order of a list plugins using their internal names. */
  public OrderedMap<String, PluginState> resolveDependencies(Seq<PluginMeta> metas) {
    PluginResolutionContext context = new PluginResolutionContext();

    for (PluginMeta meta : metas) {
      Seq<PluginDependency> dependencies = new Seq<>();
      for (String dependency : meta.dependencies)
        dependencies.add(new PluginDependency(dependency, true));
      for (String dependency : meta.softDependencies)
        dependencies.add(new PluginDependency(dependency, false));
      context.dependencies.put(meta.name, dependencies);
    }

    for (String key : context.dependencies.keys()) {
      if (context.ordered.contains(key)) continue;
      resolve(key, context);
      context.visited.clear();
    }

    OrderedMap<String, PluginState> result = new OrderedMap<>();
    for (String name : context.ordered) result.put(name, PluginState.enabled);
    result.putAll(context.invalid);
    return result;
  }

  private boolean resolve(String element, PluginResolutionContext context) {
    context.visited.add(element);

    for (PluginDependency dependency : context.dependencies.get(element)) {
      // Circular dependencies ?
      if (context.visited.contains(dependency.name) && !context.ordered.contains(dependency.name)) {
        context.invalid.put(dependency.name, PluginState.circularDependencies);
        return false;

      // If dependency present, resolve it, or if it's not required, ignore
      } else if (context.dependencies.containsKey(dependency.name)) {
        if (!context.ordered.contains(dependency.name) && !resolve(dependency.name, context) && dependency.required) {
          context.invalid.put(element, PluginState.incompleteDependencies);
          return false;
        }

      // The dependency is missing, but if not required, skip
      } else if (dependency.required) {
        context.invalid.put(element, PluginState.missingDependencies);
        return false;
      }
    }

    if (!context.ordered.contains(element)) context.ordered.add(element);
    return true;
  }

  private Fi resolveRoot(Fi fi){
      if(OS.isMac && (!(fi instanceof ZipFi))) fi.child(".DS_Store").delete();
      Fi[] files = fi.list();
      return files.length == 1 && files[0].isDirectory() ? files[0] : fi;
  }

  /**
   * Loads a plugin file+meta, but does not add it to the list. <br>
   * Note that directories can be loaded as plugins.
   */
  private LoadedPlugin loadPlugin(Fi sourceFile, boolean overwrite, boolean initialize) throws Exception {
    Time.mark();

    Fi zip = sourceFile.isDirectory() ? sourceFile : new ZipFi(sourceFile);
    if (zip.list().length == 1 && zip.list()[0].isDirectory()) zip = zip.list()[0];

    PluginMeta meta = findMeta(zip);
    if (meta == null) {
      Log.warn("Plugin @ doesn't have a 'plugin.[h]json' file, skipping.", zip);
      throw new PluginLoadException("Invalid file: No plugin.json found.");
    }

    String camelized = meta.name.replace(" ", "");
    String mainClass = meta.main == null ? camelized.toLowerCase(Locale.ROOT) + "." + camelized + "Plugin" : meta.main;
    String baseName = meta.name.toLowerCase(Locale.ROOT).replace(" ", "-");

    LoadedPlugin other = plugins.find(m -> m.name.equals(baseName));

    if (other != null) {
      if (overwrite) {
        //close the classloader for jar mods
        if(other.loader instanceof URLClassLoader u) u.close();
        //close zip file
        if (other.root instanceof ZipFi) other.root.delete();
        //delete the old plugin directory
        if (other.file.isDirectory()) other.file.deleteDirectory();
        else other.file.delete();
        //unload
        plugins.remove(other);
      } else throw new PluginLoadException("A plugin named '" + baseName + "' is already imported.");
    }

    ClassLoader loader = null;
    Plugin mainMod;
    Fi mainFile = zip;

    String[] path = (mainClass.replace('.', '/') + ".class").split("/");
    for (String str : path) {
      if (!str.isEmpty()) mainFile = mainFile.child(str);
    }

    //make sure the main class exists before loading it; if it doesn't just don't put it there
    //if the plugin is explicitly marked as java, try loading it anyway
    if (mainFile.exists() && 
        ("0".equals(meta.minClajVersion) || ClajVars.version.isAtLeast(meta.minClajVersion)) && 
        !ClajVars.skipPluginLoading && 
        initialize
    ) {
      loader = JarLoader.load(sourceFile, mainLoader);
      mainLoader.addChild(loader);
      Class<?> main = Class.forName(mainClass, true, loader);

      //detect plugins that incorrectly package CLaJ in the jar
      //this works because JarLoader uses a child-first loading strategy.
      if (Class.forName(Plugin.class.getName(), false, loader) != Plugin.class) {
        throw new PluginLoadException("""
          This plugin has loaded CLaJ dependencies from its own class loader. \
          You are incorrectly including CLaJ dependencies in the plugin JAR! \
          Make sure CLaJ is declared as 'compileOnly' in your 'build.gradle', \
          and the JAR is created with 'runtimeClasspath'.""");
      }

      metas.put(main, meta);
      loaders.put(loader, main);
      mainMod = (Plugin)main.getDeclaredConstructor().newInstance();
      
    } else {
      if (!mainFile.exists()) {
        Log.warn("The entry-point of plugin '@' was not found: @. Please check his 'main' property.", 
                 meta.name, meta.main);
      }
      
      mainMod = null;
    }

    //disallow putting a description after the version
    if (meta.version != null) {
      int line = meta.version.indexOf('\n');
      if(line != -1) meta.version = meta.version.substring(0, line);
    }

    Log.info("Loaded plugin '@' in @ms", meta.name, Time.elapsed());
    return new LoadedPlugin(sourceFile, zip, mainMod, loader, meta);
  }

  /** Represents a plugin's state. May be a jar file, folder or zip. */
  public static class LoadedPlugin {
    /** The location of this plugin's zip file/folder on the disk. */
    public final Fi file;
    /** The root zip file; points to the contents of this plugin. In the case of folders, this is the same as the plugin's file. */
    public final Fi root;
    /** The plugin's main class. */
    public final Plugin main;
    /** Internal plugin name. Used for textures. */
    public final String name;
    /** This plugin's metadata. */
    public final PluginMeta meta;
    /** This plugin dependencies as already-loaded plugins. */
    public Seq<LoadedPlugin> dependencies = new Seq<>();
    /** This plugin soft dependencies as already-loaded plugins. */
    public Seq<LoadedPlugin> softDependencies = new Seq<>();
    /** All missing required dependencies of this plugin as strings. */
    public Seq<String> missingDependencies = new Seq<>();
    /** All missing soft dependencies of this plugin as strings. */
    public Seq<String> missingSoftDependencies = new Seq<>();
    /** Current state of this plugin. */
    public PluginState state = PluginState.enabled;
    /** Class loader for JAR plugins. */
    public ClassLoader loader;

    public LoadedPlugin(Fi file, Fi root, Plugin main, ClassLoader loader, PluginMeta meta) {
      this.root = root;
      this.file = file;
      this.loader = loader;
      this.main = main;
      this.meta = meta;
      name = meta.name;
    }

    public boolean enabled() {
      return state == PluginState.enabled;
    }

    public boolean hasUnmetDependencies() {
      return !missingDependencies.isEmpty();
    }

    public int getMinMajor(){
      return meta.getMinMajor();
    }
        
    @Override
    public String toString() {
      return "LoadedPlugin{" +
             "file=" + file +
             ", root=" + root +
             ", name='" + name + '\'' +
             '}';
    }
  }

  /** Plugin metadata information.*/
  public static class PluginMeta {
    /** Name as defined in plugin.json. Stripped of colors, without spaces in all lower case. */
    public String name;
    /** Minimum CLaJ version that this mod requires, e.g. "2.4" */
    public String minClajVersion = "0";
    public @Nullable String displayName, author, description, version, repo;
    /** Plugin's main class. */
    public String main;
    public Seq<String> dependencies = new Seq<>();
    public Seq<String> softDependencies = new Seq<>();

    /** Removes all colors */
    public void cleanup() {
      if (name != null) name = Strings.stripColors(name).toLowerCase(Locale.ROOT).replace(" ", "-");
      if (displayName != null) displayName = Strings.stripColors(displayName);
      if (displayName == null && name != null) displayName = Strings.capitalize(name);
      if (author != null) author = Strings.stripColors(author);
      if (description != null) description = Strings.stripColors(description);
    }

    public int getMinMajor(){
      String ver = minClajVersion == null ? "0" : minClajVersion;
      int dot = ver.indexOf(".");
      return dot != -1 ? Strings.parseInt(ver.substring(0, dot), 0) : Strings.parseInt(ver, 0);
    }

    @Override
    public String toString() {
      return "ModMeta{" +
             "name='" + name + '\'' +
             ", minClajVersion='" + minClajVersion + '\'' +
             ", displayName='" + displayName + '\'' +
             ", author='" + author + '\'' +
             ", description='" + description + '\'' +
             ", version='" + version + '\'' +
             ", main='" + main + '\'' +
             ", repo='" + repo + '\'' +
             ", dependencies=" + dependencies +
             ", softDependencies=" + softDependencies +
             '}';
    }
  }


  @SuppressWarnings("serial")
  public static class PluginLoadException extends RuntimeException {
    public PluginLoadException(String message) {
      super(message);
    }
  }


  public enum PluginState {
    enabled,
    missingDependencies,
    incompleteDependencies,
    circularDependencies,
    unsupported,
    disabled,
  }


  public static class PluginResolutionContext {
    public final ObjectMap<String, Seq<PluginDependency>> dependencies = new ObjectMap<>();
    public final ObjectSet<String> visited = new ObjectSet<>();
    public final OrderedSet<String> ordered = new OrderedSet<>();
    public final ObjectMap<String, PluginState> invalid = new OrderedMap<>();
  }

  public static final class PluginDependency {
    public final String name;
    public final boolean required;

    public PluginDependency(String name, boolean required) {
      this.name = name;
      this.required = required;
    }
  }
  
  private static class CacheEntry {
    public JsonSettings settings;
    public Fi config;
    public PluginLogger logger;
    public ObjectMap<Class<?>, PluginLogger> classLoggers;
    public ObjectMap<String, PluginLogger> stringLoggers;
    
    public JsonSettings getSettings(Prov<JsonSettings> prov) {
      if (settings == null) settings = prov.get();
      return settings;
    }
    
    public Fi getConfig(Prov<Fi> prov) {
      if (config == null) config = prov.get();
      return config;
    }
    
    public PluginLogger getLogger(Prov<PluginLogger> prov) {
      if (logger == null) logger = prov.get();
      return logger;
    }
        
    public PluginLogger getLogger(Class<?> topic, Prov<PluginLogger> prov) {
      if (classLoggers == null) classLoggers = new ObjectMap<>(8);
      return classLoggers.get(topic, prov);
    }  
    
    public PluginLogger getLogger(String topic, Prov<PluginLogger> prov) {
      if (stringLoggers == null) stringLoggers = new ObjectMap<>(8);
      return stringLoggers.get(topic, prov);
    }
  }
}
