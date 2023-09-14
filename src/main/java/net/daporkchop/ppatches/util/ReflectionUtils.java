package net.daporkchop.ppatches.util;

import lombok.experimental.UtilityClass;

import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class ReflectionUtils {
    public static Field getDeclaredFieldExact(Class<?> owner, String name, Class<?> fieldType) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() == fieldType) {
            return field;
        }

        //there are multiple fields with the given name, and getDeclaredField returned the wrong one...
        for (Field declaredField : owner.getDeclaredFields()) {
            if (name.equals(declaredField.getName()) && fieldType == declaredField.getType()) {
                return declaredField;
            }
        }
        throw new NoSuchFieldException(name + " with type " + fieldType);
    }

    public static Method getDeclaredMethodExact(Class<?> owner, String name, MethodType methodType) throws NoSuchMethodException {
        Class<?>[] parameterArray = methodType.parameterArray();

        Method method = owner.getDeclaredMethod(name, parameterArray);
        if (method.getReturnType() == methodType.returnType()) {
            return method;
        }

        //there are multiple methods with the given name and parameter types, and getDeclaredMethod returned the wrong one...
        for (Method declaredMethod : owner.getDeclaredMethods()) {
            if (name.equals(declaredMethod.getName()) && methodType.returnType() == declaredMethod.getReturnType() && Arrays.equals(parameterArray, declaredMethod.getParameterTypes())) {
                return declaredMethod;
            }
        }
        throw new NoSuchMethodException(name + methodType);
    }
}
