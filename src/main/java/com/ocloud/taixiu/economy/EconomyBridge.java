package com.ocloud.taixiu.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;

public class EconomyBridge {
    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";
    private static final String ESSENTIALS_ECONOMY_CLASS = "com.earth2me.essentials.api.Economy";

    private final JavaPlugin plugin;

    public EconomyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return resolveProvider() != null || resolveEssentialsApi() != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (player == null || !validAmount(amount)) return false;

        Object provider = resolveProvider();
        if (provider != null) {
            Object response = invoke(provider, "has", player, amount);
            if (!(response instanceof Boolean) && player.getName() != null) {
                response = invoke(provider, "has", player.getName(), amount);
            }
            if (response instanceof Boolean bool) return bool;
        }

        Boolean essentialsResult = essentialsHas(player, amount);
        return essentialsResult != null && essentialsResult;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (player == null || !validAmount(amount)) return false;

        Object provider = resolveProvider();
        if (provider != null) {
            Object response = invoke(provider, "withdrawPlayer", player, amount);
            if (response == null && player.getName() != null) {
                response = invoke(provider, "withdrawPlayer", player.getName(), amount);
            }
            if (response != null) return responseSuccess(response);
        }

        return withdrawEssentials(player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (player == null || !validAmount(amount)) return false;

        Object provider = resolveProvider();
        if (provider != null) {
            Object response = invoke(provider, "depositPlayer", player, amount);
            if (response == null && player.getName() != null) {
                response = invoke(provider, "depositPlayer", player.getName(), amount);
            }
            if (response != null) return responseSuccess(response);
        }

        return depositEssentials(player, amount);
    }

    public double getBalance(OfflinePlayer player) {
        if (player == null) return 0.0d;

        Object provider = resolveProvider();
        if (provider != null) {
            Object result = invoke(provider, "getBalance", player);
            if (!(result instanceof Number) && player.getName() != null) {
                result = invoke(provider, "getBalance", player.getName());
            }
            if (result instanceof Number number) return finiteOrZero(number.doubleValue());
        }

        Double essentialsBalance = getEssentialsBalance(player);
        return essentialsBalance == null ? 0.0d : finiteOrZero(essentialsBalance);
    }

    public String format(double amount) {
        Object provider = resolveProvider();
        if (provider != null) {
            Object result = invoke(provider, "format", amount);
            if (result instanceof String text && !text.isBlank()) return text;
        }
        return formatFallback(amount);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveProvider() {
        if (plugin == null || plugin.getServer() == null) return null;
        try {
            Class<?> economyClass = Class.forName(VAULT_ECONOMY_CLASS);
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration((Class) economyClass);
            return registration == null ? null : registration.getProvider();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Class<?> resolveEssentialsApi() {
        try {
            return Class.forName(ESSENTIALS_ECONOMY_CLASS);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Double getEssentialsBalance(OfflinePlayer player) {
        Class<?> api = resolveEssentialsApi();
        String name = player.getName();
        if (api == null || name == null || name.isBlank()) return null;

        Object result = invokeStatic(api, "getMoneyExact", name);
        if (!(result instanceof Number)) result = invokeStatic(api, "getMoney", name);
        if (result instanceof Number number) return number.doubleValue();
        return null;
    }

    private Boolean essentialsHas(OfflinePlayer player, double amount) {
        Class<?> api = resolveEssentialsApi();
        String name = player.getName();
        if (api == null || name == null || name.isBlank()) return null;

        BigDecimal decimal = BigDecimal.valueOf(amount);
        Object result = invokeStatic(api, "hasEnough", name, decimal);
        if (!(result instanceof Boolean)) result = invokeStatic(api, "hasEnough", name, amount);
        if (result instanceof Boolean bool) return bool;

        Double balance = getEssentialsBalance(player);
        return balance == null ? null : balance + 0.0000001d >= amount;
    }

    private boolean withdrawEssentials(OfflinePlayer player, double amount) {
        Class<?> api = resolveEssentialsApi();
        String name = player.getName();
        if (api == null || name == null || name.isBlank()) return false;

        Boolean enough = essentialsHas(player, amount);
        if (!Boolean.TRUE.equals(enough)) return false;

        BigDecimal decimal = BigDecimal.valueOf(amount);
        if (invokeStaticVoid(api, "subtract", name, decimal)) return true;

        if (invokeStaticVoid(api, "substract", name, decimal)) return true;
        if (invokeStaticVoid(api, "subtract", name, amount)) return true;
        if (invokeStaticVoid(api, "substract", name, amount)) return true;

        Double balance = getEssentialsBalance(player);
        return balance != null && invokeStaticVoid(api, "setMoney", name,
                BigDecimal.valueOf(balance).subtract(decimal));
    }

    private boolean depositEssentials(OfflinePlayer player, double amount) {
        Class<?> api = resolveEssentialsApi();
        String name = player.getName();
        if (api == null || name == null || name.isBlank()) return false;

        BigDecimal decimal = BigDecimal.valueOf(amount);
        if (invokeStaticVoid(api, "add", name, decimal)) return true;
        if (invokeStaticVoid(api, "add", name, amount)) return true;

        Double balance = getEssentialsBalance(player);
        return balance != null && invokeStaticVoid(api, "setMoney", name,
                BigDecimal.valueOf(balance).add(decimal));
    }

    private Object invoke(Object target, String methodName, Object... args) {
        if (target == null) return null;
        return invokeMethods(target.getClass(), target, methodName, args).value();
    }

    private Object invokeStatic(Class<?> type, String methodName, Object... args) {
        if (type == null) return null;
        return invokeMethods(type, null, methodName, args).value();
    }

    private boolean invokeStaticVoid(Class<?> type, String methodName, Object... args) {
        if (type == null) return false;
        return invokeMethods(type, null, methodName, args).invoked();
    }

    private Invocation invokeMethods(Class<?> type, Object target, String methodName, Object[] args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
            if (!matches(method.getParameterTypes(), args)) continue;
            try {
                return new Invocation(true, method.invoke(target, args));
            } catch (ReflectiveOperationException | RuntimeException ignored) {

            }
        }
        return Invocation.NOT_INVOKED;
    }

    private boolean matches(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null || !wrap(parameterTypes[i]).isAssignableFrom(wrap(arg.getClass()))) return false;
        }
        return true;
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        return type;
    }

    private boolean responseSuccess(Object response) {
        if (response instanceof Boolean bool) return bool;
        Object result = invoke(response, "transactionSuccess");
        return result instanceof Boolean bool && bool;
    }

    private boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0.0d;
    }

    private double finiteOrZero(double amount) {
        return Double.isFinite(amount) ? amount : 0.0d;
    }

    private String formatFallback(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 0.000001d) return String.format("%.0f", amount);
        return String.format("%.2f", amount);
    }

    private record Invocation(boolean invoked, Object value) {
        private static final Invocation NOT_INVOKED = new Invocation(false, null);
    }
}
