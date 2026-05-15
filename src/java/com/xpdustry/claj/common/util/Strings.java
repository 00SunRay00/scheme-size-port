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

package com.xpdustry.claj.common.util;

import java.io.*;
import java.util.*;

import arc.func.*;
import arc.struct.Seq;
import arc.util.io.*;
import arc.util.serialization.*;
import arc.util.serialization.JsonWriter.OutputType;


public class Strings extends arc.util.Strings {
  public static String rJust(String str, int length) { return rJust(str, length, " "); }
  /** Justify string to the right. E.g. "&emsp; right" */
  public static String rJust(String str, int length, String filler) {
    int sSize = str.length(), fSize = filler.length();

    if (fSize == 0 || sSize >= length) return str;
    if (fSize == 1) return repeat(filler, length-sSize)+str;
    int add = length-sSize;
    return repeat(filler, add/fSize)+filler.substring(0, add%fSize)+str;
  }
  public static Seq<String> rJust(Seq<String> list, int length) { return rJust(list, length, " "); }
  public static Seq<String> rJust(Seq<String> list, int length, String filler) {
    list.replace(str -> rJust(str, length, filler));
    return list;
  }

  public static String lJust(String str, int length) { return lJust(str, length, " "); }
  /** Justify string to the left. E.g. "left &emsp;" */
  public static String lJust(String str, int length, String filler) {
    int sSize = str.length(), fSize = filler.length();

    if (fSize == 0 || sSize >= length) return str;
    if (fSize == 1) return str+repeat(filler, length-sSize);
    int add = length-sSize;
    return str+repeat(filler, add/fSize)+filler.substring(0, add%fSize);
  }
  public static Seq<String> lJust(Seq<String> list, int length) { return lJust(list, length, " "); }
  public static Seq<String> lJust(Seq<String> list, int length, String filler) {
    list.replace(str -> lJust(str, length, filler));
    return list;
  }

  public static String cJust(String str, int length) { return cJust(str, length, " "); }
  /** Justify string to the center. E.g. "&emsp; center &emsp;". */
  public static String cJust(String str, int length, String filler) {
    int sSize = str.length(), fSize = filler.length();

    if (fSize == 0 || sSize >= length) return str;
    int add = length-sSize, left = add/2, right = add-add/2;
    if (fSize == 1) return repeat(filler, left)+str+repeat(filler, right);
    return repeat(filler, left/fSize)+filler.substring(0, left%fSize)+str+
           repeat(filler, right/fSize)+filler.substring(0, right%fSize);
  }
  public static Seq<String> cJust(Seq<String> list, int length) { return cJust(list, length, " "); }
  public static Seq<String> cJust(Seq<String> list, int length, String filler) {
    list.replace(str -> cJust(str, length, filler));
    return list;
  }

  public static String sJust(String left, String right, int length) { return sJust(left, right, length, " "); }
  /** Justify string to the sides. E.g. "left &emsp; right" */
  public static String sJust(String left, String right, int length, String filler) {
    int fSize = filler.length(), lSize = left.length(), rSize = right.length();

    if (fSize == 0 || lSize+rSize >= length) return left+right;
    int add = length-lSize-rSize;
    if (fSize == 1) return left+repeat(filler, add)+right;
    return left+repeat(filler, add/fSize)+filler.substring(0, add%fSize)+right;
  }
  public static Seq<String> sJust(Seq<String> left, Seq<String> right, int length) { return sJust(left, right, length, " "); }
  public static Seq<String> sJust(Seq<String> left, Seq<String> right, int length, String filler) {
    Seq<String> arr = /*new Seq<>(Integer.max(left.size, right.size))*/left; // for optimization, the left side will be used
    int i = 0;

    for (; i<Integer.min(left.size, right.size); i++) arr.set(i, /*.add(*/sJust(left.get(i), right.get(i), length, filler));
    // Fill the rest
    for (; i<left.size; i++) arr.set(i, /*.add(*/lJust(left.get(i), length, filler));
    for (; i<right.size; i++) arr.add(rJust(right.get(i), length, filler));

    return arr;
  }

  @SafeVarargs
  public static Seq<String> columnify(Seq<String>... columns) {
    return columnify(1, columns);
  }

  @SafeVarargs
  public static Seq<String> columnify(int gap, Seq<String>... columns) {
    return columnify(gap, " ", columns);
  }

  @SafeVarargs
  public static Seq<String> columnify(String filler, Seq<String>... columns) {
    return columnify(1, filler, columns);
  }

  @SafeVarargs
  public static Seq<String> columnify(int gap, String filler, Seq<String>... columns) {
    int[] lengths = new int[columns.length];
    for (int c=0; c<columns.length; c++) lengths[c] = maxLength(columns[c]);
    return columnify(filler, columns, lengths);
  }

  public static Seq<String> columnify(String filler, Seq<String>[] columns, int[] lengths) {
    return columnify(1, filler, columns, lengths);
  }

  public static Seq<String> columnify(int gap, String filler, Seq<String>[] columns, int[] lengths) {
    if (columns.length == 0) return new Seq<>(0);
    if (lengths.length < columns.length) {
      int[] newLengths = new int[columns.length];
      System.arraycopy(lengths, 0, newLengths, 0, lengths.length);
      for (int c=lengths.length; c<columns.length; c++) newLengths[c] = maxLength(columns[c]);
      lengths = newLengths;
    }

    int max = Structs.max(columns, a -> a.size), fSize = filler.length();
    Seq<String> arr = new Seq<>(max);
    StringBuilder builder = new StringBuilder();
    String[] fillers = new String[columns.length];

    for (int i=0, c; i<max; i++) {
      for (c=0; c<columns.length; c++) {
        if (i < columns[c].size) builder.append(lJust(columns[c].get(i), lengths[c] + gap, filler));
        else if (fillers[c] != null) builder.append(fillers[c]);
        else if (fSize == 1) builder.append(fillers[c] = repeat(filler, lengths[c] + gap));
        else builder.append(fillers[c] = repeat(filler, (lengths[c] + gap) / fSize) +
                                         filler.substring(0, (lengths[c] + gap) % fSize));
      }
      arr.add(builder.toString());
      builder.setLength(0);
    }

    return arr;
  }

  public static Seq<String> tableify(Seq<String> lines, int width) {
    return tableify(lines, width, Strings::lJust);
  }

  public static Seq<String> tableify(Seq<String> lines, int width, int gap) {
    return tableify(lines, width, gap, Strings::lJust);
  }

  public static Seq<String> tableify(Seq<String> lines, int width, Func2<String, Integer, String> justifier) {
    return tableify(lines, width, 1, justifier);
  }

  /**
   * Create a table with given {@code lines}.<br>
   * Columns number is automatic calculated with the table's {@code width}.
   */
  public static Seq<String> tableify(Seq<String> lines, int width, int gap, Func2<String, Integer, String> justifier) {
    int columns = Math.max(1, width / (maxLength(lines) + 2)); // Estimate the columns
    Seq<String> result = new Seq<>(lines.size / columns + 1);
    int[] bests = new int[columns];
    StringBuilder builder = new StringBuilder();

    // Calculate the best length for each columns
    for (int i=0, c=0, s=0; i<lines.size; i++) {
      s = lines.get(i).length();
      c = i % columns;
      if (s > bests[c]) bests[c] = s;
    }

    // Now justify lines
    for (int i=0, c; i<lines.size;) {
      for (c=0; c<columns && i<lines.size; c++, i++)
        builder.append(justifier.get(lines.get(i), bests[c] + gap));

      result.add(builder.toString());
      builder.setLength(0);
    }

    return result;
  }

  public static boolean isVersionAtLeast(String currentVersion, String newVersion) {
    return compareVersion(currentVersion, newVersion, 0) < 0;
  }

  public static boolean isVersionAtLeast(String currentVersion, String newVersion, int maxDepth) {
    return compareVersion(currentVersion, newVersion, maxDepth) < 0;
  }

  public static int compareVersion(String currentVersion, String newVersion) {
    return compareVersion(currentVersion, newVersion, 0);
  }

  /**
   * Compare if {@code newVersion} is greater or less than {@code currentVersion}, e.g. "v146" > "124.1". <br>
   * {@code maxDepth} defines the number of comparisons of version segments, allowing sub-versions to be ignored.
   * (default is 0)
   *
   * @apiNote can handle dots and dashes in the version and makes very fast comparison. <br>
   *          Also ignores non-int parts. (e.g. {@code "v1.2-rc36"}, the {@code "rc36"} part will be ignored)
   */
  @SuppressWarnings("null")
  public static int compareVersion(String currentVersion, String newVersion, int maxDepth) {
    if (maxDepth < 1) maxDepth = Integer.MAX_VALUE;

    boolean currentEmpty = currentVersion == null || currentVersion.isEmpty();
    boolean newEmpty = newVersion == null || newVersion.isEmpty();
    if (currentEmpty && newEmpty) return 0;
    else if (currentEmpty && !newEmpty) return -1;
    else if (!currentEmpty && newEmpty) return 1;

    int last1 = currentVersion.charAt(0) == 'v' ? 1 : 0, last2 = newVersion.charAt(0) == 'v' ? 1 : 0,
        len1 = currentVersion.length(),
        len2 = newVersion.length(),
        dash1 = 0, dash2 = 0,
        dot1 = 0, dot2 = 0,
        p1 = 0, p2 = 0;

    while ((dot1 != -1 && dot2 != -1) && (last1 < len1 && last2 < len2) && maxDepth-- > 0) {
      dot1 = currentVersion.indexOf('.', last1);
      dash1 = currentVersion.indexOf('-', last1);
      dot2 = newVersion.indexOf('.', last2);
      dash2 = newVersion.indexOf('-', last2);
      if (dot1 == -1) dot1 = dash1;
      if (dash1 != -1) dot1 = Math.min(dot1, dash1);
      if (dot1 == -1) dot1 = len1;
      if (dot2 == -1) dot2 = dash2;
      if (dash2 != -1) dot2 = Math.min(dot2, dash2);
      if (dot2 == -1) dot2 = len2;

      p1 = parseInt(currentVersion, 10, 0, last1, dot1);
      p2 = parseInt(newVersion, 10, 0, last2, dot2);
      last1 = dot1+1;
      last2 = dot2+1;

      if (p1 != p2) return p1 < p2 ? -1 : 1;
    }
    if (maxDepth <= 0) return p1 < p2 ? -1 : 1;

    // Continue iteration on newVersion to see if it's just leading zeros.
    while (dot2 != -1 && last2 < len2) {
      dot2 = newVersion.indexOf('.', last2);
      dash2 = newVersion.indexOf('-', last2);
      if (dot2 == -1) dot2 = dash2;
      if (dash2 != -1) dot2 = Math.min(dot2, dash2);
      if (dot2 == -1) dot2 = len2;

      p2 = parseInt(newVersion, 10, 0, last2, dot2);
      last2 = dot2+1;

      if (p2 > 0) return -1;
    }

    return 0;
  }

  public static String kebabize(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    boolean sep = true;

    for (int i=0, n=s.length(); i<n; i++) {
      char c = s.charAt(i);
      if (c == '_' || c == '-' || c == ' ') {
        if (!sep) sb.append('-');
        sep = true;
      } else if (Character.isUpperCase(c)) {
        if (!sep) sb.append('-');
        sb.append(Character.toLowerCase(c));
        sep = false;
      } else {
        sb.append(c);
        sep = false;
      }
    }

    return sb.toString();
  }

  /** Taken from the {@link String#repeat(int)} method of JDK 11 */
  public static String repeat(String str, int count) {
    if (count < 0) throw new IllegalArgumentException("count is negative: " + count);
    if (count == 1) return str;

    final byte[] value = str.getBytes();
    final int len = value.length;
    if (len == 0 || count == 0)  return "";
    if (Integer.MAX_VALUE / count < len)
      throw new OutOfMemoryError("Required length exceeds implementation limit");
    if (len == 1) {
      final byte[] single = new byte[count];
      Arrays.fill(single, value[0]);
      return new String(single);
    }

    final int limit = len * count;
    final byte[] multiple = new byte[limit];
    System.arraycopy(value, 0, multiple, 0, len);
    int copied = len;
    for (; copied < limit - copied; copied <<= 1)
      System.arraycopy(multiple, 0, multiple, copied, copied);
    System.arraycopy(multiple, 0, multiple, copied, limit - copied);
    return new String(multiple);
  }

  public static String repeat(char c, int count) {
    final char[] single = new char[count];
    Arrays.fill(single, c);
    return new String(single);
  }

  public static int maxLength(Iterable<? extends String> list) {
    return Structs.max(list, String::length);
  }

  public static int maxLength(String... list) {
    return Structs.max(list, String::length);
  }

  //TODO: need to merge these three methods in one StringBuilder
  public static String normalize(String str) {
    return stripGlyphs(stripColors(str)).trim();
  }

  /** @return whether the specified string mean true */
  public static boolean isTrue(String str) {
    return isTrue(str, true);
  }

  /** @return whether the specified string mean true */
  public static boolean isTrue(String str, boolean isValue) {
    if (isValue) {
      return switch (str.toLowerCase()) {
        case "1", "true", "on", "enable", "activate", "yes" -> true;
        default -> false;
      };
    } else {
      return switch (str.toLowerCase()) {
        case "on", "enable", "activate" -> true;
        default -> false;
      };
    }
  }

  /** @return whether the specified string mean false */
  public static boolean isFalse(String str) {
    return isFalse(str, true);
  }

  /** @return whether the specified string mean false */
  public static boolean isFalse(String str, boolean isValue) {
    if (isValue) {
      return switch (str.toLowerCase()) {
        case "0", "false", "off", "disable", "desactivate", "no" -> true;
        default -> false;
      };
    } else {
      return switch (str.toLowerCase()) {
        case "off", "disable", "desactivate" -> true;
        default -> false;
      };
    }
  }

  public static String jsonPrettyPrint(JsonValue object, OutputType outputType) {
    StringWriter out = new StringWriter();
    try { jsonPrettyPrint(object, out, outputType, 0); }
    catch (IOException _) { return ""; }
    return out.toString();
  }

  public static void jsonPrettyPrint(JsonValue object, Writer writer, OutputType outputType) throws IOException {
    jsonPrettyPrint(object, writer, outputType, 0);
  }

  /**
   * Re-implementation of {@link JsonValue#prettyPrint(OutputType, Writer)},
   * because the ident isn't correct and the max object size before new line is too big.
   */
  public static void jsonPrettyPrint(JsonValue object, Writer writer, OutputType outputType, int indent)
  throws IOException {
    switch (object.type()) {
      case object:
        if (object.child == null) writer.write("{}");
        else {
          indent++;
          boolean newLines = needNewLine(object, 1);
          writer.write(newLines ? "{\n" : "{ ");
          for (JsonValue child = object.child; child != null; child = child.next) {
            if(newLines) writer.write(repeat("  ", indent));
            writer.write(outputType.quoteName(child.name));
            writer.write(": ");
            jsonPrettyPrint(child, writer, outputType, indent);
            if((!newLines || outputType != OutputType.minimal) && child.next != null) writer.append(',');
            writer.write(newLines ? '\n' : ' ');
          }
          if(newLines) writer.write(repeat("  ", indent - 1));
          writer.write('}');
        }
        break;
      case array:
        if (object.child == null) writer.write("[]");
        else {
          indent++;
          boolean newLines = needNewLine(object, 1);
          writer.write(newLines ? "[\n" : "[ ");
          for (JsonValue child = object.child; child != null; child = child.next) {
            if (newLines) writer.write(repeat("  ", indent));
            jsonPrettyPrint(child, writer, outputType, indent);
            if ((!newLines || outputType != OutputType.minimal) && child.next != null) writer.append(',');
            writer.write(newLines ? '\n' : ' ');
          }
          if (newLines) writer.append(repeat("  ", indent - 1));
          writer.write(']');
        }
        break;
      case stringValue: writer.write(outputType.quoteValue(object.asString())); break;
      case doubleValue: writer.write(Double.toString(object.asDouble())); break;
      case longValue: writer.write(Long.toString(object.asLong())); break;
      case booleanValue: writer.write(Boolean.toString(object.asBoolean())); break;
      case nullValue: writer.write("null"); break;
      default: throw new SerializationException("Unknown object type: " + object);
    }
  }

  /**
   * Re-implementation of {@link JsonValue#json(JsonValue, StringBuilder, OutputType)},
   * with the ability write directly to a {@link Writer} instead of a {@link StringBuilder}.
   */
  public static void toJson(JsonValue object, Writer writer, OutputType outputType) throws IOException {
    switch (object.type()) {
      case object:
        if (object.child == null) writer.write("{}");
        else {
          writer.write('{');
          for (JsonValue child = object.child; child != null; child = child.next) {
            writer.write(outputType.quoteName(child.name));
            writer.write(':');
            toJson(child, writer, outputType);
            if (child.next != null) writer.write(',');
          }
          writer.write('}');
        }
        break;
      case array:
        if (object.child == null) writer.write("[]");
        else {
          writer.write('[');
          for (JsonValue child = object.child; child != null; child = child.next) {
            toJson(child, writer, outputType);
            if (child.next != null) writer.write(',');
          }
          writer.write(']');
        }
        break;
      case stringValue: writer.write(outputType.quoteValue(object.asString())); break;
      case doubleValue: writer.write(Double.toString(object.asDouble())); break;
      case longValue: writer.write(Long.toString(object.asLong())); break;
      case booleanValue: writer.write(Boolean.toString(object.asBoolean())); break;
      case nullValue: writer.write("null"); break;
      default: throw new SerializationException("Unknown object type: " + object);
    }
  }

  private static boolean needNewLine(JsonValue object, int maxChildren) {
    for (JsonValue child = object.child; child != null; child = child.next)
      if (child.isObject() || child.isArray() || --maxChildren < 0) return true;
    return false;
  }

  /** Like {@link #format(String, Object[])} but you can specify the {@link StringBuilder}. */
  public static void format(StringBuilder out, String text, Object... args){
    if (args.length > 0) {
      for (int i=0, argi=0, n=text.length(); i<n; i++) {
        char c = text.charAt(i);
        if (c == '@' && argi < args.length) out.append(args[argi++]);
        else out.append(c);
      }
    } else out.append(text);
  }

  /** Like {@link String#join(CharSequence, CharSequence[])} but you can specify the start and end of the list. */
  public static String join(CharSequence delimiter, CharSequence[] elements, int start, int end) {
    if (elements == null || delimiter == null) return null;
    if (elements.length == 0 || start < 0 || end > elements.length || start >= end) return "";

    StringJoiner joiner = new StringJoiner(delimiter);
    for (int i=start; i<end; i++) joiner.add(elements[i]);
    return joiner.toString();
  }

  /** Converts a list to a human readable sentence. E.g. {@code [1, 2, 3, 4, 5]} -> {@code "1, 2, 3, 4 and 5"} */
  public static <T> String toSentence(Iterable<T> list, Func<T, String> stringifier) {
    StringBuilder builder = new StringBuilder();
    toSentence(builder, list, (b, e) -> b.append(stringifier.get(e)));
    return builder.toString();
  }

  /** Converts a list to a human readable sentence with configurable {@code or} and {@code and} delimiters. */
  public static <T> String toSentence(Iterable<T> list, Func<T, String> stringifier, String or, String and) {
    StringBuilder builder = new StringBuilder();
    toSentence(builder, list, (b, e) -> b.append(stringifier.get(e)), or, and);
    return builder.toString();
  }

  /** Converts a list to a human readable sentence. E.g. {@code [1, 2, 3, 4, 5]} -> {@code "1, 2, 3, 4 and 5"} */
  public static <T> void toSentence(StringBuilder builder, Iterable<T> list, Cons2<StringBuilder, T> stringifier) {
    toSentence(builder, list, stringifier, ", ", " and ");
  }
  /** Converts a list to a human readable sentence with configurable {@code or} and {@code and} delimiters. */
  public static <T> void toSentence(StringBuilder builder, Iterable<T> list, Cons2<StringBuilder, T> stringifier,
                                    String or, String and) {
    Iterator<T> iter = list.iterator();
    if (!iter.hasNext()) return;

    stringifier.get(builder, iter.next());
    while (iter.hasNext()) {
      T tmp = iter.next();
      builder.append(iter.hasNext() ? or : and);
      stringifier.get(builder, tmp);
    }
  }

  /** {@link String#contains(CharSequence)} but with a predicate. */
  public static boolean contains(String src, Boolf<Character> predicate) {
    for (int i=0, n=src.length(); i<n; i++) {
      if (predicate.get(src.charAt(i))) return true;
    }
    return false;
  }

  /** {@link String#matches(String)} but with a predicate. */
  public static boolean matches(String src, Boolf<Character> predicate) {
    for (int i=0, n=src.length(); i<n; i++) {
      if (!predicate.get(src.charAt(i))) return false;
    }
    return true;
  }

  /** Encodes a long to an url-safe base64 string. */
  public static String longToBase64(long l) {
    byte[] result = new byte[Long.BYTES];
    for (int i=Long.BYTES-1; i>=0; i--) {
      result[i] = (byte)(l & 0xFF);
      l >>= 8;
    }
    return new String(Base64Coder.encode(result, Base64Coder.urlsafeMap));
  }

  public static long base64ToLong(String str) {
    byte[] b = Base64Coder.decode(str, Base64Coder.urlsafeMap);
    if (b.length != Long.BYTES) throw new IndexOutOfBoundsException("must be " + Long.BYTES + " bytes");
    long result = 0;
    for (int i=0; i<Long.BYTES; i++) {
      result <<= 8;
      result |= (b[i] & 0xFF);
    }
    return result;
  }

  /** Method that suppress {@link IOException} from {@link ByteBufferInput#readUTF()}. */
  public static String readUTF(ByteBufferInput in) {
    try { return in.readUTF(); }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  /** Method that suppress {@link IOException} from {@link ByteBufferOutput#writeUTF(String)}. */
  public static void writeUTF(ByteBufferOutput in, String str) {
    try { in.writeUTF(str); }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  public static String formattedClassName(Class<?> clazz) {
    return insertSpaces(clazz.getSimpleName());
  }

  public static String formatBytes(long v) {
    return formatSize(v, "B");
  }

  public static String formatSize(long value, String unit) {
    boolean negated = false;
    if (value < 0) {
      negated = true;
      value = -value;
    }
    if (value < 1024) return (negated ? "-" + value : value) + " " + unit;
    int z = (63 - Long.numberOfLeadingZeros(value)) / 10;
    return String.format((negated ? "-%.1f %s" : "%.1f %s") + unit,
                         Math.scalb((double)value, z * -10), " KMGTPE".charAt(z));
  }

  private static final Object[][] TIME_PERIODS = {
    {"year",        "years",       "y",  1000L*60*60*24*365},
    {"month",       "months",      "mo", 1000L*60*60*24*30},
    //{"week",        "weeks",       "w",  1000L*60*60*24*7},
    {"day",         "days",        "d",  1000L*60*60*24},
    {"hour",        "hours",       "h",  1000L*60*60},
    {"minute",      "minutes",     "m",  1000L*60},
    {"second",      "seconds",     "s",  1000L},
    //{"millisecond", "millisecond", "ms", 1L},
  };

  /**
   * Convert {@code millis} duration to localized human readable format (e.g. 123456 -> 2 minutes and 3 seconds),
   * using the logger system.
   *
   * @param millis milliseconds to convert to duration
   * @param narrow use short units or not. E.g. "5 months" if {@code false}, "5mo" if {@code true}
   */
  public static String formatDuration(long millis, boolean narrow) {
    boolean negative = millis < 0;
    millis = Math.abs(millis);
    StringBuilder builder = new StringBuilder();
    boolean space = false;

    for (Object[] element : TIME_PERIODS) {
      long period = (long)element[3];
      if (millis >= period) {
        long count = millis / period;
        millis %= period;
        if (space) builder.append(' ');
        if (negative) builder.append('-');
        builder.append(count).append(element[narrow ? 2 : count > 1 ? 1 : 0]);
        space = true;
      }
    }

    return builder.toString();
  }
}
