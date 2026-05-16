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

package com.xpdustry.claj.common.status;

import com.xpdustry.claj.common.util.Strings;


/**
 * CLaJ versions are always in format: {@code protocolVersion.majorVersion.minorVersion}. <br>
 * Only {@code majorVersion} is important. <br>
 * {@code protocolVersion} is discarded, as it's always {@code 2} (for this project).
 * Different CLaJ types must not be compatible with each others. <br>
 * As well as {@code minorVersion} (optional), because it's represents changes 
 * that doesn't affect the protocol itself.
 */
public class ClajVersion implements Comparable<ClajVersion> {
  public final int protocolVersion, majorVersion, minorVersion;

  protected ClajVersion(int protocolVersion, int majorVersion, int minorVersion) {
    this.protocolVersion = protocolVersion;
    this.majorVersion = majorVersion;
    this.minorVersion = minorVersion;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + majorVersion;
    result = 31 * result + minorVersion;
    result = 31 * result + protocolVersion;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || obj instanceof ClajVersion other
        && protocolVersion == other.protocolVersion
        && majorVersion == other.majorVersion
        && minorVersion == other.minorVersion;
  }

  @Override
  public String toString() {
    return protocolVersion + "." + majorVersion + "." + minorVersion;
  }
  
  // Comparison methods
  
  @Override
  public int compareTo(ClajVersion other) { 
    return compare(this, other); 
  }
  
  public int compareTo(String other) { 
    return Strings.compareVersion(toString(), other); 
  }
  
  public boolean isAtLeast(ClajVersion version) {
    return isVersionAtLeast(this, version);
  }
    
  public boolean isAtLeast(String version) {
    return Strings.isVersionAtLeast(toString(), version);
  }
    
  public static int compare(ClajVersion current, ClajVersion other) {
    if (current.protocolVersion != other.protocolVersion)
      return current.protocolVersion < other.protocolVersion ? -1 : 1;
    if (current.majorVersion != other.majorVersion)
      return current.majorVersion < other.majorVersion ? -1 : 1;
    if (current.minorVersion != other.minorVersion)
      return current.minorVersion < other.minorVersion ? -1 : 1;
    return 0;
  }
    
  public static boolean isVersionAtLeast(ClajVersion current, ClajVersion other) {
    return other.protocolVersion > current.protocolVersion
        || other.majorVersion > current.majorVersion
        || other.minorVersion > current.minorVersion;
  }
  
  // Parsing methods
  
  public static ClajVersion of(String version) throws IllegalArgumentException {
    int i = -1;

    int protocolVersion = parse(version, ++i, i = version.indexOf('.', i));
    if (protocolVersion < 0) throw makeFormatError("protocolVersion", protocolVersion);
    int majorVersion = parse(version, ++i, (i = version.indexOf('.', i)) == -1 ? version.length() : i);
    if (majorVersion < 0) throw makeFormatError("majorVersion", majorVersion);
    int minorVersion = i == -1 ? 0 : parse(version, ++i, (i = version.indexOf('.', i)) == -1 ? version.length() : i);
    if (minorVersion < 0) throw makeFormatError("minorVersion", minorVersion);
    if (i != -1) throw new IllegalArgumentException("Too many version parts");
    
    return new ClajVersion(protocolVersion, majorVersion, minorVersion);
  }
  
  public static ClajVersion of(int protocolVersion, int majorVersion, int minorVersion) 
  throws IllegalArgumentException {
    if (protocolVersion < 0) throw makeFormatError("protocolVersion", -3);
    if (majorVersion < 0) throw makeFormatError("majorVersion", -3);
    if (minorVersion < 0) throw makeFormatError("minorVersion", -3);
    
    return new ClajVersion(protocolVersion, majorVersion, minorVersion);
  }

  protected static IllegalArgumentException makeFormatError(String var, int value) {
    return new IllegalArgumentException((value == -1 ? "Missing " : "Invalid ") + var + (value == -3 ? " range" : ""));
  }

  protected static int parse(String str, int start, int stop) {
    if (stop < 0 || start > stop) return -1;
    int parsed = Strings.parseInt(str, 10, Integer.MIN_VALUE, start, stop);
    return parsed == Integer.MIN_VALUE ? -2 : parsed < 0 ? -3 : parsed;
  }
}
