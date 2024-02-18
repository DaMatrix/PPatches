package net.daporkchop.ppatches.util.asm.cp;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.util.Set;
import java.util.TreeSet;

import static net.daporkchop.ppatches.util.asm.cp.ConstantPoolConstants.*;
import static org.objectweb.asm.Opcodes.ASM5;

/**
 * @author DaPorkchop_
 */
public final class ConstantPoolIndex {
    /**
     * Flag to enable extracting classes referenced in field/method descriptors as ordinary referenced classes.
     */
    public static final int INCLUDE_DESCRIPTORS_IN_REFERENCED_CLASSES = 1;

    /**
     * Flag to enable extracting classes referenced in annotations as ordinary referenced classes.
     */
    public static final int INCLUDE_ANNOTATIONS_IN_REFERENCED_CLASSES = 2;
    
    /**
     * Flag to enable extracting constant primitive values.
     */
    public static final int INCLUDE_CONSTANT_PRIMITIVES = 4;

    private final Set<String> referencedStrings = new TreeSet<>();
    private final Set<String> referencedClasses = new TreeSet<>();
    private final Set<String> referencedMethodTypes = new TreeSet<>();

    private final Set<FieldOrMethodRef> referencedFields = new TreeSet<>();
    private final Set<FieldOrMethodRef> referencedMethods = new TreeSet<>();

    //TODO: maybe use fastutil collections for these (they're accessed from the root transformer which results in a ClassCircularityError during startup)
    private final Set<Integer> referencedInts;
    private final Set<Integer> referencedFloats;

    private final Set<Long> referencedLongs;
    private final Set<Long> referencedDoubles;

    public ConstantPoolIndex(ClassReader reader, int flags) {
        if ((flags & INCLUDE_CONSTANT_PRIMITIVES) != 0) {
            this.referencedInts = new TreeSet<>();
            this.referencedFloats = new TreeSet<>();
            this.referencedLongs = new TreeSet<>();
            this.referencedDoubles = new TreeSet<>();
        } else {
            this.referencedInts = this.referencedFloats = null;
            this.referencedLongs = this.referencedDoubles = null;
        }
        
        char[] buf = new char[reader.getMaxStringLength()];

        @SuppressWarnings("unchecked")
        Set<String>[] referencedStringSets = new Set[MAX_CONSTANT_TAGS];
        referencedStringSets[CONSTANT_String] = this.referencedStrings;
        referencedStringSets[CONSTANT_Class] = this.referencedClasses;
        referencedStringSets[CONSTANT_MethodType] = this.referencedMethodTypes;

        for (int item = 0; item < reader.getItemCount(); item++) { //iterate over all the items in the constant pool
            int index = reader.getItem(item);
            if (index == 0) { //empty item (seems to happen sporadically)
                continue;
            }

            int tag = reader.readByte(index - 1);
            switch (tag) {
                case CONSTANT_Utf8: //this would require reflection to implement, and isn't really worth it since i can't see any meaningful use cases. maybe for the future?
                case CONSTANT_NameAndType: //this isn't really useful by itself, and would just add unnecessary extra overhead
                    break;
                case CONSTANT_String:
                case CONSTANT_Class:
                case CONSTANT_MethodType:
                    referencedStringSets[tag].add(reader.readUTF8(index, buf));
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref: {
                    int nameType = reader.getItem(reader.readUnsignedShort(index + 2));
                    String owner = reader.readClass(index, buf);
                    String name = reader.readUTF8(nameType, buf);
                    String desc = reader.readUTF8(nameType + 2, buf);
                    (tag == CONSTANT_Fieldref ? this.referencedFields : this.referencedMethods).add(new FieldOrMethodRef(owner, name, desc));
                    break;
                }
                case CONSTANT_MethodHandle: //TODO: implement
                case CONSTANT_InvokeDynamic:
                    break;
                case CONSTANT_Integer:
                case CONSTANT_Float:
                    if ((flags & INCLUDE_CONSTANT_PRIMITIVES) != 0) {
                        (tag == CONSTANT_Integer ? this.referencedInts : this.referencedFloats).add(reader.readInt(index));
                    }
                    break;
                case CONSTANT_Long:
                case CONSTANT_Double:
                    if ((flags & INCLUDE_CONSTANT_PRIMITIVES) != 0) {
                        (tag == CONSTANT_Long ? this.referencedLongs : this.referencedDoubles).add(reader.readLong(index));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("encountered constant pool entry with unexpected tag " + tag);
            }
        }

        if ((flags & INCLUDE_DESCRIPTORS_IN_REFERENCED_CLASSES) != 0) {
            this.extractTypesFromDescriptors();
        }

        if ((flags & INCLUDE_ANNOTATIONS_IN_REFERENCED_CLASSES) != 0) {
            extractAnnotationTypes(reader, this.referencedClasses);
        }
    }

    private void extractTypesFromDescriptors() {
        this.referencedMethodTypes.forEach(this::extractTypesFromMethodDescriptor);

        this.referencedFields.forEach(fieldref -> {
            assert this.referencedClasses.contains(fieldref.owner) : fieldref.owner;
            this.extractTypesFromFieldDescriptor(fieldref.desc);
        });

        this.referencedMethods.forEach(methodref -> {
            assert this.referencedClasses.contains(methodref.owner) : methodref.owner;
            this.extractTypesFromMethodDescriptor(methodref.desc);
        });

        //TODO: we'll need a few more
    }

    private void extractTypesFromFieldDescriptor(String desc) {
        addInternalNameToSetIfObjectType(desc, this.referencedClasses);
    }

    private void extractTypesFromMethodDescriptor(String desc) {
        for (Type argumentType : Type.getArgumentTypes(desc)) {
            addInternalNameToSetIfObjectType(argumentType, this.referencedClasses);
        }

        addInternalNameToSetIfObjectType(Type.getReturnType(desc), this.referencedClasses);
    }

    private static void addInternalNameToSetIfObjectType(String desc, Set<String> dst) {
        addInternalNameToSetIfObjectType(Type.getType(desc), dst);
    }

    private static void addInternalNameToSetIfObjectType(Type type, Set<String> dst) {
        switch (type.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                dst.add(type.getInternalName());
        }
    }

    private static void extractAnnotationTypes(ClassReader reader, Set<String> dst) {
        AnnotationVisitor annotationVisitor = new AnnotationVisitor(ASM5) {
            @Override
            public void visit(String name, Object value) {
                if (value instanceof Type) {
                    addInternalNameToSetIfObjectType((Type) value, dst);
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String desc) {
                addInternalNameToSetIfObjectType(desc, dst);
                return this;
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return this;
            }
        };

        MethodVisitor methodVisitor = new MethodVisitor(ASM5) {
            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }
        };

        FieldVisitor fieldVisitor = new FieldVisitor(ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }
        };

        reader.accept(new ClassVisitor(ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
                addInternalNameToSetIfObjectType(desc, dst);
                return annotationVisitor;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                return fieldVisitor;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return methodVisitor;
            }
        }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE); //TODO: the SKIP_CODE flag means we'll never actually end up visiting local variable instructions, but for now that doesn't matter
    }

    /**
     * Checks if this class references a class with the given internal name.
     * <p>
     * By default, this includes classes referenced directly (i.e. using the Java {@code .class} operator), indirectly (as the owner type of any field or method references
     * in the class) or as a caught exception type of any try-catch blocks.
     * <p>
     * If the flag {@link #INCLUDE_DESCRIPTORS_IN_REFERENCED_CLASSES} is set, this will also include any classes referenced as the field type of any field references, or
     * as an argument or return type of the method descriptor of any method references in the class.
     * <p>
     * If the flag {@link #INCLUDE_ANNOTATIONS_IN_REFERENCED_CLASSES} is set, this will also include any classes referenced as the type or value of any annotation in
     * the class.
     *
     * @param internalName the internal name of the class to check for
     * @return {@code true} if the class contains any references to the class with the given internal name
     */
    public boolean referencesClass(String internalName) {
        return this.referencedClasses.contains(internalName);
    }
    
    /**
     * Checks if this class references a field with the given owner, name and descriptor.
     *
     * @param owner the internal name of the owning class
     * @param name the field name
     * @param desc the field descriptor
     * @return {@code true} if the class contains any references to the given field
     */
    public boolean referencesField(String owner, String name, String desc) {
        return this.referencedFields.contains(new FieldOrMethodRef(owner, name, desc));
    }
    
    /**
     * Checks if this class references a method with the given owner, name and descriptor.
     *
     * @param owner the internal name of the owning class
     * @param name the method name
     * @param desc the method descriptor
     * @return {@code true} if the class contains any references to the given method
     */
    public boolean referencesMethod(String owner, String name, String desc) {
        return this.referencedMethods.contains(new FieldOrMethodRef(owner, name, desc));
    }

    /**
     * Checks if this class contains a constant pool entry with the given {@link String} value.
     *
     * @param value the value
     * @return {@code true} if the class contains a constant pool entry with the given {@link String} value
     */
    public boolean referencesString(String value) {
        return this.referencedStrings.contains(value);
    }

    /**
     * Checks if this class contains a constant pool entry with the given {@code int} value.
     *
     * @param value the value
     * @return {@code true} if the class contains a constant pool entry with the given {@code int} value
     * @throws RuntimeException if this {@link ConstantPoolIndex} was not constructed with the {@link #INCLUDE_CONSTANT_PRIMITIVES} flag
     */
    public boolean referencesInt(int value) {
        return this.referencedInts.contains(value);
    }

    /**
     * Checks if this class contains a constant pool entry with the given {@code float} value.
     *
     * @param value the value
     * @return {@code true} if the class contains a constant pool entry with the given {@code float} value
     * @throws RuntimeException if this {@link ConstantPoolIndex} was not constructed with the {@link #INCLUDE_CONSTANT_PRIMITIVES} flag
     */
    public boolean referencesFloat(float value) {
        return this.referencedFloats.contains(Float.floatToRawIntBits(value));
    }

    /**
     * Checks if this class contains a constant pool entry with the given {@code long} value.
     *
     * @param value the value
     * @return {@code true} if the class contains a constant pool entry with the given {@code long} value
     * @throws RuntimeException if this {@link ConstantPoolIndex} was not constructed with the {@link #INCLUDE_CONSTANT_PRIMITIVES} flag
     */
    public boolean referencesLong(long value) {
        return this.referencedLongs.contains(value);
    }

    /**
     * Checks if this class contains a constant pool entry with the given {@code double} value.
     *
     * @param value the value
     * @return {@code true} if the class contains a constant pool entry with the given {@code double} value
     * @throws RuntimeException if this {@link ConstantPoolIndex} was not constructed with the {@link #INCLUDE_CONSTANT_PRIMITIVES} flag
     */
    public boolean referencesDouble(double value) {
        return this.referencedDoubles.contains(Double.doubleToRawLongBits(value));
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode(cacheStrategy = EqualsAndHashCode.CacheStrategy.LAZY)
    private static final class FieldOrMethodRef implements Comparable<FieldOrMethodRef> {
        public final String owner;
        public final String name;
        public final String desc;

        @Override
        public String toString() {
            return 'L' + this.owner + ';' + this.name + ':' + this.desc;
        }

        @Override
        public int compareTo(FieldOrMethodRef o) {
            int d = this.owner.compareTo(o.owner);
            if (d == 0) {
                d = this.name.compareTo(o.name);
                if (d == 0) {
                    d = this.desc.compareTo(o.desc);
                }
            }
            return d;
        }
    }
}
