package scheme.tools;

import arc.Core;
import arc.Events;
import arc.Settings;
import arc.mock.MockApplication;
import arc.mock.MockFiles;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.game.EventType;
import mindustry.gen.SendChatMessageCallPacket;
import mindustry.net.Administration.Config;
import mindustry.net.Net;
import mindustry.net.ValidateException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MessageQueueTest {
    private FakeNet net;

    @BeforeClass
    public static void installHooks() {
        Core.app = new MockApplication() {
            @Override public void post(Runnable runnable) {
                // Tests advance the queue explicitly instead of racing the timer thread.
            }
        };
        Core.files = new MockFiles();
        MessageQueue.load();
        Timer.instance().stop();
        Timer.instance().clear();
    }

    @AfterClass
    public static void removeHooks() {
        Events.clear();
    }

    @Before
    public void setUp() {
        MessageQueue.clear();
        MessageQueue.messageRate = new arc.util.Ratekeeper();
        MessageQueue.dropRate = new arc.util.Ratekeeper();
        Vars.state = new GameState();
        Vars.state.set(GameState.State.playing);
        net = new FakeNet();
        Vars.net = net;
    }

    @After
    public void tearDown() {
        MessageQueue.clear();
        Vars.net = null;
        Vars.state = null;
        Vars.netServer = null;
    }

    @Test
    public void accepts150Rejects151AndSurrogateByUtf16Length() {
        net.mode(false);
        String accepted = repeat('a', 150);
        String rejected = repeat('b', 151);
        String surrogateRejected = repeat('c', 149) + "\uD83D\uDE00";
        MessageQueue.send(null);
        MessageQueue.send("");
        MessageQueue.send(accepted);
        MessageQueue.send(rejected);
        MessageQueue.send(surrogateRejected);
        assertEquals(1, MessageQueue.messages.size);
        assertEquals(accepted, MessageQueue.messages.first());
        MessageQueue.update();
        assertEquals(accepted, ((SendChatMessageCallPacket) net.sent.get(0)).message);
    }

    @Test
    public void fifoDedupAndRateLimitPreserveOrder() {
        net.mode(false);
        MessageQueue.send("one");
        MessageQueue.send("one");
        MessageQueue.send("two");
        assertEquals(2, MessageQueue.messages.size);
        MessageQueue.update();
        MessageQueue.update();
        assertEquals(2, net.sent.size());
        assertEquals("one", ((SendChatMessageCallPacket) net.sent.get(0)).message);
        assertEquals("two", ((SendChatMessageCallPacket) net.sent.get(1)).message);
        for (int i = 2; i < 16; i++) MessageQueue.send("command-" + i);
        for (int i = 2; i < 16; i++) MessageQueue.update();
        assertEquals(15, net.sent.size());
        assertEquals("command-15", MessageQueue.messages.first());
        MessageQueue.messageRate.lastTime = 0;
        MessageQueue.update();
        assertEquals(16, net.sent.size());
        assertTrue(MessageQueue.messages.isEmpty());
    }

    @Test
    public void clearDoesNotResetRatekeeper() {
        for (int i = 0; i < 15; i++) assertTrue(MessageQueue.allow());
        MessageQueue.clear();
        assertFalse(MessageQueue.allow());
    }

    @Test
    public void disconnectPreventsCallLocalFallback() {
        net.mode(false);
        MessageQueue.send("queued");
        net.disconnect();
        MessageQueue.update();
        assertTrue(MessageQueue.messages.isEmpty());
        assertTrue(net.sent.isEmpty());
    }

    @Test
    public void sameNetReconnectAndLifecycleEventsClearQueue() {
        net.mode(false);
        MessageQueue.send("queued");
        net.disconnect();
        net.mode(false);
        Events.fire(new EventType.ClientServerConnectEvent("127.0.0.1", 6567));
        assertTrue(MessageQueue.messages.isEmpty());
        MessageQueue.send("world");
        Events.fire(new EventType.WorldLoadEvent());
        assertTrue(MessageQueue.messages.isEmpty());
        MessageQueue.send("reset");
        Events.fire(new EventType.ResetEvent());
        assertTrue(MessageQueue.messages.isEmpty());
        MessageQueue.send("preconnect");
        Events.fire(new EventType.ClientPreConnectEvent(null));
        assertTrue(MessageQueue.messages.isEmpty());
        MessageQueue.send("menu");
        Vars.state.set(GameState.State.menu);
        assertTrue(MessageQueue.messages.isEmpty());
        MessageQueue.send("in menu");
        assertTrue(MessageQueue.messages.isEmpty());
    }

    @Test
    public void pausedLocalAndHostedCommandsReachNativeHandler() {
        Core.settings = new Settings();
        Config.logCommands.set(false);
        Vars.netServer = new NetServer();
        List<String> received = new ArrayList<>();
        Vars.netServer.clientCommands.register("queue-test", "<value>", "Test command.",
            (args, player) -> received.add(args[0]));
        Vars.state.set(GameState.State.paused);
        MessageQueue.send("/queue-test local");
        MessageQueue.update();
        assertEquals("local", received.get(0));
        MessageQueue.clear();
        net.mode(true);
        MessageQueue.send("/queue-test host");
        MessageQueue.update();
        assertEquals(2, received.size());
        assertEquals("host", received.get(1));
        assertTrue(net.sent.isEmpty());
    }

    @Test
    public void replacingNetworkOrSwitchingModeDropsOldCommands() {
        net.mode(false);
        MessageQueue.send("old connection");
        Vars.net = new FakeNet();
        ((FakeNet) Vars.net).mode(false);
        MessageQueue.update();
        assertTrue(MessageQueue.messages.isEmpty());
        assertTrue(net.sent.isEmpty());

        Vars.net = net;
        MessageQueue.send("old client command");
        net.mode(true);
        MessageQueue.send("new host command");
        assertEquals(1, MessageQueue.messages.size);
        assertEquals("new host command", MessageQueue.messages.first());
    }

    @Test
    public void directInvalidQueueEntryIsDroppedWithoutSplitOrMutation() {
        net.mode(false);
        MessageQueue.send("seed");
        MessageQueue.messages.removeFirst();
        MessageQueue.messages.add(null);
        MessageQueue.messages.add(repeat('x', 151));
        MessageQueue.messages.add("valid");
        MessageQueue.update();
        MessageQueue.update();
        assertEquals(1, MessageQueue.messages.size);
        assertEquals("valid", MessageQueue.messages.first());
        assertTrue(net.sent.isEmpty());
        assertEquals(0, MessageQueue.messageRate.occurences);
        MessageQueue.update();
        assertEquals("valid", ((SendChatMessageCallPacket) net.sent.get(0)).message);
    }

    @Test
    public void validationFailureClearsRemainingQueueWithoutLoggingPayload() {
        net.mode(false);
        net.throwValidation = true;
        List<String> warnings = new ArrayList<>();
        Log.LogHandler previous = Log.logger;
        Log.logger = (level, text) -> warnings.add(text);
        try {
            MessageQueue.send("private-command");
            MessageQueue.send("second");
            MessageQueue.update();
            assertTrue(MessageQueue.messages.isEmpty());
            assertEquals(1, warnings.size());
            assertFalse(warnings.get(0).contains("private-command"));
            assertFalse(warnings.get(0).contains("private-error"));
        } finally {
            Log.logger = previous;
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(c);
        return result.toString();
    }

    private static final class FakeNet extends Net {
        final List<Object> sent = new ArrayList<>();
        boolean throwValidation;
        private boolean active, server;

        FakeNet() { super(null); }

        void mode(boolean server) {
            this.active = true;
            this.server = server;
        }

        @Override public boolean active() { return active; }
        @Override public boolean server() { return active && server; }
        @Override public boolean client() { return active && !server; }
        @Override public void disconnect() { active = false; server = false; }

        @Override
        public void send(Object object, boolean reliable) {
            if (throwValidation) throw new ValidateException(null, "private-error");
            sent.add(object);
        }
    }
}
