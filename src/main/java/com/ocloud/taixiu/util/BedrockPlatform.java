package com.ocloud.taixiu.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class BedrockPlatform {
    private static final String GEYSER_PLUGIN = "Geyser-Spigot";
    private static final String FLOODGATE_PLUGIN = "floodgate";
    private static final String GEYSER_API = "org.geysermc.geyser.api.GeyserApi";
    private static final String FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi";
    private static final ThreadLocal<String> LAST_FORM_FAILURE = new ThreadLocal<>();

    private BedrockPlatform() {
    }

    public static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        return invokeNamed(target, null, name, arguments);
    }

    public static boolean isBedrock(Player player) {
        return player != null && isBedrock(player.getUniqueId());
    }

    public static boolean isBedrock(UUID playerId) {
        if (playerId == null) return false;

        try {
            ApiHandle floodgate = floodgateApi();
            if (detectsFloodgate(floodgate.instance(), floodgate.type(), playerId)) return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }

        try {
            ApiHandle geyser = geyserApi();
            if (detectsGeyser(geyser.instance(), geyser.type(), playerId)) return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }

        return isFloodgateUuid(playerId);
    }

    public static Class<?> formClass(String className) throws ClassNotFoundException {
        ClassNotFoundException last = null;

        for (String pluginName : new String[]{FLOODGATE_PLUGIN, GEYSER_PLUGIN}) {
            try {
                return loadFromPlugin(className, pluginName);
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            if (last != null) ex.addSuppressed(last);
            throw ex;
        }
    }

    public static Class<?> formClass(String className, UUID playerId) throws ClassNotFoundException {
        if (playerId != null) {
            try {
                ApiHandle floodgate = floodgateApi();
                if (detectsFloodgate(floodgate.instance(), floodgate.type(), playerId)) {
                    return loadFromPlugin(className, FLOODGATE_PLUGIN);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            try {
                ApiHandle geyser = geyserApi();
                if (detectsGeyser(geyser.instance(), geyser.type(), playerId)) {
                    return loadFromPlugin(className, GEYSER_PLUGIN);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return formClass(className);
    }

    public static boolean sendForm(UUID playerId, Object form) {
        if (playerId == null || form == null) return false;
        List<String> failures = new ArrayList<>();

        try {
            ApiHandle floodgate = floodgateApi();
            if (invokeCompatibleFormSender(floodgate.instance(), floodgate.type(), playerId, form)) {
                LAST_FORM_FAILURE.remove();
                return true;
            }
            Object bedrockPlayer = invokeNamed(floodgate.instance(), floodgate.type(), "getPlayer", playerId);
            if (bedrockPlayer != null && invokeCompatibleFormSender(bedrockPlayer, null, null, form)) {
                LAST_FORM_FAILURE.remove();
                return true;
            }
            failures.add("Floodgate không chấp nhận kiểu form " + form.getClass().getName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            failures.add("Floodgate: " + describe(ex));
        }

        try {
            ApiHandle geyser = geyserApi();
            if (invokeCompatibleFormSender(geyser.instance(), geyser.type(), playerId, form)) {
                LAST_FORM_FAILURE.remove();
                return true;
            }
            failures.add("Geyser không chấp nhận kiểu form " + form.getClass().getName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            failures.add("Geyser: " + describe(ex));
        }
        LAST_FORM_FAILURE.set(String.join("; ", failures));
        return false;
    }

    public static String takeLastFormFailure() {
        String detail = LAST_FORM_FAILURE.get();
        LAST_FORM_FAILURE.remove();
        return detail == null || detail.isBlank() ? "không có API tương thích" : detail;
    }

    public static String firstFormInput(Object response) {
        if (response == null) return "";
        for (String method : new String[]{"asInput", "getInput"}) {
            try {
                Object value = invokeNamed(response, null, method, 0);
                if (value instanceof String text) return text;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        try {
            Object value = invokeNamed(response, null, "asInput");
            if (value instanceof String text) return text;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public static void configureSimpleButtons(Object builder, List<SimpleButton> buttons)
            throws ReflectiveOperationException {
        if (builder == null) throw new IllegalArgumentException("builder");
        List<SimpleButton> safeButtons = buttons == null ? List.of() : List.copyOf(buttons);
        ClassLoader loader = builder.getClass().getClassLoader();

        for (SimpleButton button : safeButtons) {
            boolean addedWithImage = false;
            if (button.imagePath() != null && !button.imagePath().isBlank()) {
                try {
                    Class<?> imageType = Class.forName(
                            "org.geysermc.cumulus.util.FormImage$Type", true, loader);
                    String configuredType = button.imageType() == null ? "" : button.imageType().trim();
                    String resolvedType = configuredType.isEmpty()
                            ? inferImageType(button.imagePath())
                            : configuredType.toUpperCase(java.util.Locale.ROOT);
                    Object type = Enum.valueOf((Class<? extends Enum>) imageType.asSubclass(Enum.class), resolvedType);
                    invokeNamed(builder, null, "button", button.text(), type, button.imagePath());
                    addedWithImage = true;
                } catch (ClassNotFoundException | NoSuchMethodException | IllegalArgumentException | LinkageError ignored) {

                }
            }
            if (!addedWithImage) invokeNamed(builder, null, "button", button.text());
        }

        java.util.concurrent.atomic.AtomicBoolean used = new java.util.concurrent.atomic.AtomicBoolean();
        Consumer<Object> resultHandler = response -> {
            try {
                Object value = invokeNamedIfPresent(response, null, "clickedButtonId");
                if (!(value instanceof Number)) {
                    value = invokeNamedIfPresent(response, null, "getClickedButtonId");
                    if (!(value instanceof Number)) return;
                }
                Number number = (Number) value;
                int index = number.intValue();
                if (index >= 0 && index < safeButtons.size() && used.compareAndSet(false, true)) {
                    Runnable action = safeButtons.get(index).action();
                    if (action != null) action.run();
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        };
        invokeNamed(builder, null, "validResultHandler", resultHandler);
    }

    static boolean detectsGeyser(Object api, UUID playerId) throws ReflectiveOperationException {
        return detectsGeyser(api, null, playerId);
    }

    static boolean detectsFloodgate(Object api, UUID playerId) throws ReflectiveOperationException {
        return detectsFloodgate(api, null, playerId);
    }

    static boolean isFloodgateUuid(UUID playerId) {
        return playerId != null && playerId.getMostSignificantBits() == 0L;
    }

    public static boolean invokeCompatibleFormSender(Object sender, UUID playerId, Object form)
            throws ReflectiveOperationException {
        return invokeCompatibleFormSender(sender, null, playerId, form);
    }

    private static boolean detectsGeyser(Object api, Class<?> apiType, UUID playerId)
            throws ReflectiveOperationException {
        if (api == null || playerId == null) return false;
        Object direct = invokeNamedIfPresent(api, apiType, "isBedrockPlayer", playerId);
        if (Boolean.TRUE.equals(direct)) return true;
        return present(invokeNamedIfPresent(api, apiType, "connectionByUuid", playerId));
    }

    private static boolean detectsFloodgate(Object api, Class<?> apiType, UUID playerId)
            throws ReflectiveOperationException {
        if (api == null || playerId == null) return false;
        Object direct = invokeNamedIfPresent(api, apiType, "isFloodgatePlayer", playerId);
        if (Boolean.TRUE.equals(direct)) return true;
        return present(invokeNamedIfPresent(api, apiType, "getPlayer", playerId));
    }

    private static boolean present(Object value) {
        if (value instanceof Optional<?> optional) return optional.isPresent();
        return value != null && !Boolean.FALSE.equals(value);
    }

    private static boolean invokeCompatibleFormSender(Object sender, Class<?> publicType,
                                                       UUID playerId, Object form)
            throws ReflectiveOperationException {
        if (sender == null || form == null) return false;
        int parameterCount = playerId == null ? 1 : 2;
        for (Method method : methods(sender, publicType)) {
            if (!method.getName().equals("sendForm") || method.getParameterCount() != parameterCount) continue;
            Class<?>[] types = method.getParameterTypes();
            Object[] arguments;
            int formIndex;
            if (playerId == null) {
                formIndex = 0;
                arguments = new Object[]{form};
            } else {
                if (!types[0].isAssignableFrom(playerId.getClass())) continue;
                formIndex = 1;
                arguments = new Object[]{playerId, form};
            }
            if (!types[formIndex].isAssignableFrom(form.getClass())) continue;
            Object result = invokeMethod(method, sender, arguments);
            if (!(result instanceof Boolean accepted) || accepted) return true;
        }
        return false;
    }

    private static Object invokeNamedIfPresent(Object target, Class<?> publicType, String name, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return invokeNamed(target, publicType, name, arguments);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invokeNamed(Object target, Class<?> publicType, String name, Object... arguments)
            throws ReflectiveOperationException {
        for (Method method : methods(target, publicType)) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < types.length; index++) {
                if (arguments[index] != null && !wrap(types[index]).isAssignableFrom(arguments[index].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) return invokeMethod(method, target, arguments);
        }
        throw new NoSuchMethodException(name);
    }

    private static Set<Method> methods(Object target, Class<?> publicType) {
        Set<Method> methods = new LinkedHashSet<>();
        if (publicType != null) {
            for (Method method : publicType.getMethods()) methods.add(method);
        }
        for (Class<?> type : publicInterfaces(target.getClass())) {
            for (Method method : type.getMethods()) methods.add(method);
        }
        if (Modifier.isPublic(target.getClass().getModifiers())) {
            for (Method method : target.getClass().getMethods()) methods.add(method);
        }
        return methods;
    }

    private static Set<Class<?>> publicInterfaces(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            collectInterfaces(current, result);
        }
        return result;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> result) {
        for (Class<?> candidate : type.getInterfaces()) {
            if (Modifier.isPublic(candidate.getModifiers())) result.add(candidate);
            collectInterfaces(candidate, result);
        }
    }

    private static Object invokeMethod(Method method, Object target, Object[] arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw ex;
        }
    }

    private static ApiHandle geyserApi() throws ReflectiveOperationException {
        Class<?> type = loadFromPlugin(GEYSER_API, GEYSER_PLUGIN);
        return new ApiHandle(type.getMethod("api").invoke(null), type);
    }

    private static ApiHandle floodgateApi() throws ReflectiveOperationException {
        Class<?> type = loadFromPlugin(FLOODGATE_API, FLOODGATE_PLUGIN);
        return new ApiHandle(type.getMethod("getInstance").invoke(null), type);
    }

    private static Class<?> loadFromPlugin(String className, String pluginName) throws ClassNotFoundException {
        Plugin owner = Bukkit.getPluginManager().getPlugin(pluginName);
        if (owner == null || !owner.isEnabled()) throw new ClassNotFoundException(pluginName + " is unavailable");
        return Class.forName(className, true, owner.getClass().getClassLoader());
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static String describe(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record ApiHandle(Object instance, Class<?> type) {
    }

    public static String inferImageType(String imagePath) {
        if (imagePath == null) return "PATH";
        String normalized = imagePath.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("https://") || normalized.startsWith("http://") ? "URL" : "PATH";
    }

    public record SimpleButton(String text, String imageType, String imagePath, Runnable action) {
        public SimpleButton(String text, String imagePath, Runnable action) {
            this(text, inferImageType(imagePath), imagePath, action);
        }

        public SimpleButton(String text, Runnable action) {
            this(text, "PATH", "", action);
        }
    }
}
