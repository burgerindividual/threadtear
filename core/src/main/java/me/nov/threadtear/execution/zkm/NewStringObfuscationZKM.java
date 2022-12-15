package me.nov.threadtear.execution.zkm;

import me.nov.threadtear.execution.Clazz;
import me.nov.threadtear.execution.Execution;
import me.nov.threadtear.execution.ExecutionCategory;
import me.nov.threadtear.execution.ExecutionTag;
import me.nov.threadtear.io.Conversion;
import me.nov.threadtear.security.VMSecurityManager;
import me.nov.threadtear.vm.IVMReferenceHandler;
import me.nov.threadtear.vm.VM;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class NewStringObfuscationZKM extends Execution implements IVMReferenceHandler {
  private Map<String, Clazz> classes;

  private int countSuccess;
  private int countFailure;

  public NewStringObfuscationZKM() {
    super(
      ExecutionCategory.ZKM,
      "New string obfuscation removal",
      "Deobfuscates strings.",
      ExecutionTag.RUNNABLE,
      ExecutionTag.POSSIBLY_MALICIOUS
    );
  }

  @SuppressWarnings("removal")
  @Override
  public boolean execute(Map<String, Clazz> map, boolean verbose) {
    countSuccess = 0;
    countFailure = 0;

    VMSecurityManager securityManager = (VMSecurityManager) System.getSecurityManager();
    securityManager.grantAll = true;

    classes = map;
    VM vm = VM.constructVM(this);

    for (Clazz clazz : map.values()) {
      decryptAll(vm, clazz.node);
    }

    securityManager.grantAll = false;

    logger.info("Successfully decrypted {} strings and failed to decrypt {} strings!", countSuccess, countFailure);
    return true;
  }

  private void decryptAll(VM vm, ClassNode klass) {
    if (!containsStringEncryption(klass)) return;

    // Loop methods
    Class<?> loadedClass = null;

    for (MethodNode method : klass.methods) {
      if (method.instructions.size() < 4) continue;

      // Check for seed variable
      String seedFieldName;
      long methodSeed;

      if (
        method.instructions.getFirst().getOpcode() == Opcodes.GETSTATIC &&
        method.instructions.getFirst().getNext().getOpcode() == Opcodes.LDC &&
        method.instructions.getFirst().getNext().getNext().getOpcode() == Opcodes.LXOR &&
        method.instructions.getFirst().getNext().getNext().getNext().getOpcode() == Opcodes.LSTORE
      ) {
        seedFieldName = ((FieldInsnNode) method.instructions.getFirst()).name;
        methodSeed = (long) ((LdcInsnNode) method.instructions.getFirst().getNext()).cst;
      }
      else continue;

      // Search for INVOKEDYNAMIC calls to the decrypt method
      List<DecryptCall> calls = new ArrayList<>();

      for (AbstractInsnNode insn : method.instructions) {
        if (!(insn instanceof InvokeDynamicInsnNode invokeDynamicInsn) || !invokeDynamicInsn.desc.equals("(IJ)Ljava/lang/String;")) continue;

        AbstractInsnNode lxor = insn.getPrevious();
        if (lxor.getOpcode() != Opcodes.LXOR) continue;

        AbstractInsnNode lload = lxor.getPrevious();
        if (lload.getOpcode() != Opcodes.LLOAD) continue;

        AbstractInsnNode ldc = lload.getPrevious();
        if (!(ldc instanceof LdcInsnNode ldcInsnNode)) continue;

        AbstractInsnNode intInsn = ldc.getPrevious();
        if (!(intInsn instanceof IntInsnNode intInsnNode)) continue;

        // Found a call
        calls.add(new DecryptCall(
          new AbstractInsnNode[] { intInsn, ldc, lload, lxor, insn },
          intInsnNode.operand,
          (Long) ldcInsnNode.cst
        ));
      }

      // Replace calls
      for (DecryptCall call : calls) {
        // Store the instruction before the call
        AbstractInsnNode prevInsn = call.insns[0].getPrevious();

        // Remove call instructions
        for (AbstractInsnNode insn : call.insns) {
          method.instructions.remove(insn);
        }

        // Decrypt string
        if (loadedClass == null) loadedClass = loadClass(vm, klass);

        if (loadedClass == null) {
          logger.error("{}: Failed to load copied class into VM", klass.name);
          countFailure++;
          continue;
        }

        String string = decrypt(loadedClass, seedFieldName, methodSeed, call);
        if (string == null) {
          countFailure++;
          continue;
        }

        // Insert LDC string instruction
        method.instructions.insert(prevInsn, new LdcInsnNode(string));

        countSuccess++;
      }
    }
  }

  private String decrypt(Class<?> klass, String seedFieldName, long methodSeed, DecryptCall call) {
    try {
      Field seedField = klass.getDeclaredField(seedFieldName);
      seedField.setAccessible(true);
      long seed = (long) seedField.get(null);

      long var4 = seed ^ methodSeed;
      int arg1 = call.arg1;
      long arg2 = call.arg2 ^ var4;

      Method method = klass.getDeclaredMethod("a", int.class, long.class);
      method.setAccessible(true);
      return (String) method.invoke(null, arg1, arg2);
    }
    catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | ExceptionInInitializerError | NoClassDefFoundError e) {
      logger.error("{}: Failed to decrypt string", klass.getName());
      return null;
    }
  }

  private Class<?> loadClass(VM vm, ClassNode klass) {
    String name = klass.name.replace('/', '.');

    // Check if its already loaded, idk why this happens but oh well
    Class<?> loadedClass = vm.loaded.get(name);
    if (loadedClass != null) return loadedClass;

    // Copy class and get static initializer
    ClassNode copy = copyClassWithNeededMethodsAndFields(klass);
    MethodNode clinit = getClinit(copy);

    // Modify static initializer to not initialize static fields not related to string decryption
    int newArrayI = 0;

    //noinspection DataFlowIssue
    for (AbstractInsnNode insn : clinit.instructions) {
      if (insn instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.ANEWARRAY) {
        if (newArrayI == 1) {
          clinit.instructions.insertBefore(insn.getNext().getNext(), new InsnNode(Opcodes.RETURN));
          break;
        }

        newArrayI++;
      }
    }

    // Load class
    vm.explicitlyPreload(copy);
    return vm.loaded.get(name);
  }

  private ClassNode copyClassWithNeededMethodsAndFields(ClassNode klass) {
    ClassNode copy = Conversion.toNode(Conversion.toBytecode0(klass));

    ClassNode node = new ClassNode();
    node.version = copy.version;
    node.access = copy.access;
    node.name = copy.name;
    node.superName = copy.superName;

    MethodNode clinit = getClinit(copy);
    int clinitIndex = copy.methods.indexOf(clinit);
    Set<MethodNode> copiedMethods = new HashSet<>(copy.methods.subList(clinitIndex, copy.methods.size()));

    //noinspection DataFlowIssue
    Set<MethodNode> methodInvocations = Arrays.stream(clinit.instructions.toArray())
      .filter(insn -> insn instanceof MethodInsnNode)
      .map(insn -> (MethodInsnNode) insn)
      .filter(insn -> insn.owner.equals(copy.name))
      .flatMap(insn -> copy.methods.stream().filter(method -> method.name.equals(insn.name) && method.desc.equals(insn.desc)))
      .collect(Collectors.toSet());

    Set<MethodNode> methods = new HashSet<>();
    methods.addAll(copiedMethods);
    methods.addAll(methodInvocations);

    node.methods.addAll(methods);
    node.fields.addAll(copy.fields);

    return node;
  }

  private boolean containsStringEncryption(ClassNode klass) {
    MethodNode clinit = getClinit(klass);
    if (clinit == null) return false;

    int encryptionIdentifyingStrings = 0;

    for (AbstractInsnNode insn : clinit.instructions) {
      if (!(insn instanceof LdcInsnNode ldcInsn) || !(ldcInsn.cst instanceof String string)) continue;

      if (string.equals("DES/CBC/PKCS5Padding") || string.equals("DES") || string.equals("ISO-8859-1")) {
        encryptionIdentifyingStrings++;
      }
    }

    return encryptionIdentifyingStrings == 3;
  }

  private MethodNode getClinit(ClassNode klass) {
    for (MethodNode method : klass.methods) {
      if (method.name.equals("<clinit>")) return method;
    }

    return null;
  }

  @Override
  public ClassNode tryClassLoad(String name) {
    Clazz clazz = classes.get(name);
    return clazz != null ? clazz.node : null;
  }

  @Override
  public String getAuthor() {
    return "MineGame159";
  }

  private record DecryptCall(AbstractInsnNode[] insns, int arg1, long arg2) {}
}
