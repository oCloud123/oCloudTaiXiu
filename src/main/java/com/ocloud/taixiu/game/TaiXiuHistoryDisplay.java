package com.ocloud.taixiu.game;

import com.ocloud.taixiu.util.FoliaScheduler;
import com.ocloud.taixiu.util.PluginSound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TaiXiuHistoryDisplay {
    private static final int MAX_VISIBLE_ROUNDS = 10;
    private static final float BOARD_WIDTH = 4.8f;
    private static final float BOARD_HEIGHT = 2.75f;
    private static final float CHART_WIDTH = 3.95f;
    private static final float CHART_HEIGHT = 1.42f;

    private final JavaPlugin plugin;
    private final TaiXiuService service;
    private final CurrentViewRegistry<View> views = new CurrentViewRegistry<>();

    TaiXiuHistoryDisplay(JavaPlugin plugin, TaiXiuService service) {
        this.plugin = plugin;
        this.service = service;
    }

    void open(Player player) {
        close(player.getUniqueId(), false);
        List<TaiXiuStore.RoundHistory> completeHistory = service.history();
        if (completeHistory.isEmpty()) {
            player.sendMessage(TaiXiuService.component("&#F6C453Chưa có phiên Tài Xỉu nào để dựng cầu."));
            return;
        }
        int from = Math.max(0, completeHistory.size() - MAX_VISIBLE_ROUNDS);
        List<TaiXiuStore.RoundHistory> history = List.copyOf(completeHistory.subList(from, completeHistory.size()));
        FoliaScheduler.runForPlayer(plugin, player, () -> spawn(player, history));
    }

    private void spawn(Player player, List<TaiXiuStore.RoundHistory> history) {
        if (!player.isOnline()) return;
        TaiXiuDisplayFrame.Frame frame = TaiXiuDisplayFrame.fromEye(player.getEyeLocation());
        Location base = frame.anchor();
        Location viewerOrigin = player.getLocation().clone();
        Vector forward = frame.forward();
        Vector right = frame.right();
        Vector up = frame.up();
        Quaternionf boardRotation = frame.rotation();

        TaiXiuConfig config = service.config();
        Vector center = forward.clone().multiply(config.historyDisplayDistance)
                .add(up.clone().multiply(config.historyDisplayVerticalOffset));
        Vector bottomLeft = center.clone()
                .subtract(right.clone().multiply(BOARD_WIDTH / 2.0d))
                .subtract(up.clone().multiply(BOARD_HEIGHT / 2.0d));
        Vector chartOrigin = bottomLeft.clone().add(right.clone().multiply(0.62d)).add(up.clone().multiply(0.52d));

        View view = new View(player.getUniqueId(), viewerOrigin, System.currentTimeMillis(), history);
        addBlock(player, view, base, Material.BLACK_STAINED_GLASS,
                bottomLeft.clone().add(forward.clone().multiply(0.10d)), boardRotation,
                new Vector3f(BOARD_WIDTH, BOARD_HEIGHT, 0.08f), 0);
        addFrame(player, view, base, bottomLeft, right, up, forward, boardRotation);
        addGridAndAxis(player, view, base, chartOrigin, right, up, forward, boardRotation);
        addHeader(player, view, base, center, right, up, forward, boardRotation);
        addHistory(player, view, base, history, chartOrigin, right, up, forward, boardRotation);

        if (view.pieces.isEmpty()) return;
        view.primary = view.pieces.get(0).display;
        View displaced = views.put(player.getUniqueId(), view);
        if (displaced != null && displaced != view) cleanup(displaced, false);
        reveal(view, 0);
        PluginSound.play(player, service.config().historyOpenSound,
                service.config().soundVolume * 0.7f, 0.9f);
        view.task = FoliaScheduler.runTimerForPlayer(plugin, player, () -> update(player, view),
                service.config().historyAnimationTicksPerPoint,
                service.config().historyAnimationTicksPerPoint);
    }

    private void addFrame(Player player, View view, Location base, Vector bottomLeft, Vector right, Vector up,
                          Vector forward, Quaternionf rotation) {
        Vector layer = forward.clone().multiply(-0.015d);
        Material frame = Material.YELLOW_CONCRETE;
        addBlock(player, view, base, frame, bottomLeft.clone().add(layer), rotation,
                new Vector3f(BOARD_WIDTH, 0.055f, 0.055f), 0);
        addBlock(player, view, base, frame, bottomLeft.clone().add(up.clone().multiply(BOARD_HEIGHT)).add(layer), rotation,
                new Vector3f(BOARD_WIDTH, 0.055f, 0.055f), 0);
        addBlock(player, view, base, frame, bottomLeft.clone().add(layer), rotation,
                new Vector3f(0.055f, BOARD_HEIGHT, 0.055f), 0);
        addBlock(player, view, base, frame, bottomLeft.clone().add(right.clone().multiply(BOARD_WIDTH)).add(layer), rotation,
                new Vector3f(0.055f, BOARD_HEIGHT, 0.055f), 0);

        Vector accentStart = bottomLeft.clone().add(up.clone().multiply(BOARD_HEIGHT - 0.34d)).add(layer);
        addBlock(player, view, base, Material.ORANGE_CONCRETE, accentStart, rotation,
                new Vector3f(BOARD_WIDTH, 0.035f, 0.065f), 0);
    }

    private void addGridAndAxis(Player player, View view, Location base, Vector chartOrigin, Vector right, Vector up,
                                Vector forward, Quaternionf rotation) {
        for (int index = 0; index < 6; index++) {
            double fraction = index / 5.0d;
            Vector line = chartOrigin.clone().add(up.clone().multiply(CHART_HEIGHT * fraction))
                    .add(forward.clone().multiply(0.035d));
            addBlock(player, view, base, Material.GRAY_CONCRETE, line, rotation,
                    new Vector3f(CHART_WIDTH, 0.018f, 0.022f), 0);
            int value = 3 + index * 3;
            Vector label = chartOrigin.clone().subtract(right.clone().multiply(0.27d))
                    .add(up.clone().multiply(CHART_HEIGHT * fraction - 0.025d))
                    .add(forward.clone().multiply(-0.08d));
            addText(player, view, base, Component.text(String.valueOf(value), NamedTextColor.GRAY), label, rotation,
                    new Vector3f(0.42f, 0.42f, 0.42f), 0, false, 80);
        }
        for (int index = 0; index < MAX_VISIBLE_ROUNDS; index++) {
            double fraction = index / (double) (MAX_VISIBLE_ROUNDS - 1);
            Vector line = chartOrigin.clone().add(right.clone().multiply(CHART_WIDTH * fraction))
                    .add(forward.clone().multiply(0.045d));
            addBlock(player, view, base, Material.GRAY_STAINED_GLASS, line, rotation,
                    new Vector3f(0.012f, CHART_HEIGHT, 0.016f), 0);
        }
    }

    private void addHeader(Player player, View view, Location base, Vector center, Vector right, Vector up,
                           Vector forward, Quaternionf rotation) {
        Component title = TaiXiuService.component(service.config().history3dTitle);
        addText(player, view, base, title,
                center.clone().add(up.clone().multiply(BOARD_HEIGHT / 2.0d - 0.18d))
                        .add(forward.clone().multiply(-0.08d)),
                rotation,
                new Vector3f(0.64f, 0.64f, 0.64f), 0, false, 260);

        Component legend = TaiXiuService.component(service.config().history3dLegend);
        addText(player, view, base, legend,
                center.clone().subtract(up.clone().multiply(BOARD_HEIGHT / 2.0d - 0.23d))
                        .add(forward.clone().multiply(-0.09d)),
                rotation,
                new Vector3f(0.46f, 0.46f, 0.46f), 0, false, 360);
    }

    private void addHistory(Player player, View view, Location base, List<TaiXiuStore.RoundHistory> history,
                            Vector chartOrigin, Vector right, Vector up, Vector forward, Quaternionf rotation) {
        Vector previous = null;
        for (int index = 0; index < history.size(); index++) {
            TaiXiuStore.RoundHistory row = history.get(index);
            double x = history.size() == 1 ? CHART_WIDTH / 2.0d
                    : CHART_WIDTH * index / (double) (history.size() - 1);
            double y = CHART_HEIGHT * (Math.max(3, Math.min(18, row.total())) - 3) / 15.0d;
            Vector point = chartOrigin.clone().add(right.clone().multiply(x)).add(up.clone().multiply(y))
                    .add(forward.clone().multiply(-0.075d));
            Material pointMaterial = pointMaterial(row);
            Material lineMaterial = lineMaterial(row);
            int revealAt = index + 1;
            if (previous != null) addConnection(player, view, base, lineMaterial, previous, point, revealAt);

            Vector pointCorner = point.clone().subtract(right.clone().multiply(0.09d)).subtract(up.clone().multiply(0.09d));
            Piece pointPiece = addBlock(player, view, base, pointMaterial, pointCorner,
                    new Quaternionf(rotation),
                    new Vector3f(0.18f, 0.18f, 0.11f), revealAt);
            Component number = Component.text(String.valueOf(row.total()), NamedTextColor.WHITE, TextDecoration.BOLD);
            addText(player, view, base, number,
                    point.clone().add(up.clone().multiply(0.20d)).add(forward.clone().multiply(-0.055d)),
                    rotation,
                    new Vector3f(0.43f, 0.43f, 0.43f), revealAt, true, 70);
            if (index == history.size() - 1) view.latestPoint = pointPiece;
            previous = point;
        }

        TaiXiuStore.RoundHistory newest = history.get(history.size() - 1);
        String resultColor = newest.triple() ? "&#C084FC"
                : newest.side() == TaiXiuMath.Side.TAI ? "&#FF5C5C" : "&#55C7FF";
        Component summary = service.diceComponent(TaiXiuService.replacements(
                service.config().history3dSummary,
                Map.of(
                        "%round%", String.valueOf(newest.id()),
                        "%dice_1%", service.configuredDieIcon(newest.first()),
                        "%dice_2%", service.configuredDieIcon(newest.second()),
                        "%dice_3%", service.configuredDieIcon(newest.third()),
                        "%total%", String.valueOf(newest.total()),
                        "%result%", newest.triple() ? "BỘ BA" : newest.side().displayName().toUpperCase(),
                        "%result_color%", resultColor
                )));
        Vector summaryPosition = chartOrigin.clone().add(right.clone().multiply(CHART_WIDTH / 2.0d))
                .add(up.clone().multiply(CHART_HEIGHT + 0.26d)).add(forward.clone().multiply(-0.09d));
        addText(player, view, base, summary, summaryPosition, rotation,
                new Vector3f(0.42f, 0.42f, 0.42f), history.size(), true, 440);
    }

    private Piece addBlock(Player player, View view, Location base, Material material, Vector translation,
                           Quaternionf rotation, Vector3f scale, int revealAt) {
        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, entity -> {
            configure(entity);
            entity.setBlock(material.createBlockData());
        });
        Piece piece = new Piece(display, toVector3f(translation), new Quaternionf(rotation), new Vector3f(scale), revealAt);
        display.setTransformation(piece.collapsed());
        player.showEntity(plugin, display);
        view.pieces.add(piece);
        return piece;
    }

    private void addConnection(Player player, View view, Location base, Material material,
                               Vector start, Vector end, int revealAt) {
        Vector delta = end.clone().subtract(start);
        float length = (float) delta.length();
        if (length <= 0.001f) return;
        Vector3f direction = toVector3f(delta).normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(1.0f, 0.0f, 0.0f), direction);
        addBlock(player, view, base, material, start, rotation,
                new Vector3f(length, 0.045f, 0.055f), revealAt);
    }

    private void addText(Player player, View view, Location base, Component text, Vector translation,
                         Quaternionf rotation,
                         Vector3f scale, int revealAt, boolean background, int lineWidth) {
        TextDisplay display = base.getWorld().spawn(base, TextDisplay.class, entity -> {
            configure(entity);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(background ? Color.fromARGB(185, 5, 8, 16) : Color.fromARGB(0, 0, 0, 0));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(lineWidth);
            entity.setTextOpacity((byte) 255);
            entity.text(text.decoration(TextDecoration.BOLD, text.hasDecoration(TextDecoration.BOLD)));
        });
        Piece piece = new Piece(display, toVector3f(translation), new Quaternionf(rotation), new Vector3f(scale), revealAt);
        display.setTransformation(piece.collapsed());
        player.showEntity(plugin, display);
        view.pieces.add(piece);
    }

    private void configure(Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setViewRange(1.0f);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(7);
    }

    private void reveal(View view, int step) {
        for (Piece piece : view.pieces) {
            if (piece.revealAt != step) continue;
            FoliaScheduler.runForEntity(plugin, piece.display, () -> {
                if (isCurrent(view) && piece.display.isValid()) piece.display.setTransformation(piece.expanded());
            });
        }
    }

    private void update(Player player, View view) {
        if (!isCurrent(view)) {
            if (view.task != null) view.task.cancel();
            return;
        }
        if (!player.isOnline() || view.primary == null || !view.primary.isValid()) {
            closeCurrent(view, false);
            return;
        }
        TaiXiuConfig config = service.config();
        if (!player.getWorld().equals(view.viewerOrigin.getWorld())
                || player.getLocation().distanceSquared(view.viewerOrigin) >= config.historyMaxDistance * config.historyMaxDistance
                || System.currentTimeMillis() - view.openedAt >= config.historyLifetimeSeconds * 1000L) {
            closeCurrent(view, true);
            return;
        }
        if (view.shown < view.history.size()) {
            view.shown++;
            reveal(view, view.shown);
            if (view.shown == view.history.size()) {
                PluginSound.play(player, config.historyCompleteSound,
                        config.soundVolume * 0.75f, 1.25f);
            } else if (view.shown % 2 == 0) {
                PluginSound.play(player, config.historyPointSound,
                        config.soundVolume * 0.32f, 0.85f + view.shown * 0.04f);
            }
            return;
        }
        view.ticks += config.historyAnimationTicksPerPoint;
        if (view.latestPoint != null && view.ticks % 20 == 0) {
            view.pulseHigh = !view.pulseHigh;
            Piece latest = view.latestPoint;
            FoliaScheduler.runForEntity(plugin, latest.display, () -> {
                if (isCurrent(view) && latest.display.isValid()) {
                    latest.display.setInterpolationDuration(10);
                    latest.display.setTransformation(latest.scaled(view.pulseHigh ? 1.22f : 1.0f));
                }
            });
        }
    }

    void close(UUID playerId, boolean animate) {
        View view = views.remove(playerId);
        if (view == null) return;
        cleanup(view, animate);
    }

    private void closeCurrent(View expected, boolean animate) {
        if (!views.remove(expected.playerId, expected)) return;
        cleanup(expected, animate);
    }

    private void cleanup(View view, boolean animate) {
        if (view.task != null) view.task.cancel();
        int closeTicks = service.config().historyCloseTicks;
        for (Piece piece : view.pieces) {
            if (!piece.display.isValid()) continue;
            FoliaScheduler.runForEntity(plugin, piece.display, () -> {
                if (!piece.display.isValid()) return;
                if (!animate) {
                    piece.display.remove();
                    return;
                }
                piece.display.setInterpolationDuration(closeTicks);
                piece.display.setTransformation(piece.collapsed());
                FoliaScheduler.runLaterForEntity(plugin, piece.display, () -> {
                    if (piece.display.isValid()) piece.display.remove();
                }, closeTicks + 1L);
            });
        }
    }

    void closeAll(boolean animate) {
        for (UUID playerId : views.playerIds()) close(playerId, animate);
    }

    private boolean isCurrent(View view) {
        return views.isCurrent(view.playerId, view);
    }

    private static Material pointMaterial(TaiXiuStore.RoundHistory row) {
        if (row.triple()) return Material.PURPLE_CONCRETE;
        return row.side() == TaiXiuMath.Side.TAI ? Material.RED_CONCRETE : Material.LIGHT_BLUE_CONCRETE;
    }

    private static Material lineMaterial(TaiXiuStore.RoundHistory row) {
        if (row.triple()) return Material.PURPLE_STAINED_GLASS;
        return row.side() == TaiXiuMath.Side.TAI ? Material.RED_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS;
    }

    private static Vector3f toVector3f(Vector vector) {
        return new Vector3f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ());
    }

    private static final class Piece {
        private final Display display;
        private final Vector3f translation;
        private final Quaternionf rotation;
        private final Vector3f scale;
        private final int revealAt;

        private Piece(Display display, Vector3f translation, Quaternionf rotation, Vector3f scale, int revealAt) {
            this.display = display;
            this.translation = translation;
            this.rotation = rotation;
            this.scale = scale;
            this.revealAt = revealAt;
        }

        private Transformation expanded() {
            return scaled(1.0f);
        }

        private Transformation scaled(float multiplier) {
            return new Transformation(new Vector3f(translation), new Quaternionf(rotation),
                    new Vector3f(scale).mul(multiplier), new Quaternionf());
        }

        private Transformation collapsed() {
            return new Transformation(new Vector3f(translation), new Quaternionf(rotation),
                    new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
        }
    }

    private static final class View {
        private final UUID playerId;
        private final Location viewerOrigin;
        private final long openedAt;
        private final List<TaiXiuStore.RoundHistory> history;
        private final List<Piece> pieces = new ArrayList<>();
        private Display primary;
        private Piece latestPoint;
        private int shown;
        private int ticks;
        private boolean pulseHigh;
        private FoliaScheduler.TaskHandle task;

        private View(UUID playerId, Location viewerOrigin, long openedAt, List<TaiXiuStore.RoundHistory> history) {
            this.playerId = playerId;
            this.viewerOrigin = viewerOrigin;
            this.openedAt = openedAt;
            this.history = history;
        }
    }
}
