package me.nov.threadtear.decompiler;

import me.nov.threadtear.io.JarIO;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

public class QuiltflowerBridge implements IDecompilerBridge {
  protected static final Map<String, Object> options = new HashMap<>(IFernflowerPreferences.DEFAULTS);

  @Override
  public void setAggressive(boolean aggressive) {}

  private String result;

  public String decompile(File archive, String name, byte[] bytes) {
    result = null;

    try {
      ByteArrayOutputStream log = new ByteArrayOutputStream();
      Fernflower f = new Fernflower(new Saver(), options, new PrintStreamLogger(new PrintStream(log)));

      File temp = JarIO.writeTempJar(name, bytes);
      f.addSource(temp);

      f.decompileContext();
      f.clearContext();
    }
    catch (Throwable t) {
      t.printStackTrace();

      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);

      return sw.toString();
    }

    if (result == null || result.trim().isEmpty()) {
      result = "No Quiltflower output received";
    }

    return result;
  }

  public static class QuiltflowerDecompilerInfo extends DecompilerInfo<QuiltflowerBridge> {
    @Override
    public String getName() {
      return "Quiltflower";
    }

    @Override
    public String getVersionInfo() {
      return "1.9.0";
    }

    @Override
    public QuiltflowerBridge createDecompilerBridge() {
      return new QuiltflowerBridge();
    }
  }

  private class Saver implements IResultSaver {
    @Override
    public void saveFolder(String path) {}

    @Override
    public void copyFile(String source, String path, String entryName) {}

    @Override
    public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
      result = content;
    }

    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) {}

    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) {}

    @Override
    public void copyEntry(String source, String path, String archiveName, String entry) {}

    @Override
    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
      result = content;
    }

    @Override
    public void closeArchive(String path, String archiveName) {}
  }
}
