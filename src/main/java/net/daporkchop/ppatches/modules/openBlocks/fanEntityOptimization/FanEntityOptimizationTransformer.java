package net.daporkchop.ppatches.modules.openBlocks.fanEntityOptimization;

import com.google.common.base.Predicate;
import net.daporkchop.ppatches.core.transform.ITreeClassTransformer;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.LambdaMetafactory;
import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class FanEntityOptimizationTransformer implements ITreeClassTransformer {
    @Override
    public boolean interestedInClass(String name, String transformedName) {
        return "openblocks.common.tileentity.TileEntityFan".equals(transformedName);
    }

    @Override
    public boolean transformClass(String name, String transformedName, ClassNode classNode) {
        for (MethodNode methodNode : classNode.methods) {
            for (ListIterator<AbstractInsnNode> itr = methodNode.instructions.iterator(); itr.hasNext(); ) {
                AbstractInsnNode insnNode = itr.next();
                if (insnNode.getOpcode() == INVOKEVIRTUAL) {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                    if ("net/minecraft/world/World".equals(methodInsnNode.owner)
                        && ("func_72872_a".equals(methodInsnNode.name) || "getEntitiesWithinAABB".equals(methodInsnNode.name))
                        && "(Ljava/lang/Class;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;".equals(methodInsnNode.desc)) {

                        //invoke isPushableEntity(Entity) using a lambda as the filter
                        itr.set(new InvokeDynamicInsnNode("apply", Type.getMethodDescriptor(Type.getType(Predicate.class)),
                                new Handle(H_INVOKESTATIC, Type.getInternalName(LambdaMetafactory.class), "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                                Type.getMethodType(Type.BOOLEAN_TYPE, Type.getType(Object.class)),
                                new Handle(H_INVOKESTATIC, classNode.name, "isPushableEntity", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(Entity.class)), false),
                                Type.getMethodType(Type.BOOLEAN_TYPE, Type.getType(Entity.class))));

                        itr.add(new MethodInsnNode(INVOKEVIRTUAL, methodInsnNode.owner,
                                "func_72872_a".equals(methodInsnNode.name) ? "func_175647_a" : "getEntitiesWithinAABB",
                                "(Ljava/lang/Class;Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;",
                                false));
                        return true;
                    }
                }
            }
        }

        throw new IllegalStateException("couldn't find call to getEntitiesWithinAABB(Class, AxisAlignedBB) in TileEntityFan to be replaced!");
    }
}
