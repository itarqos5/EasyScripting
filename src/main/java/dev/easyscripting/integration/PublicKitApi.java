package dev.easyscripting.integration;

import java.lang.reflect.*;
import java.util.*;

/** Fixed public kit-provider calls only. No private access, dynamic method input or NMS. */
public final class PublicKitApi {
  private PublicKitApi() {}

  public static Object call(Object target, String name, Object... args) {
    Class<?> type = target instanceof Class<?> c ? c : target.getClass();
    var candidates =
        Arrays.stream(type.getMethods())
            .filter(m -> m.getName().equals(name) && matches(m.getParameterTypes(), args))
            .filter(m -> !(target instanceof Class<?>) || Modifier.isStatic(m.getModifiers()))
            .toList();
    if (candidates.size() != 1) throw unsupported(type, name);
    try {
      return candidates.getFirst().invoke(target instanceof Class<?> ? null : target, args);
    } catch (ReflectiveOperationException error) {
      throw failure(type, name, error);
    }
  }

  public static Object construct(Class<?> type, Object... args) {
    var candidates =
        Arrays.stream(type.getConstructors())
            .filter(c -> matches(c.getParameterTypes(), args))
            .toList();
    if (candidates.size() != 1) throw unsupported(type, "constructor");
    try {
      return candidates.getFirst().newInstance(args);
    } catch (ReflectiveOperationException error) {
      throw failure(type, "constructor", error);
    }
  }

  public static Class<?> type(Object provider, String name) {
    try {
      return Class.forName(name, true, provider.getClass().getClassLoader());
    } catch (ClassNotFoundException error) {
      throw unsupported(provider.getClass(), name);
    }
  }

  private static boolean matches(Class<?>[] types, Object[] args) {
    if (types.length != args.length) return false;
    for (int i = 0; i < types.length; i++) {
      Class<?> type =
          types[i] == int.class
              ? Integer.class
              : types[i] == boolean.class ? Boolean.class : types[i];
      if (args[i] == null ? types[i].isPrimitive() : !type.isInstance(args[i])) return false;
    }
    return true;
  }

  private static IllegalArgumentException unsupported(Class<?> type, String method) {
    return new IllegalArgumentException(
        "Unsupported kit-provider API: "
            + type.getSimpleName()
            + "."
            + method
            + ". Update the provider or claim the kit yourself and use Import inventory.");
  }

  private static IllegalArgumentException failure(
      Class<?> type, String method, ReflectiveOperationException error) {
    Throwable cause =
        error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
    return new IllegalArgumentException(
        "Kit import failed in " + type.getSimpleName() + "." + method + ": " + cause.getMessage(),
        cause);
  }
}
