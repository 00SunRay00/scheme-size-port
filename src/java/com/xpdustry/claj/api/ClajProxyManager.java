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

package com.xpdustry.claj.api;

import arc.func.Cons;
import arc.util.Threads;

import com.xpdustry.claj.common.status.CloseReason;


public class ClajProxyManager {
  protected final int workers;
  protected final ClajProvider provider;
  protected int created;
  protected final ClajProxy[] proxies;
  protected final Thread[] threads;
  protected final boolean[] reserved;

  public ClajProxyManager(ClajProvider provider) { this(provider, 1); }
  public ClajProxyManager(ClajProvider provider, int workers) {
    this.workers = workers;
    this.provider = provider;
    proxies = new ClajProxy[workers];
    threads = new Thread[workers];
    reserved = new boolean[workers];
  }

  public String getProxyName(int index) {
    return workers == 1 ? "Claj Proxy" : "Claj Proxy " + (index+1);
  }

  public boolean hasCreatedProxies() {
    return created > 0;
  }

  public int createdProxies() {
    return created;
  }

  public int proxies() {
    return workers;
  }

  public ClajProvider provider() {
    return provider;
  }

  public ClajProxy ensureCreated(int index) {
    ClajProxy proxy = proxies[index];
    if (proxy != null) return proxy;
    proxies[index] = proxy = provider.newProxy();
    created++;
    return proxy;
  }

  public ClajProxy ensureStarted(int index) {
    ClajProxy proxy = get(index);
    if (!proxy.isRunning()) {
      if (threads[index] != null) threads[index].interrupt(); // in case of
      threads[index] = Threads.daemon(getProxyName(index), proxy);
    }
    return proxy;
  }

  /** @return the first proxy. */
  public ClajProxy get() { return get(0); }
  public ClajProxy get(int index) {
    return ensureCreated(index);
  }

  public ClajProxy getOrNull(int index) {
    return proxies[index];
  }

  /** Search for a proxy with no created room. */
  public ClajProxy findFree() {
    int index = findFreeI();
    return index == -1 ? null : get(index);
  }

  public int findFreeI() {
    if (!hasCreatedProxies()) {
      get();
      return 0;
    }
    // First, try to find a free proxy
    for (int i=0; i<proxies.length; i++) {
      if (proxies[i] != null && !isBusy(i)) return i;
    }
    // Else, find an empty slot
    for (int i=0; i<proxies.length; i++) {
      if (proxies[i] == null && !reserved[i]) {
        get(i);
        return i;
      }
    }
    return -1;
  }

  public boolean isBusy(int index) {
    if (reserved[index]) return true;
    ClajProxy proxy = proxies[index];
    return proxy != null && (proxy.roomCreated() || proxy.isConnecting());
  }

  /** Checks the first proxy. */
  public boolean isRoomCreated() { return isRoomCreated(0); }
  public boolean isRoomCreated(int index) {
    ClajProxy proxy = getOrNull(index);
    if (proxy == null) return false; // Just ignore if not created
    return proxy.roomCreated();
  }

  /** Checks the first proxy. */
  public boolean isRoomClosed() { return isRoomClosed(0); }
  public boolean isRoomClosed(int index) {
    ClajProxy proxy = getOrNull(index);
    if (proxy == null) return true; // Just ignore if not created
    return !proxy.roomCreated();
  }

  /** Search whether a proxy have an open room. */
  public boolean hasOpenRoom() {
    for (ClajProxy proxy : proxies) {
      if (proxy == null) continue;
      if (proxy.roomCreated()) return true;
    }
    return false;
  }

  /** Closes the first proxy's room. */
  public void closeRoom() { closeRoom(0); }
  public void closeRoom(int index) {
    ClajProxy proxy = getOrNull(index);
    if (proxy == null) return; // Just ignore if not created
    proxy.closeRoom();
    proxy.close();
  }

  public void closeAllRooms() {
    for (ClajProxy proxy : proxies) {
      if (proxy == null) continue;
      proxy.closeRoom();
      proxy.close();
    }
  }

  /** Dispose all proxies. */
  public void dispose() {
    for (ClajProxy proxy : proxies) {
      if (proxy == null) continue;
      proxy.closeRoom();
      proxy.stop();
      try { proxy.dispose(); }
      catch (Exception e) {}
    }
  }

  /** Close all rooms and stop all proxies. */
  public void stop() {
    for (ClajProxy proxy : proxies) {
      if (proxy == null) continue;
      proxy.closeRoom();
      proxy.stop();
    }
  }

  /**
   * This will try to find an available proxy.
   * @return the proxy on which the room was opened.
   *         (does not guarantee that the room is already open or will be.)
   */
  public ClajProxy createRoom(String host, int port, Cons<ClajLink> created, Cons<CloseReason> closed,
                         Cons<Throwable> failed) {
    int index = findFreeI();
    if (index == -1) {
      if (workers > 1) failed.get(new RuntimeException("No available proxy for room creation"));
      else failed.get(new RuntimeException("Room is already created, please close it before"));
      return null;
    }

    return createRoom(index, host, port, created, closed, failed);
  }

  @SuppressWarnings("resource")
  public ClajProxy createRoom(int index, String host, int port, Cons<ClajLink> created, Cons<CloseReason> closed,
                         Cons<Throwable> failed) {
    if (isBusy(index)) {
      failed.get(new RuntimeException("Room is already created, please close it before"));
      return null;
    }
    ClajProxy proxy = ensureStarted(index);
    reserved[index] = true;

    Runnable task = () -> {
      proxy.connect(host, port, created, reason -> {
        if (closed != null) closed.get(reason);
        reserved[index] = false;
      }, error -> {
        if (failed != null) failed.get(error);
        reserved[index] = false;
      });
    };

    try {
      if (provider.getExecutor() == null) task.run();
      else provider.getExecutor().submit(task);
    } catch (Exception e) {
      reserved[index] = false; // in case of
      throw e;
    }

    return proxy;
  }
}
