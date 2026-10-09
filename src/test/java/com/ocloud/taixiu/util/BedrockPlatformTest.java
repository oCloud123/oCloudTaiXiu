package com.ocloud.taixiu.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlatformTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();

    @Test
    void supportsCurrentGeyserBooleanDetection() throws ReflectiveOperationException {
        assertTrue(BedrockPlatform.detectsGeyser(new CurrentGeyserApi(), PLAYER_ID));
    }

    @Test
    void fallsBackToGeyserConnectionLookup() throws ReflectiveOperationException {
        assertTrue(BedrockPlatform.detectsGeyser(new ConnectionOnlyGeyserApi(), PLAYER_ID));
        assertFalse(BedrockPlatform.detectsGeyser(new EmptyConnectionGeyserApi(), PLAYER_ID));
    }

    @Test
    void fallsBackToFloodgatePlayerLookup() throws ReflectiveOperationException {
        assertTrue(BedrockPlatform.detectsFloodgate(new PlayerOnlyFloodgateApi(), PLAYER_ID));
        assertFalse(BedrockPlatform.detectsFloodgate(new EmptyFloodgateApi(), PLAYER_ID));
    }

    @Test
    void recognizesAnUnlinkedFloodgateUuidWithoutAnApi() {
        assertTrue(BedrockPlatform.isFloodgateUuid(UUID.fromString("00000000-0000-0000-0009-01ffa1d6ea02")));
        assertFalse(BedrockPlatform.isFloodgateUuid(UUID.randomUUID()));
    }

    @Test
    void supportsFloodgate225SimpleFormResponseHandler() throws ReflectiveOperationException {
        AtomicInteger selected = new AtomicInteger(-1);
        LegacySimpleBuilder builder = new LegacySimpleBuilder();

        BedrockPlatform.configureSimpleButtons(builder, java.util.List.of(
                new BedrockPlatform.SimpleButton("First", () -> selected.set(0)),
                new BedrockPlatform.SimpleButton("Second", () -> selected.set(1))
        ));
        builder.submit(1);

        assertTrue(builder.buttons.equals(java.util.List.of("First", "Second")));
        assertTrue(selected.get() == 1);
    }

    @Test
    void aRepeatedFormResponseCannotSubmitTheSameBetTwice() throws ReflectiveOperationException {
        AtomicInteger submissions = new AtomicInteger();
        LegacySimpleBuilder builder = new LegacySimpleBuilder();
        BedrockPlatform.configureSimpleButtons(builder, java.util.List.of(
                new BedrockPlatform.SimpleButton("Confirm", submissions::incrementAndGet)));
        builder.submit(0);
        builder.submit(0);
        assertEquals(1, submissions.get());
    }

    @Test
    void supportsLegacyGetClickedButtonIdResponse() throws ReflectiveOperationException {
        AtomicInteger selected = new AtomicInteger();
        LegacySimpleBuilder builder = new LegacySimpleBuilder();
        BedrockPlatform.configureSimpleButtons(builder, java.util.List.of(
                new BedrockPlatform.SimpleButton("Confirm", selected::incrementAndGet)));
        builder.handler.accept(new OlderSimpleResponse());
        assertEquals(1, selected.get());
    }

    public static final class OlderSimpleResponse {
        public int getClickedButtonId() { return 0; }
    }

    @Test
    void infersRemoteAndPackImageTypes() {
        assertEquals("URL", BedrockPlatform.inferImageType("https://cdn.example.com/shop.png"));
        assertEquals("PATH", BedrockPlatform.inferImageType("textures/items/emerald"));
        assertEquals("PATH", BedrockPlatform.inferImageType(""));
    }

    @Test
    void readsGiftcodeFromBedrockCustomFormResponse() {
        assertEquals("NEWBIE", BedrockPlatform.firstFormInput(new IndexedCustomResponse()));
        assertEquals("WELCOME", BedrockPlatform.firstFormInput(new LegacyCustomResponse()));
        assertEquals("", BedrockPlatform.firstFormInput(null));
    }

    public static final class CurrentGeyserApi {
        public boolean isBedrockPlayer(UUID uuid) {
            return PLAYER_ID.equals(uuid);
        }
    }

    public static final class ConnectionOnlyGeyserApi {
        public Optional<String> connectionByUuid(UUID uuid) {
            return PLAYER_ID.equals(uuid) ? Optional.of("connection") : Optional.empty();
        }
    }

    public static final class EmptyConnectionGeyserApi {
        public Object connectionByUuid(UUID uuid) {
            return null;
        }
    }

    public static final class PlayerOnlyFloodgateApi {
        public Object getPlayer(UUID uuid) {
            return PLAYER_ID.equals(uuid) ? new Object() : null;
        }
    }

    public static final class EmptyFloodgateApi {
        public boolean isFloodgatePlayer(UUID uuid) {
            return false;
        }

        public Object getPlayer(UUID uuid) {
            return null;
        }
    }

    public static final class LegacySimpleBuilder {
        private final java.util.List<String> buttons = new java.util.ArrayList<>();
        private Consumer<Object> handler;

        public LegacySimpleBuilder button(String text) {
            buttons.add(text);
            return this;
        }

        public LegacySimpleBuilder validResultHandler(Consumer<Object> handler) {
            this.handler = handler;
            return this;
        }

        void submit(int buttonId) {
            handler.accept(new LegacySimpleResponse(buttonId));
        }
    }

    public record LegacySimpleResponse(int clickedButtonId) {
    }

    public static final class IndexedCustomResponse {
        public String asInput(int index) {
            return index == 0 ? "NEWBIE" : "wrong";
        }
    }

    public static final class LegacyCustomResponse {
        public String asInput() {
            return "WELCOME";
        }
    }
}
