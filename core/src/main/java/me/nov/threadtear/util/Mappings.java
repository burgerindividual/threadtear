package me.nov.threadtear.util;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mappings extends Remapper implements MappingVisitor {
  public static Mappings read(Reader reader) {
    Mappings mappings = new Mappings();

    try {
      MappingReader.read(reader, mappings);
    }
    catch (IOException e) {
      return null;
    }

    return mappings;
  }

  // Class

  private final Map<String, String> classes = new HashMap<>();
  private final Map<String, String> fields = new HashMap<>();
  private final Map<String, String> methods = new HashMap<>();

  public int remappedCount = 0;

  private Mappings() {}

  // Remapper

  @Override
  public String mapMethodName(String owner, String name, String descriptor) {
    String mappedName = methods.get(name);

    if (mappedName != null) {
      remappedCount++;
      return mappedName;
    }

    return name;
  }

  @Override
  public String mapFieldName(String owner, String name, String descriptor) {
    String mappedName = fields.get(name);

    if (mappedName != null) {
      remappedCount++;
      return mappedName;
    }

    return name;
  }

  @Override
  public String map(String internalName) {
    String mappedName = classes.get(internalName);

    if (mappedName != null) {
      remappedCount++;
      return mappedName;
    }

    return internalName;
  }


  // MappingVisitor

  private String key;

  @Override
  public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {}

  @Override
  public boolean visitClass(String srcName) throws IOException {
    return true;
  }

  @Override
  public boolean visitField(String srcName, String srcDesc) throws IOException {
    return true;
  }

  @Override
  public boolean visitMethod(String srcName, String srcDesc) throws IOException {
    return true;
  }

  @Override
  public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) throws IOException {
    return false;
  }

  @Override
  public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) throws IOException {
    return false;
  }

  @Override
  public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
    if (namespace == 0) {
      key = name;
      return;
    }

    switch (targetKind) {
      case CLASS ->  classes.put(key, name);
      case FIELD ->  fields.put(key, name);
      case METHOD -> methods.put(key, name);
    }
  }

  @Override
  public void visitComment(MappedElementKind targetKind, String comment) throws IOException {}
}
