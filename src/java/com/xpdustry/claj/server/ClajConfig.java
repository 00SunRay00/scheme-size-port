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

import arc.Files;
import arc.files.Fi;
import arc.func.Cons;
import arc.func.Prov;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Structs;
import arc.util.serialization.*;

import com.xpdustry.claj.common.status.ClajType;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.util.Autosaver;
import com.xpdustry.claj.server.util.JsonSettings;


public class ClajConfig {
  public static final Seq<Field<?>> all = new Seq<>();
  public static String fileName = "config.json";
  protected static JsonSettings settings;

  @SuppressWarnings("rawtypes")
  public static void init() {
    settings = new JsonSettings(new Fi(fileName, Files.FileType.local), true, true, true, false);

    settings.addSerializer(ClajType.class, new Json.Serializer<ClajType>() {
      public void write(Json json, ClajType object, Class knownType) { json.writeValue(object.type());}
      public ClajType read(Json json, JsonValue jsonData, Class type) { return ClajType.of(jsonData.asString()); }
    });
  }

  public static void load() {
    try {
      if (settings == null) init();
      settings.load();
      all.each(Field::load);
    } catch (Exception e) {
      String fileName = settings == null ? null : settings.file().path();
      throw new RuntimeException("Failed to load configuration of file '" + fileName + "'", e);
    }
  }

  public static void reload() {
    try {
      if (settings == null) return;
      settings.load();
      all.each(Field::reload);
    } catch (Exception e) {
      String fileName = settings == null ? null : settings.file().path();
      throw new RuntimeException("Failed to reload configuration of file '" + fileName + "'", e);
    }
  }

  /** Force save all fields. Do nothing if config was not initialized. */
  public static void save() {
    try {
      if (settings == null) return;
      all.each(Field::save);
      settings.save();
    } catch (Exception e) {
      String fileName = settings == null ? null : settings.file().path();
      throw new RuntimeException("Failed to sve configuration in file '" + fileName + "'", e);
    }
  }

  /** Case is ignored. */
  @SuppressWarnings("unchecked")
  public static <T> Field<T> get(String key) {
    return (Field<T>)all.find(f -> f.key.equalsIgnoreCase(key));
  }


  @SuppressWarnings("unchecked")
  public static class Field<T> implements Autosaver.Saveable {
    public final String key, desc;
    protected final T defaultValue;
    public final Class<T> type;
    protected Cons<T> changed;
    protected T value;
    protected boolean loaded, modified;

    public Field(String key, String desc, T def) { this(key, desc, def, null, true); }
    public Field(String key, String desc, T def, Cons<T> changed) { this(key, desc, def, changed, true); }
    public Field(String key, String desc, T def, boolean register) { this(key, desc, def, null, register); }
    public Field(String key, String desc, T def, Cons<T> changed, boolean register) {
      if (key == null || (key = key.trim()).isEmpty()) throw new IllegalArgumentException("'key' is null");
      if (ClajConfig.get(key) != null) throw new IllegalArgumentException("Field '" + key + "' already exists");
      this.key = key;
      this.desc = desc == null ? "" : desc;
      this.defaultValue = def;
      this.type = def == null ? null : (Class<T>)def.getClass();
      this.changed = changed == null ? _ -> {} : changed;

      if (register) all.add(this);
      Autosaver.add(this, Autosaver.SavePriority.normal);
    }

    /** @return "field '" + {@link key} + "'". */
    public String name() {
      return "field '" + key + "'";
    }

    public boolean modified() {
      return modified;
    }

    protected T checkValue(T value) {
      if (value == null || value.getClass() != type) {
        String valueType = value == null ? null : value.getClass().getName();
        throw new IllegalArgumentException("Invalid value type for " + name() + "; " + valueType + "!=" + type.getName());
      }
      return value;
    }

    protected T loadValue() {
      return settings.getOrPut(key, type, getDefault());
    }

    public void reload() {
      loaded = false;
      load();
    }

    public void load() {
      if (loaded) return;
      if (settings == null || !settings.loaded())
        throw new IllegalStateException("Settings are not initialized");
      value = checkValue(loadValue());
      loaded = true;
      modified = false;
    }

    protected void saveValue() {
      settings.put(key, value);
    }

    public void save() {
      if (!loaded) return;
      if (settings == null || !settings.loaded())
        throw new IllegalStateException("Settings are not initialized");
      saveValue();
      modified = false;
    }

    public T get() {
      load();
      return value;
    }

    public void set(T value) {
      this.value = checkValue(value);
      notifyChange();
    }

    protected void notifyChange() {
      modified = true;
      changed.get(value);
    }

    public T getDefault() {
      return defaultValue;
    }

    public void setDefault() {
      set(getDefault());
    }

    public boolean isDefault() {
      return Structs.eq(get(), getDefault());
    }

    /** Adds changed callback. */
    public void changed(Cons<T> changed) {
      Cons<T> old = this.changed;
      this.changed = v -> {
        old.get(v);
        changed.get(v);
      };
    }

    @Override
    public String toString() {
      return String.valueOf(get());
    }

    protected T decodeValue(String value) {
      return settings.getJson().fromJson(type, value);
    }

    /**
     * Tries to decode the value from a {@link String} by parsing and decoding as Json.
     * @throws IllegalArgumentException if decoding failed, or decoded type is not the same.
     */
    public T decode(String value) throws IllegalArgumentException {
      // Fast path
      if (type == null) return (T)(Boolean)value.equals("null");
      if (type == String.class) return (T)value;
      if (type == Boolean.class) {
        if (Strings.isTrue(value)) return (T)Boolean.TRUE;
        if (Strings.isFalse(value)) return (T)Boolean.FALSE;
        throw new IllegalArgumentException("Invalid boolean value");
      }
      // Slow path using json decoding
      try {
        T decoded = decodeValue(value);
        if (decoded == null) throw new IllegalArgumentException("Decoded value cannot be null");
        if (decoded.getClass() == type) return decoded;
        throw new IllegalArgumentException("Decoded value must be a " + type.getSimpleName());
      } catch (SerializationException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  public static class SetField<T> extends Field<ObjectSet<T>> {
    public final Class<T> elementType;
    protected final Prov<ObjectSet<T>> defaultMaker;

    public SetField(String key, String desc, Class<T> elementType, Prov<ObjectSet<T>> def) {
      this(key, desc, elementType, def, null, true);
    }
    public SetField(String key, String desc, Class<T> elementType, Prov<ObjectSet<T>> def, Cons<ObjectSet<T>> changed) {
      this(key, desc, elementType, def, changed, true);
    }
    public SetField(String key, String desc, Class<T> elementType, Prov<ObjectSet<T>> def, boolean register) {
      this(key, desc, elementType, def, null, register);
    }
    public SetField(String key, String desc, Class<T> elementType, Prov<ObjectSet<T>> def, Cons<ObjectSet<T>> changed,
                    boolean register) {
      super(key, desc, new ObjectSet<>(0), changed, register);
      this.elementType = elementType;
      defaultMaker = def;
    }
    protected ObjectSet<T> loadValue() { return settings.getOrPut(key, type, elementType, defaultMaker); }
    protected void saveValue() { settings.put(key, elementType, value.toSeq()); }
    public ObjectSet<T> getDefault() { return defaultMaker.get(); }
    protected ObjectSet<T> decodeValue(String value) { return settings.getJson().fromJson(type, elementType, value); }

    public boolean add(T value) {
      boolean added = get().add(value);
      notifyChange();
      return added;
    }
    public boolean remove(T value) {
      boolean removed = get().remove(value);
      notifyChange();
      return removed;
    }
    public boolean contains(T value) { return get().contains(value); }
    public void clear() {
      get().clear();
      notifyChange();
    }
  }


  private static Seq<String> fieldDescs = Seq.with(
      "Toggle debug log level",
      "Maximum number of connections (not clients) allowed on this server. Set to &lb0&lw to disable.",
      "Maximum number of rooms that can be created on this server. Set to &lb0&lw to disable.",
      """
      Maximum number of rooms allowed per ip address. Also used as a rate-limit per minute.
      Set to &lb0&lw to disable.
      """.trim(),
      "Maximum number of clients allowed per room. Set to &lb0&lw to disable.",
      """
      Limit for packet count sent within 3 sec that will lead to a disconnect.
      Ignored for room hosts. Set to &lb0&lw to disable.
      """.trim(),
      """
      Limit for packet count sent within 3 sec, and per client, that will lead to a room closure.
      In theory x30-50 bigger than the normal limit, because a room host can send a lot of packets.
      Set to &lb0&lw to disable.
      """.trim(),
      """
      Limit of room join requests per minute per ip address.
      The server will act as the room was not found, which can prevent from room searching.
      Set to &lb0&lw to disable.
      """.trim(),
      """
      Limit of room info requests per minute per ip address.
      The server will act as the room is not found. Set to &lb0&lw to disable.
      """.trim(),
      """
      Limit of room list requests per minute per ip address.
      The server will return an empty list. Set to &lb0&lw to disable.
      """.trim(),
      """
      Limit of new states that can be received from room host per minute.
      The server will ignore them. Set to &lb0&lw to disable.
      """.trim(),
      """
      Limit of clients that can wait for the new state of a room.
      A low value can limit DDoS attacks, but may annoy users. Set to &lb0&lw to disable.
      """.trim(),
      """
      Limit of clients that can wait for the new room list of a type.
      A low value can limit DDoS attacks, but may annoy users. Set to &lb0&lw to disable.
      """.trim(),
      """
      Whether to accept or not clients who attempt to join a room without specifying their CLaJ implementation.
      Setting this to false will break compatibility with older CLaJ versions.
      """.trim(),
      """
      Warn a client that trying to create a room, that it's CLaJ version is deprecated.
      This do not warn ones who trying to join a room.
      """.trim(),
      "Warn all rooms when the server is closing.",
      "The time to wait before exiting the server when closing it. (in seconds)",
      "The time after which a new room status will be requested, if necessary. (in seconds)",
      "The time to wait for the room's new status. (in seconds)",
      "The time after which a new status will be requested to all rooms, if necessary. (in seconds)",
      """
      Listing public rooms can be very long when requesting states,
      this defines the time before the list is send as is, even some state was not received.
      """.trim(),
      """
      The time after which the IP address rater will be removed. (in seconds)
      Because join, info, and list limitations uses IP addresses, to prevent peoples from resetting their
      rate-limits just by reconnecting, it's necessary to clean them after a time not seen the IP address,
      to avoid a potential OOM. Set to &lb0&lw to disable.
      """.trim(),
      """
      The time after which the room will be closed for AFK. (in minutes)
      Here, AFK means that no CLaJ clients has joined the room for a long time.
      Even if, in reality, there are connected clients, but with another way than CLaJ.
      Set to &lb0&lw to disable.
      """.trim()
  ).reverse();

  public static Field<Boolean> debug = new Field<>("debug", fieldDescs.pop(), false, v ->
                                                   Log.level = v ? Log.LogLevel.debug : Log.LogLevel.info);
  public static Field<Integer> maxConnections = new Field<>("max-connections", fieldDescs.pop(), 1<<23);
  public static Field<Integer> maxRooms = new Field<>("max-rooms", fieldDescs.pop(), 1<<16);
  public static Field<Integer> roomLimit = new Field<>("room-limit", fieldDescs.pop(), 16);
  public static Field<Short> clientLimit = new Field<>("client-limit", fieldDescs.pop(), (short)(1<<8));
  public static Field<Integer> spamLimit = new Field<>("spam-limit", fieldDescs.pop(), 1<<8);
  public static Field<Integer> hostSpamLimit = new Field<>("host-spam-limit", fieldDescs.pop(), 1<<13);
  public static Field<Integer> joinLimit = new Field<>("join-limit", fieldDescs.pop(), 32);
  public static Field<Integer> infoLimit = new Field<>("info-limit", fieldDescs.pop(), 32);
  public static Field<Integer> listLimit = new Field<>("list-limit", fieldDescs.pop(), 32);
  public static Field<Integer> stateLimit = new Field<>("state-limit", fieldDescs.pop(), 24);
  public static Field<Integer> infoRequestLimit = new Field<>("info-request-limit", fieldDescs.pop(), 1<<7);
  public static Field<Integer> listRequestLimit = new Field<>("list-request-limit", fieldDescs.pop(), 1<<10);
  public static Field<Boolean> acceptNoType = new Field<>("accept-no-type", fieldDescs.pop(), true);
  public static Field<Boolean> warnDeprecated = new Field<>("warn-deprecated", fieldDescs.pop(), true);
  public static Field<Boolean> warnClosing = new Field<>("warn-closing", fieldDescs.pop(), true);
  public static Field<Integer> closeWait = new Field<>("close-wait", fieldDescs.pop(), 10);
  public static Field<Integer> stateLifetime = new Field<>("state-lifetime", fieldDescs.pop(), 60);
  public static Field<Integer> stateTimeout = new Field<>("state-timeout", fieldDescs.pop(), 20);
  public static Field<Integer> listLifetime = new Field<>("list-lifetime", fieldDescs.pop(), 60);
  public static Field<Integer> listTimeout = new Field<>("list-timeout", fieldDescs.pop(), 30);
  public static Field<Integer> raterLifetime = new Field<>("rater-tifetime", fieldDescs.pop(), 5 * 60);
  public static Field<Integer> afkTime = new Field<>("afk-time", fieldDescs.pop(), 2 * 60);

  // Other fields having their own command
  public static SetField<String> blacklist = new SetField<>("blacklist", "", String.class, ObjectSet::new);
  public static SetField<ClajType> typeBlacklist = new SetField<>("type-blacklist", "", ClajType.class, ObjectSet::new);

  static { fieldDescs.shrink(); } // remove cached fields desc
}
