package net.daporkchop.ppatches.modules.optifine.optimizeReflector;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.minecraftforge.common.MinecraftForge;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class OptimizeReflectorBootstrap {
    @SneakyThrows
    public static CallSite bootstrapSimple(MethodHandles.Lookup lookup, String name, MethodType type,
                                           Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getValueMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Object value = getValueMethod.invoke(reflector);
        return new ConstantCallSite(MethodHandles.constant(type.returnType(), value).asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrapClass_isInstance(MethodHandles.Lookup lookup, String name, MethodType type,
                                                     Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetClassMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Object targetClass = getTargetClassMethod.invoke(reflector);
        MethodHandle handle = targetClass == null
                ? MethodHandles.constant(boolean.class, false) //clazz == null means that the target class doesn't exist!
                : lookup.bind(targetClass, name, type);
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrapField_getValue(MethodHandles.Lookup lookup, String name, MethodType type,
                                                   Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetFieldMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Field targetField = (Field) getTargetFieldMethod.invoke(reflector);
        MethodHandle handle = targetField == null
                ? MethodHandles.constant(Object.class, null)
                : lookup.unreflectGetter(targetField);
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrapField_setValue(MethodHandles.Lookup lookup, String name, MethodType type,
                                                   Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetFieldMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Field targetField = (Field) getTargetFieldMethod.invoke(reflector);
        MethodHandle handle = targetField == null
                ? MethodHandles.constant(Object.class, null)
                : lookup.unreflectSetter(targetField);
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrap_getFieldValue(MethodHandles.Lookup lookup, String name, MethodType type,
                                                   Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetFieldMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Field targetField = (Field) getTargetFieldMethod.invoke(reflector);
        MethodHandle handle;
        if (targetField == null) {
            if (type.returnType().isPrimitive()) { //all getFieldValue* methods with a primitive return type take a default argument
                handle = MethodHandles.permuteArguments(MethodHandles.identity(type.returnType()), type, type.parameterCount() - 1);
            } else { //all getFieldValue* methods with an Object return type return null by default
                handle = MethodHandles.dropArguments(MethodHandles.constant(type.returnType(), null), 0, type.parameterArray());
            }
        } else {
            handle = lookup.unreflectGetter(targetField);
            if (type.returnType().isPrimitive()) { //all getFieldValue* methods with a primitive return type take a default argument, which needs to be stripped away
                handle = MethodHandles.dropArguments(handle, type.parameterCount() - 1, type.parameterType(type.parameterCount() - 1));
            }
        }
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrap_getFieldValue(MethodHandles.Lookup lookup, String name, MethodType type,
                                                   Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetFieldMethod, MethodHandle getReflectorFieldMethod, int fieldIndex) {
        assert !type.returnType().isPrimitive() : "not a primitive return type: " + name + type;

        Object reflectorFields = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();
        Object reflectorField = getReflectorFieldMethod.invoke(reflectorFields, fieldIndex);

        MethodHandle handle;
        if (reflectorField == null) {
            handle = MethodHandles.dropArguments(MethodHandles.constant(type.returnType(), null), 0, type.parameterArray());
        } else {
            Field targetField = (Field) getTargetFieldMethod.invoke(reflectorField);
            handle = targetField == null
                    ? MethodHandles.dropArguments(MethodHandles.constant(type.returnType(), null), 0, type.parameterArray())
                    : lookup.unreflectGetter(targetField).asType(type);
        }
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrap_setFieldValue(MethodHandles.Lookup lookup, String name, MethodType type,
                                                   Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetFieldMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Field targetField = (Field) getTargetFieldMethod.invoke(reflector);
        MethodHandle handle = targetField == null
                ? MethodHandles.constant(boolean.class, false)
                : MethodHandles.collectArguments(MethodHandles.constant(boolean.class, true), 0, lookup.unreflectSetter(targetField).asType(MethodType.methodType(void.class, type)));
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrap_call(MethodHandles.Lookup lookup, String name, MethodType type,
                                          Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetMethodMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Method targetMethod = (Method) getTargetMethodMethod.invoke(reflector);
        MethodHandle handle;
        if (targetMethod == null) { //the method wasn't found, return the default value
            Class<?> returnType = type.returnType();
            MethodHandle returnDefaultValue;
            if (!returnType.isPrimitive()) {
                returnDefaultValue = MethodHandles.constant(returnType, null);
            } else if (returnType == void.class) {
                returnDefaultValue = lookup.findStatic(OptimizeReflectorBootstrap.class, "empty", MethodType.methodType(void.class));
            } else if (returnType == boolean.class) {
                returnDefaultValue = MethodHandles.constant(returnType, false);
            } else { //this should be able to be automatically converted to the correct type, we use byte so that there can only be widening conversions
                returnDefaultValue = MethodHandles.constant(returnType, (byte) 0);
            }
            handle = MethodHandles.dropArguments(returnDefaultValue, 0, type.parameterArray());
        } else {
            handle = lookup.unreflect(targetMethod);
        }
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrap_newInstance(MethodHandles.Lookup lookup, String name, MethodType type,
                                                 Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetConstructorMethod) {
        Object reflector = lookup.findStaticGetter(reflectorClass, reflectorName, reflectorInstanceClass).invoke();

        Constructor<?> targetConstructor = (Constructor<?>) getTargetConstructorMethod.invoke(reflector);
        MethodHandle handle;
        if (targetConstructor == null) { //the method wasn't found, return the default value
            handle = MethodHandles.dropArguments(MethodHandles.constant(type.returnType(), null), 0, type.parameterArray());
        } else {
            handle = lookup.unreflectConstructor(targetConstructor);
        }
        return new ConstantCallSite(handle.asType(type));
    }

    @SneakyThrows
    public static CallSite bootstrap_postForgeBusEvent(MethodHandles.Lookup lookup, String name, MethodType type,
                                                       Class<?> reflectorClass, String reflectorName, Class<?> reflectorInstanceClass, MethodHandle getTargetConstructorMethod, MethodHandle eventBusPostMethod) {
        MethodHandle newInstance = bootstrap_newInstance(lookup, name, MethodType.methodType(eventBusPostMethod.type().parameterType(1), type), reflectorClass, reflectorName, reflectorInstanceClass, getTargetConstructorMethod).getTarget();
        return new ConstantCallSite(MethodHandles.filterReturnValue(newInstance, eventBusPostMethod.bindTo(MinecraftForge.EVENT_BUS)));
    }

    @SneakyThrows
    public static CallSite bootstrap_postForgeBusEvent(MethodHandles.Lookup lookup, String name, MethodType type,
                                                       MethodHandle eventBusPostMethod) {
        return new ConstantCallSite(eventBusPostMethod.bindTo(MinecraftForge.EVENT_BUS));
    }

    public static void empty() {
        //no-op
    }
}
