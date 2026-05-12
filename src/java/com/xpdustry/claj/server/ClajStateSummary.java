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

import java.lang.management.ManagementFactory;

import com.sun.management.OperatingSystemMXBean;

import arc.Core;
import arc.util.OS;
import arc.util.Time;

import com.xpdustry.claj.common.status.ClajVersion;
import com.xpdustry.claj.server.util.NetworkSpeed;


/** Class that hold a summary of the current server state at his call. */
public class ClajStateSummary {
  public final ClajVersion version;
  public final int majorVersion;
  public final String javaVersion;
  public final long uptime;
  public final int tps;
  public final long usedHeap, allocatedHeap;
  /** in %. {@code -1} if unknown. */
  public final float javaCpuLoad, systemCpuLoad;
  public final int rooms, clients, connections;
  /** This ignores Ethernet/IP/TCP/ArcNet headers. {@code -1} if disabled. */
  public final long uploadSpeed, downloadSpeed, totalUpload, totalDownload;
  
  ClajStateSummary() {
    version =  ClajVars.version;
    majorVersion = ClajVars.version.majorVersion;
    javaVersion = OS.javaVersion;
    uptime = Time.timeSinceMillis(ClajVars.startedAt);
    tps = Core.graphics.getFramesPerSecond();
    usedHeap = Core.app.getJavaHeap();
    allocatedHeap = Runtime.getRuntime().totalMemory();
    javaCpuLoad = CpuUsageGetter.processCpuLoad();
    systemCpuLoad = CpuUsageGetter.cpuLoad();
    rooms = ClajVars.relay.rooms.size;
    clients = ClajVars.relay.clientsInRooms();
    connections = ClajVars.relay.getConnections().length;
    NetworkSpeed net = ClajVars.relay.networkSpeed;
    if (net != null) {
      uploadSpeed = (long)net.uploadSpeed();
      downloadSpeed = (long)net.downloadSpeed();
      totalUpload = net.totalUpload();
      totalDownload = net.totalDownload();      
    } else {
      uploadSpeed = downloadSpeed = totalUpload = totalDownload = -1;
    }
  }
  
  /** In case of required classes are not present. */
  private static class CpuUsageGetter {
    private static Object bean;
    
    public static float processCpuLoad() {
      try {
        if (bean == null) bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return (float)(((OperatingSystemMXBean)bean).getProcessCpuLoad()*100);
      } catch (Throwable e) { return -1f; }
    }
    
    @SuppressWarnings("deprecation")
    public static float cpuLoad() {
      try {
        if (bean == null) bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return (float)(((OperatingSystemMXBean)bean).getSystemCpuLoad()*100);
      } catch (Throwable e) { return -1f; }
    }
  }
  
  
  public static ClajStateSummary now() {
    return new ClajStateSummary();
  }
}
