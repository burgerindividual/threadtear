package me.nov.threadtear;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;

public class Utils {
  public static File openFileDialog(String title, String defaultPath, String... filters) {
    if (defaultPath == null) defaultPath = System.getProperty("user.home");

    PointerBuffer filtersBuffer = allocPointerBuffer(filters);
    String result = TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, filtersBuffer, null, false);
    freePointerBuffer(filtersBuffer);

    return result != null ? new File(result) : null;
  }

  public static File saveFileDialog(String title, String defaultPath, String... filters) {
    if (defaultPath == null) defaultPath = System.getProperty("user.home");

    PointerBuffer filtersBuffer = allocPointerBuffer(filters);
    String result = TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, filtersBuffer, null);
    freePointerBuffer(filtersBuffer);

    return result != null ? new File(result) : null;
  }

  public static PointerBuffer allocPointerBuffer(String... strings) {
    PointerBuffer buffer = MemoryUtil.memAllocPointer(strings.length);

    for (int i = 0; i < strings.length; i++) {
      buffer.put(i, MemoryUtil.memASCII(strings[i]));
    }

    return buffer;
  }

  public static void freePointerBuffer(PointerBuffer buffer) {
    for (int i = 0; i < buffer.limit(); i++) {
      MemoryUtil.nmemFree(buffer.get(i));
    }

    MemoryUtil.memFree(buffer);
  }
}
