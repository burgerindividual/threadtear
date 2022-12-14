package me.nov.threadtear.execution.cleanup;

import me.nov.threadtear.execution.Clazz;
import me.nov.threadtear.execution.Execution;
import me.nov.threadtear.execution.ExecutionCategory;
import me.nov.threadtear.execution.ExecutionTag;
import me.nov.threadtear.util.Mappings;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Map;

public class Remapper extends Execution {
  public Remapper() {
    super(
      ExecutionCategory.CLEANING,
      "Remapper",
      "Remaps class, method and fields names using a provided 'mappings.xxx' file. Currently only tested with 'tiny' aka yarn mappings.",
      ExecutionTag.BETTER_DECOMPILE,
      ExecutionTag.BETTER_DEOBFUSCATE
    );
  }

  @Override
  public boolean execute(Map<String, Clazz> map, boolean verbose) {
    // Read mappings
    Mappings mappings = readMappings();

    if (mappings == null) {
      logger.error("Failed to read 'mappings.xxx' file.");
      return false;
    }

    // Remap
    for (Clazz clazz : map.values()) {
      ClassNode copy = new ClassNode();

      ClassRemapper remapper = new ClassRemapper(copy, mappings);
      clazz.node.accept(remapper);

      clazz.node = copy;
    }

    logger.info("Remapped {} names!", mappings.remappedCount);
    return true;
  }

  private Mappings readMappings() {
    File[] files = new File(".").listFiles();
    if (files == null) return null;

    File mappingsFile = null;

    for (File file : files) {
      String name = file.getName();

      int dotI = name.lastIndexOf('.');
      if (dotI != -1) name = name.substring(0, dotI);

      if (file.isFile() && name.equals("mappings")) {
        mappingsFile = file;
        break;
      }
    }

    if (mappingsFile == null) return null;

    try {
      return Mappings.read(new FileReader(mappingsFile));
    }
    catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public String getAuthor() {
    return "MineGame159";
  }
}
