package me.nov.threadtear.execution.cleanup;

import me.nov.threadtear.execution.Clazz;
import me.nov.threadtear.execution.Execution;
import me.nov.threadtear.execution.ExecutionCategory;
import me.nov.threadtear.execution.ExecutionTag;
import me.nov.threadtear.util.asm.Access;
import org.apache.commons.lang3.StringUtils;
import org.benf.cfr.reader.bytecode.analysis.variables.Keywords;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

public class GuessParameterNames extends Execution {
  public GuessParameterNames() {
    super(
      ExecutionCategory.CLEANING,
      "Guess parameter names",
      "Gives names to method parameters based on their type if they don't have one.",
      ExecutionTag.RUNNABLE,
      ExecutionTag.BETTER_DECOMPILE
    );
  }

  @Override
  public boolean execute(Map<String, Clazz> map, boolean verbose) {
    Map<Type, Integer> typesCount = new HashMap<>();
    Map<Type, Integer> typesIndex = new HashMap<>();
    int count = 0;

    for (Clazz clazz : map.values()) {
      for (MethodNode method : clazz.node.methods) {
        // Check if method already contains parameter info
        boolean addParameters = method.parameters == null || method.parameters.isEmpty();
        boolean addLocalVariables = method.localVariables == null || method.localVariables.isEmpty();

        if (!addParameters && !addLocalVariables) continue;

        // Get parameter types
        Type[] types = Type.getArgumentTypes(method.desc);
        if (types.length == 0) continue;

        // Get parameter types count
        for (Type type : types) {
          typesCount.compute(type, (t, tCount) -> tCount == null ? 1 : tCount + 1);
        }

        // Get start and end labels
        Label start = getStartLabel(method);
        Label end = getEndLabel(method);

        // Add parameter info
        int i = Access.isStatic(method.access) ? 0 : 1;

        for (Type type : types) {
          String name = getName(type);

          int typeCount = typesCount.get(type);
          if (typeCount > 1) {
            int index = typesIndex.compute(type, (t, tIndex) -> tIndex == null ? 1 : tIndex + 1);
            name += index;
          }

          if (addParameters) method.visitParameter(name, 0);
          if (addLocalVariables) method.visitLocalVariable(name, type.getDescriptor(), null, start, end, i);

          i++;
        }

        count += types.length;

        // Reset method state
        typesCount.clear();
        typesIndex.clear();
      }
    }

    logger.info("Added {} parameter names!", count);
    return true;
  }

  private String getName(Type type) {
    String name = type.getClassName();

    int dotI = name.lastIndexOf('.');
    if (dotI != -1) name = name.substring(dotI + 1);

    name = StringUtils.remove(name, '$');
    name = name.replace("[]", "Array");

    char first = name.charAt(0);
    if (Character.isUpperCase(first)) name = Character.toLowerCase(first) + name.substring(1);

    if (Keywords.isAKeyword(name)) name = "_" + name;

    return name;
  }

  private Label getStartLabel(MethodNode method) {
    if (method.instructions.getFirst() instanceof LabelNode labelNode) {
      return labelNode.getLabel();
    }

    Label start = new Label();
    start.info = new LabelNode(start);

    method.instructions.insert((AbstractInsnNode) start.info);

    return start;
  }

  private Label getEndLabel(MethodNode method) {
    if (method.instructions.getLast() instanceof LabelNode labelNode) {
      return labelNode.getLabel();
    }

    Label end = new Label();
    method.visitLabel(end);

    return end;
  }

  @Override
  public String getAuthor() {
    return "MineGame159";
  }
}
