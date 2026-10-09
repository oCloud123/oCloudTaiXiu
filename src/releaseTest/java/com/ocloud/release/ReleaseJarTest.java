package com.ocloud.release;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseJarTest {
    @TempDir Path directory;
    private final Path artifact = Path.of(System.getProperty("release.jar"));

    @Test void releaseContainsPluginDependenciesAndEditableConfig() throws Exception {
        try (var jar = new JarFile(artifact.toFile())) {
            assertEquals("mojang", jar.getManifest().getMainAttributes().getValue("paperweight-mappings-namespace"));
            String descriptor = new String(jar.getInputStream(jar.getJarEntry("plugin.yml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.contains("main: com.ocloud.taixiu.OCloudTaiXiuPlugin"));
            assertNotNull(jar.getJarEntry("com/ocloud/taixiu/OCloudTaiXiuPlugin.class"));
            assertNotNull(jar.getJarEntry("com/ocloud/taixiu/game/NativeTaiXiuUi.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("r/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("net/milkbowl/")));
            for (String config : List.of("taixiu.yml", "gui.yml", "bedrock/taixiu.yml")) {
                assertArrayEquals(Files.readAllBytes(Path.of("src/main/resources", config)),
                        jar.getInputStream(jar.getJarEntry(config)).readAllBytes());
            }
            assertNotNull(jar.getJarEntry("org/sqlite/JDBC.class"));
            assertNotNull(jar.getJarEntry("META-INF/services/java.sql.Driver"));
        }
    }

    private URLClassLoader jarLoader() throws Exception {
        return new URLClassLoader(new java.net.URL[] {artifact.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("com.ocloud.taixiu.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
    }

    public static final class Wallet implements Economy {
        double balance = 1_000_000;
        @Override public double getBalance(OfflinePlayer player) { return balance; }
        @Override public boolean has(OfflinePlayer player, double amount) { return balance >= amount; }
        @Override public boolean withdrawPlayer(OfflinePlayer player, double amount) {
            if (!has(player, amount)) return false;
            balance -= amount; return true;
        }
        @Override public boolean depositPlayer(OfflinePlayer player, double amount) { balance += amount; return true; }
    }

    @Test void pluginStartsPlacesAndPaysBetsCancelsRefundsAndForcesTriples() throws Exception {
        try (var jar = new JarFile(artifact.toFile());
             var loader = jarLoader();
             var bukkit = mockStatic(Bukkit.class)) {
            String descriptor = new String(jar.getInputStream(jar.getJarEntry("plugin.yml")).readAllBytes(), StandardCharsets.UTF_8);
            String main = descriptor.lines().filter(line -> line.startsWith("main:")).findFirst().orElseThrow().substring(5).trim();
            Class<? extends JavaPlugin> pluginType = Class.forName(main, true, loader).asSubclass(JavaPlugin.class);
            JavaPlugin plugin = mock(pluginType, CALLS_REAL_METHODS);
            Server server = mock(Server.class);
            PluginManager manager = mock(PluginManager.class);
            ServicesManager services = mock(ServicesManager.class);
            PluginCommand command = mock(PluginCommand.class);
            List<java.util.logging.LogRecord> logs = new ArrayList<>();
            var logger = java.util.logging.Logger.getAnonymousLogger();
            logger.setUseParentHandlers(false);
            logger.addHandler(new java.util.logging.Handler() {
                @Override public void publish(java.util.logging.LogRecord record) { logs.add(record); }
                @Override public void flush() {}
                @Override public void close() {}
            });
            doReturn(server).when(plugin).getServer();
            doReturn(directory.toFile()).when(plugin).getDataFolder();
            doReturn(logger).when(plugin).getLogger();
            doReturn(new PluginDescriptionFile("oCloudTaiXiu", "1.0.0", main)).when(plugin).getPluginMeta();
            doReturn(true).when(plugin).isEnabled();
            doReturn(command).when(plugin).getCommand("taixiu");
            when(server.getPluginManager()).thenReturn(manager);
            when(server.getServicesManager()).thenReturn(services);
            doAnswer(call -> jar.getInputStream(jar.getJarEntry(call.getArgument(0, String.class))))
                    .when(plugin).getResource(anyString());
            doAnswer(call -> {
                String name = call.getArgument(0);
                Path destination = directory.resolve(name);
                Files.createDirectories(destination.getParent());
                try (var input = jar.getInputStream(jar.getJarEntry(name))) { Files.copy(input, destination); }
                return null;
            }).when(plugin).saveResource(anyString(), eq(false));
            Files.writeString(directory.resolve("taixiu.yml"), "round:\n  betting-seconds: 10\n");
            Wallet wallet = new Wallet();
            RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
            when(registration.getProvider()).thenReturn(wallet);
            when(services.getRegistration(Economy.class)).thenReturn(registration);

            GlobalRegionScheduler global = mock(GlobalRegionScheduler.class);
            List<Consumer<ScheduledTask>> timers = new ArrayList<>();
            when(global.runAtFixedRate(any(), any(), anyLong(), anyLong())).thenAnswer(call -> {
                timers.add(call.getArgument(1)); return mock(ScheduledTask.class);
            });
            ArrayDeque<Consumer<ScheduledTask>> delayed = new ArrayDeque<>();
            when(global.runDelayed(any(), any(), anyLong())).thenAnswer(call -> {
                delayed.add(call.getArgument(1)); return mock(ScheduledTask.class);
            });
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(global).execute(any(), any());
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());

            Player player = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn("release-player");
            when(player.isOnline()).thenReturn(true);
            EntityScheduler entity = mock(EntityScheduler.class);
            when(player.getScheduler()).thenReturn(entity);
            when(entity.run(any(), any(), any())).thenAnswer(call -> {
                ScheduledTask scheduled = mock(ScheduledTask.class);
                call.getArgument(1, Consumer.class).accept(scheduled); return scheduled;
            });
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            bukkit.when(() -> Bukkit.getOfflinePlayer(id)).thenReturn(player);
            CommandSender admin = mock(CommandSender.class);
            when(admin.hasPermission(anyString())).thenReturn(true);
            when(admin.getName()).thenReturn("release-admin");

            try {
                plugin.onEnable();
                verify(manager, never()).disablePlugin(plugin);
                assertEquals(1, timers.size(), "Main game ticker was not registered");
                var captured = ArgumentCaptor.forClass(CommandExecutor.class);
                verify(command).setExecutor(captured.capture());
                CommandExecutor executor = captured.getValue();
                var listeners = ArgumentCaptor.forClass(Listener.class);
                verify(manager, atLeast(2)).registerEvents(listeners.capture(), eq(plugin));
                assertTrue(listeners.getAllValues().stream().anyMatch(listener -> Arrays.stream(listener.getClass().getMethods())
                        .filter(method -> method.isAnnotationPresent(EventHandler.class)).count() >= 6), "Bukkit event annotations lost");
                Consumer<ScheduledTask> tick = timers.getFirst();
                assertTrue(executor.onCommand(admin, command, "tx", new String[] {"setnext", "tai"}));
                for (int count = 0; count < 3; count++) tick.accept(null);
                executor.onCommand(player, command, "tx", new String[] {"cuoc", "tai", "100k"});
                assertEquals(900_000, wallet.balance);
                executor.onCommand(player, command, "tx", new String[] {"cuoc", "xiu", "100k"});
                assertEquals(900_000, wallet.balance, "Opposite-side bet must be rejected");
                for (int count = 0; count < 10; count++) tick.accept(null);
                int frames = 0;
                while (!delayed.isEmpty()) { assertTrue(++frames < 100); delayed.removeFirst().accept(null); }
                assertTrue(frames >= 48, "Rolling animation did not settle");
                assertEquals(1_090_000, wallet.balance, "Payout and 10% tax must match the game rules");
                assertEquals("WON", lastBetStatus());
                executor.onCommand(admin, command, "tx", new String[] {"resume"});
                for (int count = 0; count < 60 && lastRoundStatus().equals("SETTLED"); count++) tick.accept(null);
                executor.onCommand(player, command, "tx", new String[] {"cuoc", "xiu", "100k"});
                assertEquals(990_000, wallet.balance);
                executor.onCommand(admin, command, "tx", new String[] {"cancel"});
                assertEquals(1_090_000, wallet.balance);
                assertEquals("REFUNDED", lastBetStatus());
                executor.onCommand(admin, command, "tx", new String[] {"setnext", "boba"});
                for (int count = 0; count < 60 && !lastRoundStatus().equals("OPEN"); count++) tick.accept(null);
                executor.onCommand(player, command, "tx", new String[] {"cuoc", "tai", "100k"});
                for (int count = 0; count < 10; count++) tick.accept(null);
                frames = 0;
                while (!delayed.isEmpty()) { assertTrue(++frames < 100); delayed.removeFirst().accept(null); }
                assertEquals(990_000, wallet.balance);
                assertEquals("LOST", lastBetStatus());
                try (var db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("data/taixiu.db"));
                     var statement = db.createStatement();
                     var result = statement.executeQuery("SELECT dice_1,dice_2,dice_3,result FROM tx_rounds ORDER BY id DESC LIMIT 1")) {
                    assertTrue(result.next());
                    assertEquals(result.getInt(1), result.getInt(2));
                    assertEquals(result.getInt(2), result.getInt(3));
                    assertEquals("TRIPLE", result.getString(4));
                }
            } finally { plugin.onDisable(); }
            for (String action : List.of("Starting", "Stopping")) {
                var banners = logs.stream().filter(record -> record.getMessage().contains("┌ " + action + " oCloudTaiXiu v1.0.0")).toList();
                assertEquals(1, banners.size(), "Lifecycle banner must be one combined log record");
                String message = banners.getFirst().getMessage();
                assertTrue(message.startsWith("\n"), "Logo must align below the logger prefix");
                assertTrue(message.contains("oCloud Studio - TaiXiu"));
                assertTrue(message.contains("https://discord.gg/WVNrYzQfhX"));
                assertTrue(message.contains("└ ✓ Done in "));
                assertFalse(message.toLowerCase(Locale.ROOT).contains("license"));
                assertNull(banners.getFirst().getThrown());
            }
            Path preview = Path.of("build/verification/banner-preview.txt");
            Files.createDirectories(preview.getParent());
            String previewText = String.join("\n\n", logs.stream()
                    .filter(record -> record.getMessage().contains("oCloud Studio - TaiXiu"))
                    .map(java.util.logging.LogRecord::getMessage).toList());
            Files.writeString(preview, previewText.replaceAll("\u001B\\[[0-9;]*m", ""));
        }
    }

    private String lastBetStatus() throws Exception { return query("SELECT status FROM tx_bets ORDER BY round_id DESC LIMIT 1"); }
    private String lastRoundStatus() throws Exception { return query("SELECT status FROM tx_rounds ORDER BY id DESC LIMIT 1"); }
    private String query(String sql) throws Exception {
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("data/taixiu.db"));
             var statement = db.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getString(1);
        }
    }
}
