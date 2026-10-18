package scheme.tools;

import arc.Events;
import arc.struct.Queue;
import arc.util.Log;
import arc.util.Ratekeeper;
import arc.util.Timer;
import mindustry.core.GameState.State;
import mindustry.game.EventType.*;
import mindustry.gen.Call;
import mindustry.net.Net;
import mindustry.net.ValidateException;

import static mindustry.Vars.*;

/** Prevents ddos ban. */
public class MessageQueue {

    public static Queue<String> messages = new Queue<>();
    public static Ratekeeper messageRate = new Ratekeeper(), dropRate = new Ratekeeper();
    private static Net queuedNet;
    private static boolean queuedClient, queuedServer;

    public static void load() {
        Events.run(WorldLoadEvent.class, MessageQueue::clear);
        Events.run(ResetEvent.class, MessageQueue::clear);
        Events.run(ClientPreConnectEvent.class, MessageQueue::clear);
        Events.run(ClientServerConnectEvent.class, MessageQueue::clear);
        Events.on(StateChangeEvent.class, event -> {
            if (event.to == State.menu) clear();
        });

        Timer.schedule(MessageQueue::update, 0f, .1f);
    }

    public static void send(String message) {
        if (!inGame()) {
            clear();
            return;
        }
        if (!messages.isEmpty() && !sameSession()) clear();
        if (!valid(message)) return;

        if (messages.isEmpty()) {
            queuedNet = net;
            queuedClient = net.client();
            queuedServer = net.server();
        }
        if (messages.isEmpty() || !message.equals(messages.last())) messages.add(message);
    }

    static void update() {
        if (messages.isEmpty()) return;
        // A disconnected client's Call would otherwise execute against the local game.
        if (!sameSession()) {
            clear();
            return;
        }
        if (!valid(messages.first())) {
            messages.removeFirst();
            return;
        }
        if (!allow()) return;

        try {
            Call.sendChatMessage(messages.removeFirst());
        } catch (ValidateException ignored) {
            clear();
            Log.warn("[Scheme Size] Queued chat command was rejected; pending commands cleared.");
        }
    }

    static void clear() {
        messages.clear();
        queuedNet = null;
    }

    private static boolean inGame() {
        return net != null && state != null && state.isGame();
    }

    private static boolean sameSession() {
        return inGame() && net == queuedNet
            && net.client() == queuedClient && net.server() == queuedServer;
    }

    private static boolean valid(String message) {
        if (message == null || message.isEmpty()) return false;
        if (message.length() > maxTextLength) {
            // Commands may contain scripts: truncating or splitting changes their meaning.
            Log.warn("[Scheme Size] Skipped queued chat command exceeding the game's text limit.");
            return false;
        }
        return true;
    }

    public static boolean allow() {
        return messageRate.allow(2000L, 15);
    }

    public static boolean drop() {
        return dropRate.allow(1000L, 30);
    }
}
