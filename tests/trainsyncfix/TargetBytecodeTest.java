// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package trainsyncfix;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Guards the version-specific float-local injection without loading Minecraft. */
public final class TargetBytecodeTest {
    public static void main(String[] args) throws Exception {
        String owner="com/simibubi/create/content/trains/entity/CarriageSyncData";
        try(JarFile jar=new JarFile(Path.of(args[0]).toFile())) {
            ClassNode node=new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(owner+".class"))).accept(node,0);
            MethodNode approach=node.methods.stream().filter(m->m.name.equals("approach")).findFirst().orElseThrow();
            String prefix="(Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntity;Lcom/simibubi/create/content/trains/entity/Carriage;";
            if(!approach.desc.equals(prefix+"F)V"))throw new AssertionError("approach descriptor changed");
            MethodNode apply=node.methods.stream().filter(m->m.name.equals("apply")).findFirst().orElseThrow();
            if(!apply.desc.equals(prefix+")V"))throw new AssertionError("apply descriptor changed");
            List<Integer> floatSlots=approach.localVariables.stream().filter(v->v.desc.equals("F")).map(v->v.index).distinct().sorted().toList();
            if(floatSlots.size()<2 || floatSlots.get(1)!=6)throw new AssertionError("float ordinal 1 is no longer slot 6: "+floatSlots);
            int stores=0;
            for(AbstractInsnNode insn:approach.instructions) {
                if(insn instanceof VarInsnNode var && var.getOpcode()==Opcodes.FSTORE && var.var==6) {
                    stores++;
                    if(!(var.getPrevious() instanceof VarInsnNode load) || load.getOpcode()!=Opcodes.FLOAD || load.var!=3)
                        throw new AssertionError("partial initialization changed");
                }
            }
            if(stores!=1)throw new AssertionError("expected exactly one partial store, got "+stores);
            System.out.println("PASS: Create target descriptors and single final-partial float-local injection site");
        }
    }
}
