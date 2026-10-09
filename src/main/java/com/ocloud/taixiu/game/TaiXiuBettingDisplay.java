package com.ocloud.taixiu.game;

import com.ocloud.taixiu.util.CompactNumberUtil;
import com.ocloud.taixiu.util.BedrockPlatform;
import com.ocloud.taixiu.util.FoliaScheduler;
import com.ocloud.taixiu.util.PluginSound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TaiXiuBettingDisplay {
    private static final float BOARD_WIDTH = 6.4f;
    private static final float BOARD_HEIGHT = 3.6f;

    private final JavaPlugin plugin;
    private final TaiXiuService service;
    private final CurrentViewRegistry<View> views = new CurrentViewRegistry<>();

    TaiXiuBettingDisplay(JavaPlugin plugin, TaiXiuService service) {
        this.plugin = plugin;
        this.service = service;
    }

    void open(Player player) {
        close(player.getUniqueId(), false);
        FoliaScheduler.runForPlayer(plugin, player, () -> spawn(player));
    }

    private void spawn(Player player) {
        if (!player.isOnline()) return;
        boolean bedrock = BedrockPlatform.isBedrock(player);
        Location eye = player.getEyeLocation();

        if (bedrock) eye.setYaw(Math.round(eye.getYaw() / 90.0f) * 90.0f);
        TaiXiuDisplayFrame.Frame frame = TaiXiuDisplayFrame.fromEye(eye);
        Location base = frame.anchor();
        Location origin = player.getLocation().clone();
        Vector forward = frame.forward();
        Vector right = frame.right();
        Vector up = frame.up();
        Quaternionf rotation = frame.rotation();
        TaiXiuConfig config = service.config();
        Vector center = forward.clone().multiply(config.bettingDisplayDistance)
                .add(up.clone().multiply(config.bettingDisplayVerticalOffset));
        Vector bottomLeft = center.clone().subtract(right.clone().multiply(BOARD_WIDTH / 2.0d))
                .subtract(up.clone().multiply(BOARD_HEIGHT / 2.0d));

        View view = new View(player.getUniqueId(), origin, base.clone().add(center), right, up, forward, bedrock);
        view.amountChoices.addAll(amountChoices(config));
        view.selectedAmount = view.amountChoices.get(0);
        addBoard(player, view, base, center, bottomLeft, right, up, forward, rotation);
        addDice(player, view, base, center, right, up, forward, rotation);
        addControls(player, view, base, center, right, up, forward, rotation);

        if (view.pieces.isEmpty()) return;
        view.primary = view.pieces.get(0).display;
        View displaced = views.put(player.getUniqueId(), view);
        if (displaced != null && displaced != view) cleanup(displaced, false);
        if (bedrock) showBedrockBoard(player, view);
        for (Piece piece : view.pieces) reveal(view, piece);
        updateInformation(player, view);
        PluginSound.play(player, service.config().menuOpenSound,
                service.config().soundVolume * 0.8f, 1.0f);
        view.task = FoliaScheduler.runTimerForPlayer(plugin, player, () -> update(player, view), 2L, 2L);
    }

    private void addBoard(Player player, View view, Location base, Vector center, Vector bottomLeft,
                          Vector right, Vector up, Vector forward, Quaternionf rotation) {
        addBlock(player, view, base, Material.TINTED_GLASS,
                bottomLeft.clone().add(forward.clone().multiply(0.12d)), rotation,
                new Vector3f(BOARD_WIDTH, BOARD_HEIGHT, 0.09f));
        Material frame = Material.POLISHED_BLACKSTONE;
        addBlock(player, view, base, frame, bottomLeft.clone(), rotation,
                new Vector3f(BOARD_WIDTH, 0.09f, 0.10f));
        addBlock(player, view, base, frame, bottomLeft.clone().add(up.clone().multiply(BOARD_HEIGHT)), rotation,
                new Vector3f(BOARD_WIDTH, 0.09f, 0.10f));
        addBlock(player, view, base, frame, bottomLeft.clone(), rotation,
                new Vector3f(0.09f, BOARD_HEIGHT, 0.10f));
        addBlock(player, view, base, frame, bottomLeft.clone().add(right.clone().multiply(BOARD_WIDTH)), rotation,
                new Vector3f(0.09f, BOARD_HEIGHT, 0.10f));
        addBlock(player, view, base, Material.RED_CONCRETE,
                bottomLeft.clone().add(up.clone().multiply(BOARD_HEIGHT - 0.16d)).add(forward.clone().multiply(-0.02d)),
                rotation, new Vector3f(BOARD_WIDTH, 0.07f, 0.11f));

        Component title = TaiXiuService.component(service.config().betting3dTitle);
        view.title = addText(player, view, base, title,
                center.clone().add(up.clone().multiply(1.53d)).add(forward.clone().multiply(-0.22d)),
                rotation, new Vector3f(0.78f, 0.78f, 0.78f), false, 360);
        view.info = addText(player, view, base, Component.empty(),
                center.clone().add(up.clone().multiply(1.11d)).add(forward.clone().multiply(-0.22d)),
                rotation, new Vector3f(0.52f, 0.52f, 0.52f), true, 560);
        view.footer = addText(player, view, base, Component.empty(),
                center.clone().subtract(up.clone().multiply(1.48d)).add(forward.clone().multiply(-0.22d)),
                rotation, new Vector3f(0.47f, 0.47f, 0.47f), true, 560);
    }

    private void addDice(Player player, View view, Location base, Vector center, Vector right, Vector up,
                         Vector forward, Quaternionf rotation) {
        double[] x = view.bedrock ? new double[]{-1.0d, 0.0d, 1.0d}
                : new double[]{-0.48d, 0.0d, 0.48d};
        for (double offset : x) {
            if (view.bedrock) {
                view.bedrockDice.add(addText(player, view, base,
                        Component.text("?", NamedTextColor.BLACK, TextDecoration.BOLD),
                        center.clone().add(right.clone().multiply(offset))
                                .add(up.clone().multiply(0.30d)).add(forward.clone().multiply(-0.28d)),
                        rotation, new Vector3f(0.82f, 0.82f, 0.82f), false, 80));
                continue;
            }
            Vector corner = center.clone().add(right.clone().multiply(offset - 0.20d))
                    .add(up.clone().multiply(0.08d)).add(forward.clone().multiply(-0.01d));
            addBlock(player, view, base, Material.WHITE_CONCRETE, corner, rotation,
                    new Vector3f(0.40f, 0.40f, 0.20f));
            List<Piece> face = new ArrayList<>(7);
            double[][] positions = {
                    {-0.105d, 0.105d}, {0.105d, 0.105d},
                    {-0.105d, 0.0d}, {0.0d, 0.0d}, {0.105d, 0.0d},
                    {-0.105d, -0.105d}, {0.105d, -0.105d}
            };
            for (double[] position : positions) {
                Vector pipCorner = center.clone()
                        .add(right.clone().multiply(offset + position[0] - 0.03d))
                        .add(up.clone().multiply(0.28d + position[1] - 0.03d))
                        .add(forward.clone().multiply(-0.235d));
                Piece pip = addBlock(player, view, base, Material.BLACK_CONCRETE, pipCorner, rotation,
                        new Vector3f(0.06f, 0.06f, 0.025f));
                pip.visible = false;
                face.add(pip);
            }
            view.dicePips.add(face);
        }
    }

    private void addControls(Player player, View view, Location base, Vector center, Vector right, Vector up,
                             Vector forward, Quaternionf rotation) {
        addButton(player, view, base, center.clone().subtract(right.clone().multiply(2.02d))
                        .add(up.clone().multiply(0.18d)), right, up, forward, rotation,
                1.72f, 0.76f, TaiXiuService.component(service.config().betting3dButtonTai),
                ButtonAction.TAI, null, Material.RED_CONCRETE);
        addButton(player, view, base, center.clone().add(right.clone().multiply(2.02d))
                        .add(up.clone().multiply(0.18d)), right, up, forward, rotation,
                1.72f, 0.76f, TaiXiuService.component(service.config().betting3dButtonXiu),
                ButtonAction.XIU, null, Material.LIGHT_BLUE_CONCRETE);

        List<AmountChoice> amounts = view.amountChoices;
        double spacing = amounts.size() <= 1 ? 0.0d : Math.min(1.05d, 5.0d / (amounts.size() - 1));
        float buttonWidth = amounts.size() > 5 ? 0.72f : 0.88f;
        for (int index = 0; index < amounts.size(); index++) {
            double x = (index - (amounts.size() - 1) / 2.0d) * spacing;
            AmountChoice amount = amounts.get(index);
            addButton(player, view, base, center.clone().add(right.clone().multiply(x))
                            .subtract(up.clone().multiply(0.91d)), right, up, forward, rotation,
                    buttonWidth, 0.44f, Component.text(amount.label(service.playerBalance(player)),
                            NamedTextColor.WHITE, TextDecoration.BOLD),
                    ButtonAction.AMOUNT, amount, Material.RED_CONCRETE);
        }

        addButton(player, view, base, center.clone().add(right.clone().multiply(2.82d))
                        .add(up.clone().multiply(1.42d)), right, up, forward, rotation,
                0.42f, 0.42f, TaiXiuService.component(service.config().betting3dClose),
                ButtonAction.CLOSE, null, Material.RED_CONCRETE);
        refreshButtons(view, null);
    }

    private void addButton(Player player, View view, Location base, Vector center, Vector right, Vector up,
                           Vector forward, Quaternionf rotation, float width, float height, Component label,
                           ButtonAction action, AmountChoice amount, Material hoverMaterial) {
        Vector corner = center.clone().subtract(right.clone().multiply(width / 2.0d))
                .subtract(up.clone().multiply(height / 2.0d)).add(forward.clone().multiply(-0.04d));
        Piece background = addBlock(player, view, base, Material.BLACK_CONCRETE, corner, rotation,
                new Vector3f(width, height, 0.11f));
        TextDisplay labelDisplay = addText(player, view, base, label,
                center.clone().add(forward.clone().multiply(-0.25d)), rotation,
                new Vector3f(0.58f, 0.58f, 0.58f), false, Math.max(90, (int) (width * 190)));
        Piece textPiece = view.pieceByDisplay.get(labelDisplay.getUniqueId());

        Vector local = base.toVector().add(center).subtract(view.boardCenter.toVector());
        Button button = new Button(background, textPiece, action, amount, hoverMaterial,
                local.dot(view.right), local.dot(view.up), width, height);
        view.buttons.add(button);
    }

    boolean handleClick(Player player) {
        if (player == null) return false;
        View view = views.get(player.getUniqueId());
        if (view == null) return false;
        Button button = findHovered(player, view);
        if (button == null) return false;
        long now = System.currentTimeMillis();
        if (button == view.lastClickedButton && now - view.lastClickAt < 160L) return true;
        view.lastClickedButton = button;
        view.lastClickAt = now;
        PluginSound.play(player, service.config().menuClickSound,
                service.config().soundVolume * 0.7f, 1.15f);
        switch (button.action) {
            case AMOUNT -> {
                view.selectedAmount = button.amount;
                refreshButtons(view, view.hovered);
                updateBedrockAmountSelection(player, view);
                updateInformation(player, view);
            }
            case TAI -> {
                service.placeBet(player, TaiXiuMath.Side.TAI, view.selectedAmount.token());
                updateInformation(player, view);
            }
            case XIU -> {
                service.placeBet(player, TaiXiuMath.Side.XIU, view.selectedAmount.token());
                updateInformation(player, view);
            }
            case CLOSE -> closeCurrent(view, true);
        }
        return true;
    }

    private void update(Player player, View view) {
        if (!views.isCurrent(view.playerId, view)) {
            if (view.task != null) view.task.cancel();
            return;
        }
        if (!player.isOnline() || view.primary == null || !view.primary.isValid()) {
            closeCurrent(view, false);
            return;
        }
        double maxDistance = service.config().bettingDisplayMaxDistance;
        if (!player.getWorld().equals(view.origin.getWorld())
                || player.getLocation().distanceSquared(view.origin) >= maxDistance * maxDistance) {
            closeCurrent(view, true);
            return;
        }

        Button hovered = findHovered(player, view);
        if (hovered != view.hovered) {
            Button old = view.hovered;
            view.hovered = hovered;
            if (old != null) renderButton(view, old, false);
            if (hovered != null) {
                renderButton(view, hovered, true);
                PluginSound.play(player, service.config().menuHoverSound,
                        service.config().soundVolume * 0.35f, 1.25f);
            }
        }
        view.ticks += 2;
        if (view.ticks % 10 == 0) updateInformation(player, view);
    }

    private Button findHovered(Player player, View view) {
        Location eye = player.getEyeLocation();
        if (!eye.getWorld().equals(view.boardCenter.getWorld())) return null;
        Vector direction = eye.getDirection().normalize();
        double denominator = direction.dot(view.forward);
        if (Math.abs(denominator) < 0.0001d) return null;
        Vector toPlane = view.boardCenter.toVector().subtract(eye.toVector());
        double distance = toPlane.dot(view.forward) / denominator;
        if (distance < 0.0d || distance > 7.5d) return null;
        Vector hit = eye.toVector().add(direction.multiply(distance)).subtract(view.boardCenter.toVector());
        double x = hit.dot(view.right);
        double y = hit.dot(view.up);
        for (Button button : view.buttons) {
            double padding = button.action == ButtonAction.TAI || button.action == ButtonAction.XIU ? 0.12d : 0.06d;
            if (Math.abs(x - button.x) <= button.width / 2.0d + padding
                    && Math.abs(y - button.y) <= button.height / 2.0d + padding) return button;
        }
        return null;
    }

    private void refreshButtons(View view, Button hovered) {
        for (Button button : view.buttons) renderButton(view, button, button == hovered);
    }

    private void renderButton(View view, Button button, boolean hovered) {
        boolean selected = button.action == ButtonAction.AMOUNT && button.amount.equals(view.selectedAmount);
        Material material = hovered ? button.hoverMaterial : selected ? Material.ORANGE_CONCRETE : Material.BLACK_CONCRETE;
        FoliaScheduler.runForEntity(plugin, button.background.display, () -> {
            if (!(button.background.display instanceof BlockDisplay block) || !block.isValid()) return;
            block.setBlock(material.createBlockData());
            block.setInterpolationDuration(4);
            block.setTransformation(button.background.scaled(hovered ? 1.07f : 1.0f));
        });
        if (button.label != null) {
            FoliaScheduler.runForEntity(plugin, button.label.display, () -> {
                if (!button.label.display.isValid()) return;
                button.label.display.setInterpolationDuration(4);
                button.label.display.setTransformation(button.label.scaled(hovered ? 1.10f : 1.0f));
            });
        }
    }

    private void updateInformation(Player player, View view) {
        TaiXiuService.RoundSnapshot snapshot = service.snapshot(player.getUniqueId());
        long balance = service.playerBalance(player);
        String selectedLabel = view.selectedAmount.label(balance);
        String time = String.format("%02d:%02d", Math.max(0, snapshot.secondsRemaining()) / 60,
                Math.max(0, snapshot.secondsRemaining()) % 60);
        Map<String, String> values = new HashMap<>();
        values.put("%round%", snapshot.roundId() <= 0 ? "--" : String.valueOf(snapshot.roundId()));
        values.put("%time%", time);
        values.put("%tai_total%", TaiXiuService.money(snapshot.taiTotal()));
        values.put("%xiu_total%", TaiXiuService.money(snapshot.xiuTotal()));
        values.put("%selection%", selectedLabel);
        values.put("%amount%", selectedLabel);
        values.put("%side%", "");
        values.put("%tai_max%", TaiXiuService.money(Math.min(balance,
                service.maximumAdditionalBet(player.getUniqueId(), TaiXiuMath.Side.TAI))));
        values.put("%xiu_max%", TaiXiuService.money(Math.min(balance,
                service.maximumAdditionalBet(player.getUniqueId(), TaiXiuMath.Side.XIU))));
        Component title = TaiXiuService.component(TaiXiuService.replacements(service.config().betting3dTitle, values));
        Component info = TaiXiuService.component(TaiXiuService.replacements(service.config().betting3dTotals, values));
        info = info.append(Component.newline()).append(TaiXiuService.component(
                TaiXiuService.replacements(service.config().betting3dLimits, values)));
        if (snapshot.ownBet() != null) {
            values.put("%side%", snapshot.ownBet().side().displayName());
            values.put("%amount%", TaiXiuService.money(snapshot.ownBet().amount()));
            info = info.append(Component.newline()).append(TaiXiuService.component(
                    TaiXiuService.replacements(service.config().betting3dOwnBet, values)));
        }
        values.put("%amount%", selectedLabel);
        values.put("%selection%", selectedLabel);
        Component footer = TaiXiuService.component(
                TaiXiuService.replacements(balance <= 0L
                        ? service.config().text("display.betting-3d.no-money",
                                "&cVí hiện 0$ • Bạn cần có tiền trong Vault để đặt cược")
                        : service.config().betting3dSelected, values));
        updateText(view.title, title);
        updateText(view.info, info);
        updateText(view.footer, footer);
        for (Button button : view.buttons) {
            if (button.action != ButtonAction.AMOUNT || button.amount == null
                    || !(button.label.display instanceof TextDisplay label)) continue;
            updateText(label, Component.text(button.amount.label(balance), NamedTextColor.WHITE, TextDecoration.BOLD));
        }

        TaiXiuStore.RoundHistory result = snapshot.lastResult();
        for (int index = 0; index < view.bedrockDice.size(); index++) {
            int value = result == null ? 0 : index == 0 ? result.first() : index == 1 ? result.second() : result.third();
            updateText(view.bedrockDice.get(index), Component.text(value == 0 ? "?" : Integer.toString(value),
                    NamedTextColor.BLACK, TextDecoration.BOLD));
        }
        for (int index = 0; index < view.dicePips.size(); index++) {
            int value = result == null ? 0 : index == 0 ? result.first() : index == 1 ? result.second() : result.third();
            updateDiceFace(view.dicePips.get(index), value);
        }
    }

    private void updateDiceFace(List<Piece> pips, int value) {
        for (int index = 0; index < pips.size(); index++) {
            Piece pip = pips.get(index);
            boolean visible = pipVisible(value, index);
            if (pip.visible == visible) continue;
            pip.visible = visible;
            FoliaScheduler.runForEntity(plugin, pip.display, () -> {
                if (pip.display.isValid()) pip.display.setTransformation(pip.expanded());
            });
        }
    }

    static boolean pipVisible(int value, int index) {
        return switch (value) {
            case 1 -> index == 3;
            case 2 -> index == 0 || index == 6;
            case 3 -> index == 0 || index == 3 || index == 6;
            case 4 -> index == 0 || index == 1 || index == 5 || index == 6;
            case 5 -> index == 0 || index == 1 || index == 3 || index == 5 || index == 6;
            case 6 -> index == 0 || index == 1 || index == 2 || index == 4 || index == 5 || index == 6;
            default -> false;
        };
    }

    private void updateText(TextDisplay display, Component text) {
        if (display == null) return;
        FoliaScheduler.runForEntity(plugin, display, () -> {
            if (display.isValid()) display.text(text);
        });
    }

    private Piece addBlock(Player player, View view, Location base, Material material, Vector translation,
                           Quaternionf rotation, Vector3f scale) {
        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, entity -> {
            configure(entity);
            entity.setBlock(material.createBlockData());
        });
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(rotation), new Vector3f(), new Quaternionf()));
        Piece piece = new Piece(display, new Quaternionf(rotation), new Vector3f(scale),
                display.teleportAsync(base.clone().add(translation)));
        player.showEntity(plugin, display);
        view.pieces.add(piece);
        view.pieceByDisplay.put(display.getUniqueId(), piece);
        return piece;
    }

    private TextDisplay addText(Player player, View view, Location base, Component text, Vector translation,
                                Quaternionf rotation, Vector3f scale, boolean background, int lineWidth) {
        TextDisplay display = base.getWorld().spawn(base, TextDisplay.class, entity -> {
            configure(entity);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(background ? Color.fromARGB(175, 5, 8, 16) : Color.fromARGB(0, 0, 0, 0));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(lineWidth);
            entity.setTextOpacity((byte) 255);
            entity.text(text);
        });
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(rotation), new Vector3f(), new Quaternionf()));
        Piece piece = new Piece(display, new Quaternionf(rotation), new Vector3f(scale),
                display.teleportAsync(base.clone().add(translation)));
        player.showEntity(plugin, display);
        view.pieces.add(piece);
        view.pieceByDisplay.put(display.getUniqueId(), piece);
        return display;
    }

    private void configure(Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setViewRange(1.0f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(8);
    }

    private void showBedrockBoard(Player player, View view) {
        int bottomY = Math.round((float) (view.boardCenter.getY() - BOARD_HEIGHT / 2.0d));
        Vector behindText = view.forward.clone().multiply(0.8d);
        for (int row = 0; row < 4; row++) {
            for (int column = -3; column <= 3; column++) {
                Vector center = view.boardCenter.toVector().add(view.right.clone().multiply(column))
                        .add(behindText);
                Location block = new Location(view.boardCenter.getWorld(), Math.floor(center.getX()),
                        bottomY + row, Math.floor(center.getZ()));
                int cell = row * 7 + column + 3;
                Material panel = bedrockPanelMaterial(column, row);
                FoliaScheduler.runForLocation(plugin, block, () -> {
                    BlockData original = block.getBlock().getBlockData();
                    FoliaScheduler.runForPlayer(plugin, player, () -> {
                        if (!views.isCurrent(view.playerId, view)) return;
                        view.bedrockBlocks.put(block, original);
                        view.bedrockCells.put(cell, block);
                        player.sendBlockChange(block, panel.createBlockData());
                        updateBedrockAmountSelection(player, view);
                    });
                });
            }
        }
        updateBedrockAmountSelection(player, view);
    }

    private void updateBedrockAmountSelection(Player player, View view) {
        if (!view.bedrock) return;
        for (Button button : view.buttons) {
            if (button.action != ButtonAction.AMOUNT) continue;
            int column = Math.max(-3, Math.min(3, Math.round((float) button.x)));
            Location block = view.bedrockCells.get(column + 3);
            if (block == null) continue;
            Material material = button.amount.equals(view.selectedAmount)
                    ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE;
            player.sendBlockChange(block, material.createBlockData());
        }
    }

    private static Material bedrockPanelMaterial(int column, int row) {
        if (row == 3 || (row == 0 && Math.abs(column) == 3)) return Material.POLISHED_BLACKSTONE;
        if (row == 2 && Math.abs(column) <= 1) return Material.WHITE_CONCRETE;
        if (row == 1 && column <= -2) return Material.RED_CONCRETE;
        if (row == 1 && column >= 2) return Material.LIGHT_BLUE_CONCRETE;
        if (row == 0 && Math.abs(column) <= 2) return Material.ORANGE_CONCRETE;
        return Material.BLACK_CONCRETE;
    }

    private void reveal(View view, Piece piece) {
        piece.ready.thenAccept(moved -> FoliaScheduler.runForEntity(plugin, piece.display, () -> {
            if (!moved || !views.isCurrent(view.playerId, view) || !piece.display.isValid()) {
                if (!moved && piece.display.isValid()) piece.display.remove();
                return;
            }
            piece.display.setTransformation(piece.expanded());
        }));
    }

    void close(UUID playerId, boolean animate) {
        View view = views.remove(playerId);
        if (view != null) cleanup(view, animate);
    }

    private void closeCurrent(View view, boolean animate) {
        if (views.remove(view.playerId, view)) cleanup(view, animate);
    }

    private void cleanup(View view, boolean animate) {
        if (view.task != null) view.task.cancel();
        if (!view.bedrockBlocks.isEmpty()) {
            Player player = org.bukkit.Bukkit.getPlayer(view.playerId);
            if (player != null && player.isOnline()) {
                FoliaScheduler.runForPlayer(plugin, player, () -> {
                    View current = views.get(view.playerId);
                    view.bedrockBlocks.forEach((block, original) -> {
                        if (current == null || !current.bedrockBlocks.containsKey(block)) {
                            player.sendBlockChange(block, original);
                        }
                    });
                });
            }
        }
        int closeTicks = service.config().historyCloseTicks;
        for (Piece piece : view.pieces) {
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

    private static List<AmountChoice> amountChoices(TaiXiuConfig config) {
        List<AmountChoice> choices = new ArrayList<>();
        for (int percent : config.bettingDisplayPercentages) {
            choices.add(new AmountChoice(percent + "%", "", percent));
        }
        if (config.bettingDisplayAllIn) choices.add(new AmountChoice("all-in", "ALL-IN", 0));
        if (config.bettingDisplayMaxSafe) choices.add(new AmountChoice("max-safe", "MAX", 0));
        if (choices.isEmpty()) choices.add(new AmountChoice("max-safe", "MAX", 0));
        return choices;
    }

    static String percentageButtonLabel(long balance, int percent) {
        long amount = TaiXiuMath.percentageOf(balance, percent);
        return amount <= 0L ? percent + "%" : CompactNumberUtil.formatShard(amount) + "$";
    }

    private enum ButtonAction { TAI, XIU, AMOUNT, CLOSE }

    private static final class Button {
        private final Piece background;
        private final Piece label;
        private final ButtonAction action;
        private final AmountChoice amount;
        private final Material hoverMaterial;
        private final double x;
        private final double y;
        private final float width;
        private final float height;

        private Button(Piece background, Piece label, ButtonAction action, AmountChoice amount,
                       Material hoverMaterial, double x, double y, float width, float height) {
            this.background = background;
            this.label = label;
            this.action = action;
            this.amount = amount;
            this.hoverMaterial = hoverMaterial;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private record AmountChoice(String token, String fixedLabel, int percent) {
        private String label(long balance) {
            if (percent <= 0) return fixedLabel;
            return percentageButtonLabel(balance, percent);
        }
    }

    private static final class Piece {
        private final Display display;
        private final Quaternionf rotation;
        private final Vector3f scale;
        private final CompletableFuture<Boolean> ready;
        private volatile boolean visible = true;

        private Piece(Display display, Quaternionf rotation, Vector3f scale, CompletableFuture<Boolean> ready) {
            this.display = display;
            this.rotation = rotation;
            this.scale = scale;
            this.ready = ready;
        }

        private Transformation expanded() {
            return visible ? scaled(1.0f) : collapsed();
        }

        private Transformation scaled(float multiplier) {
            return new Transformation(new Vector3f(), new Quaternionf(rotation),
                    new Vector3f(scale).mul(multiplier), new Quaternionf());
        }

        private Transformation collapsed() {
            return new Transformation(new Vector3f(), new Quaternionf(rotation),
                    new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
        }
    }

    private static final class View {
        private final UUID playerId;
        private final Location origin;
        private final Location boardCenter;
        private final Vector right;
        private final Vector up;
        private final Vector forward;
        private final boolean bedrock;
        private final Map<Location, BlockData> bedrockBlocks = new LinkedHashMap<>();
        private final Map<Integer, Location> bedrockCells = new HashMap<>();
        private final List<Piece> pieces = new ArrayList<>();
        private final Map<UUID, Piece> pieceByDisplay = new HashMap<>();
        private final List<Button> buttons = new ArrayList<>();
        private final List<List<Piece>> dicePips = new ArrayList<>();
        private final List<TextDisplay> bedrockDice = new ArrayList<>();
        private final List<AmountChoice> amountChoices = new ArrayList<>();
        private AmountChoice selectedAmount;
        private Display primary;
        private TextDisplay title;
        private TextDisplay info;
        private TextDisplay footer;
        private Button hovered;
        private Button lastClickedButton;
        private long lastClickAt;
        private int ticks;
        private FoliaScheduler.TaskHandle task;

        private View(UUID playerId, Location origin, Location boardCenter, Vector right, Vector up, Vector forward,
                     boolean bedrock) {
            this.playerId = playerId;
            this.origin = origin;
            this.boardCenter = boardCenter;
            this.right = right.clone();
            this.up = up.clone();
            this.forward = forward.clone();
            this.bedrock = bedrock;
        }
    }
}
