package com.nodezero.server.web;

import com.nodezero.server.ServerProperties;
import com.nodezero.server.protocol.ClientMessage;
import com.nodezero.server.protocol.ServerMessage;
import com.nodezero.server.session.Session;
import com.nodezero.server.session.SessionHost;
import com.nodezero.server.session.SessionListener;
import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Observation;
import com.nodezero.sim.engine.SimConstants;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * One WebSocket connection = one game session. Incoming JSON becomes session input; observations
 * go out from the tick thread through a {@link ConcurrentWebSocketSessionDecorator}, which buffers
 * concurrent sends and disconnects clients that are too slow instead of stalling the shard.
 */
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GameSocketHandler.class);
    private static final String SESSION_ATTRIBUTE = "nodezero.session";

    private final SessionHost host;
    private final JsonMapper json;
    private final ServerProperties properties;

    public GameSocketHandler(SessionHost host, JsonMapper json, ServerProperties properties) {
        this.host = host;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) throws IOException {
        WebSocketSession out = new ConcurrentWebSocketSessionDecorator(
                socket, properties.sendTimeLimitMs(), properties.sendBufferBytes());
        Session session;
        try {
            session = host.open(new SocketListener(out));
        } catch (SessionHost.SessionLimitException full) {
            socket.close(CloseStatus.SERVICE_OVERLOAD.withReason(full.getMessage()));
            return;
        }
        socket.getAttributes().put(SESSION_ATTRIBUTE, session.id());
        send(out, new ServerMessage.Welcome(session.id(), session.seed(), SimConstants.TICK_HZ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws IOException {
        Session session = sessionOf(socket);
        if (session == null) {
            return;
        }
        ClientMessage action;
        try {
            action = json.readValue(message.getPayload(), ClientMessage.class);
        } catch (JacksonException e) {
            send(socket, new ServerMessage.Error("cannot read message: " + e.getOriginalMessage()));
            return;
        }
        switch (action) {
            case ClientMessage.Pause p -> session.pause();
            case ClientMessage.Resume r -> session.resume();
            default -> {
                try {
                    session.submit(ClientMessage.toCommand(action));
                } catch (IllegalArgumentException | NullPointerException invalid) {
                    send(socket, new ServerMessage.Rejected(action, invalid.getMessage()));
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        Object id = socket.getAttributes().get(SESSION_ATTRIBUTE);
        if (id != null) {
            host.remove(id.toString());
        }
    }

    private Session sessionOf(WebSocketSession socket) {
        Object id = socket.getAttributes().get(SESSION_ATTRIBUTE);
        return id == null ? null : host.find(id.toString()).orElse(null);
    }

    private void send(WebSocketSession socket, ServerMessage message) throws IOException {
        socket.sendMessage(new TextMessage(json.writeValueAsString(message)));
    }

    /** Bridges session events to one client. Runs on the session's tick thread. */
    private final class SocketListener implements SessionListener {

        private final WebSocketSession out;

        SocketListener(WebSocketSession out) {
            this.out = out;
        }

        @Override
        public void onObservation(Observation observation) {
            deliver(new ServerMessage.Snapshot(observation));
        }

        @Override
        public void onRejected(Command command, String reason) {
            deliver(new ServerMessage.Rejected(ClientMessage.fromCommand(command), reason));
        }

        @Override
        public void onPauseChanged(boolean paused) {
            deliver(new ServerMessage.Paused(paused));
        }

        @Override
        public void onFailure(Throwable error) {
            try {
                out.close(CloseStatus.SERVER_ERROR);
            } catch (IOException e) {
                LOG.debug("closing failed session socket", e);
            }
        }

        private void deliver(ServerMessage message) {
            if (!out.isOpen()) {
                return;
            }
            try {
                send(out, message);
            } catch (IOException | RuntimeException e) {
                // Slow or broken client: the decorator gives up; drop the connection, keep the shard going.
                LOG.debug("dropping client {}", out.getId(), e);
                try {
                    out.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException ignored) {
                    LOG.trace("close after send failure", ignored);
                }
            }
        }
    }
}
