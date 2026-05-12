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

package com.xpdustry.claj.server;

import java.net.InetAddress;
import arc.func.Cons;
import arc.func.Cons2;
import arc.struct.*;
import arc.util.*;

import com.xpdustry.claj.common.net.stream.PreparedStream;
import com.xpdustry.claj.common.net.stream.StreamSender;
import com.xpdustry.claj.common.packets.RoomListPacket;
import com.xpdustry.claj.common.status.ClajType;


//TODO: find a way to get rid of timers.
/** Class holding caches and CLaJ routines, such as cleaning AddressRater, closing afk rooms, pending request, etc. */
public class ClajRoutines {
  /** Used to calculate whether a room is afk or not. */
  public final LongMap<Timer.Task> afk = new LongMap<>(16);
  /** List of join, info and list request rates by ip. */
  public final ObjectMap<InetAddress, AddressRater> rates = new ObjectMap<>(32);
  /** List of client who requested the state of a room that was outdated.*/
  public final LongMap<Seq<ClajConnection>> pendingInfoRequests = new LongMap<>(16);
  /** Use cleaner task instead of storing the {@link #pendingInfoRequests} invert, to avoid having to many caches. */
  public final LongMap<Timer.Task> pendingInfoTasks = new LongMap<>(16);
  /** Cache for room list requests. */
  public final ObjectMap<ClajType, CachedRoomList> listCache = new ObjectMap<>(8);

  // region cache cleaning

  public void clearCaches(Cons2<ClajConnection, Long> infoRejection) {
    pendingInfoRequests.forEach(e -> e.value.each(c -> infoRejection.get(c, e.key)));
    pendingInfoRequests.clear();
    pendingInfoTasks.eachValue(Timer.Task::cancel);
    pendingInfoTasks.clear();
    listCache.each((_, c) -> c.send());
    listCache.clear();
    rates.clear();
    afk.eachValue(Timer.Task::cancel);
    afk.clear();
  }

  public void clearRoomCache(ClajRoom room, boolean removeList) {
    cancelRoomInfoTask(room);
    if (room.type == null) return;
    CachedRoomList c = listCache.getNull(room.type);
    if (c == null) return;
    c.remove(room.id);
    if (!removeList) return;
    c.send(); // in case of pending requests
    listCache.remove(room.type);
  }

  public void clearRoomListCache(ClajType type) {
    if (type == null) return;
    CachedRoomList c = listCache.remove(type);
    if (c != null) c.send(); // in case of pending requests
  }

  public void clearClientCache(ClajConnection con) {
    removeAddressRate(con);
    //if (con.room == null) return;
    //Seq<ClajConnection> cons = pendingInfoRequests.get(con.room.id);
    //if (cons != null) cons.remove(con, true);
  }

  // end region
  // region room afk

  public void scheludeRoomAfk(ClajRoom room, Runnable afkClose) {
    if (!room.clients.isEmpty()) return;
    int life = ClajConfig.afkTime.get();
    if (life <= 0) return;
    Timer.Task old = afk.put(room.id, Timer.schedule(() -> {
      afk.remove(room.id);
      afkClose.run();
    }, life * 60));
    if (old != null) old.cancel(); // should not happen, but in case of
  }

  public void cancelRoomAfk(ClajRoom room) {
    Timer.Task task = afk.remove(room.id);
    if (task != null) task.cancel();
  }

  // end region
  // region room info

  public boolean requestRoomState(ClajRoom room, Cons<ClajRoom> sendState) {
    if (!room.requestState()) return false;
    int timeout = ClajConfig.stateTimeout.get();
    Timer.Task old;
    if (timeout > 0) {
      old = pendingInfoTasks.put(room.id, Timer.schedule(() -> {
        cancelRoomInfoTask(room);
        sendState.get(room);
      }, timeout));
      if (old != null) old.cancel(); // In case of

    } else {
      cancelRoomInfoTask(room);
      sendState.get(room);
    }
    return true;
  }

  public void cancelRoomInfoTask(ClajRoom room) {
    Timer.Task task = pendingInfoTasks.remove(room.id);
    if (task != null) task.cancel();
  }

  public Seq<ClajConnection> getPendingRoomRequests(ClajRoom room) {
    Seq<ClajConnection> cons = pendingInfoRequests.get(room.id);
    if (cons == null) pendingInfoRequests.put(room.id, cons = new Seq<>(false, 8));
    return cons;
  }

  public Seq<ClajConnection> getPendingRoomRequestsForSend(ClajRoom room) {
    return pendingInfoRequests.remove(room.id);
  }

  // end region
  // region room list

  //TODO: crappy
  public int requestRoomList(ClajConnection con, ClajType type, LongMap<ClajRoom> fallbackRooms, Cons<Boolean> rejected) {
    if (fallbackRooms == null || type == null) {
      rejected.get(false);
      return 4;
    }
    CachedRoomList cache = getListCache(type, fallbackRooms);
    int limit = ClajConfig.listRequestLimit.get();
    if (limit > 0 && cache.pending.size >= limit) {
      rejected.get(true);
      return 3;
    } else if (cache.updating()) {
      cache.pending.add(con);
      return 2;
    } else if (!cache.isOutdated()) {
      con.sendStream(cache.packet);
      return 1;
    } else {
      cache.pending.add(con);
      cache.refresh(fallbackRooms);
      return 0;
    }
  }

  public void updateRoom(ClajRoom room, boolean stateChanged) {
    CachedRoomList cache = listCache.getNull(room.type);
    if (cache != null) cache.set(room, stateChanged);
  }

  public boolean sendRoomList(ClajType type, boolean force) {
    CachedRoomList cache = listCache.getNull(type);
    if (cache == null || !force && cache.updating()) return false;
    cache.send();
    return true;
  }

  public boolean refreshRoomList(ClajType type, boolean force, LongMap<ClajRoom> rooms) {
    if (rooms == null || type == null) return false;
    CachedRoomList cache = getListCache(type, rooms);
    if (!force && cache.updating()) return false;
    cache.refresh(rooms);
    return true;
  }

  public void refreshRoomList(ClajType type, LongMap<ClajRoom> rooms) {
    getListCache(type, rooms).refresh(rooms);
  }

  protected CachedRoomList getListCache(ClajType type, LongMap<ClajRoom> fallbackRooms) {
    return listCache.get(type, () -> new CachedRoomList(type, fallbackRooms));
  }


  protected static class CachedRoomList {
    public final ClajType type;
    public final RoomListPacket packet;
    public long lastUpdate;
    public final Seq<ClajConnection> pending = new Seq<>(false, 16);
    public final ObjectSet<Long> requesting = new ObjectSet<>();
    public Timer.Task refreshTask;
    private PreparedStream cachedStream;
    private boolean streamDirty = true;

    public CachedRoomList(ClajType type, LongMap<ClajRoom> rooms) {
      this.type = type;
      packet = new RoomListPacket();
      rooms.eachValue(r -> {
        if (!r.shouldRequestState()) return;
        packet.states.put(r.id, r.rawState);
        if (r.isProtected) packet.protectedRooms.add(r.id);
      });
    }

    public void remove(long room) {
      packet.states.remove(room);
      packet.protectedRooms.remove(room);
      requesting.remove(room);
      streamDirty = true;
    }

    public void set(ClajRoom room, boolean stateChanged) {
      if (!room.isPublic) {
        remove(room.id);
        return;
      }
      packet.states.put(room.id, room.rawState);
      if (room.isProtected) packet.protectedRooms.add(room.id);
      else packet.protectedRooms.remove(room.id);
      if (stateChanged) requesting.remove(room.id);
      streamDirty = true;
    }

    public void refresh(LongMap<ClajRoom> rooms) { refresh(rooms, this::send); }
    public void refresh(LongMap<ClajRoom> rooms, Runnable done) {
      lastUpdate = Time.nanos();
      rooms.eachValue(r -> {
        if (r.shouldRequestState() && r.isStateOutdated(lastUpdate) && r.requestState(lastUpdate))
          requesting.add(r.id);
      });

      if (refreshTask != null) refreshTask.cancel(); // In case of
      int timeout = ClajConfig.listTimeout.get();
      if (timeout <= 0 || !updating()) {
        refreshTask = null;
        done.run();
        return;
      }
      refreshTask = Timer.schedule(() -> {
        refreshTask = null;
        done.run();
      }, timeout);
    }

    public void send() {
      if (refreshTask != null) {
        refreshTask.cancel();
        refreshTask = null;
      }
      requesting.clear();

      if (pending.isEmpty()) return;
      Log.debug("Sending room list of type @ to @ pending request" + (pending.size > 1 ? "s..." : "..."),
                type, pending.size);
      if (streamDirty || cachedStream == null) {
        cachedStream = StreamSender.prepare(packet);
        streamDirty = false;
      }
      pending.each(c -> c.sendStream(cachedStream));
      pending.clear();
    }

    public boolean isOutdated() {
      int life = ClajConfig.listLifetime.get() * 1_000_000_000;
      return life > 0 && Time.timeSinceNanos(lastUpdate) >= life;
    }

    public boolean updating() {
      return requesting.size > 0;
    }
  }

  // end region
  // region address rater

  public AddressRater getAddressRate(ClajConnection con) {
    return rates.get(con.address, () -> new AddressRater(con.address)).add(con);
  }

  public void removeAddressRate(ClajConnection con) {
    AddressRater rate = rates.get(con.address);
    if (rate != null) rate.remove(con);
  }


  public class AddressRater {
    public final InetAddress address;
    public final Ratekeeper joinRate = new Ratekeeper();
    public final Ratekeeper infoRate = new Ratekeeper();
    public final Ratekeeper listRate = new Ratekeeper();
    public final Ratekeeper createRate = new Ratekeeper();
    protected final IntSet connections = new IntSet(8);
    protected Timer.Task clean;
    protected int rooms;

    public AddressRater(InetAddress address) {
      this.address = address;
    }

    public AddressRater add(ClajConnection con) {
      connections.add(con.id);
      if (clean != null) clean.cancel();
      return this;
    }

    public void remove(ClajConnection con) {
      connections.remove(con.id);
      if (!connections.isEmpty()) return;

      if (clean != null) clean.cancel();
      int life = ClajConfig.raterLifetime.get();
      if (life <= 0) return;
      clean = Timer.schedule(() -> {
        clean = null;
        rates.remove(address);
      }, life);
    }

    public boolean allowJoin() {
      int limit = ClajConfig.joinLimit.get() * 2; // because joining makes 2 requests
      return limit <= 0 || joinRate.allow(60000L, limit);
    }

    public boolean allowInfo() {
      int limit = ClajConfig.infoLimit.get();
      return limit <= 0 || infoRate.allow(60000L, limit);
    }

    public boolean allowList() {
      int limit = ClajConfig.listLimit.get();
      return limit <= 0 || listRate.allow(60000L, limit);
    }

    public boolean allowCreate() {
      int limit = ClajConfig.roomLimit.get();
      if (limit <= 0) return true;
      if (rooms >= limit || !createRate.allow(60000L, limit)) return false;
      rooms++;
      return true;
    }

    public void removeRoom() {
      if (rooms > 0) rooms--;
    }
  }

  // end region
}
