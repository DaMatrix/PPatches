package net.daporkchop.ppatches.util.asm.cp;

import lombok.experimental.UtilityClass;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class ConstantPoolConstants {
    //constants copied from https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4
    public static final int CONSTANT_Class = 7;
    public static final int CONSTANT_Fieldref = 9;
    public static final int CONSTANT_Methodref = 10;
    public static final int CONSTANT_InterfaceMethodref = 11;
    public static final int CONSTANT_String = 8;
    public static final int CONSTANT_Integer = 3;
    public static final int CONSTANT_Float = 4;
    public static final int CONSTANT_Long = 5;
    public static final int CONSTANT_Double = 6;
    public static final int CONSTANT_NameAndType = 12;
    public static final int CONSTANT_Utf8 = 1;
    public static final int CONSTANT_MethodHandle = 15;
    public static final int CONSTANT_MethodType = 16;
    public static final int CONSTANT_InvokeDynamic = 18;

    public static final int MAX_CONSTANT_TAGS = 18 + 1;
}
