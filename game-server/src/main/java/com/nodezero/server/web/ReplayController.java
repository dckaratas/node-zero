package com.nodezero.server.web;

import com.nodezero.server.protocol.ClientMessage;
import com.nodezero.server.protocol.ServerMessage;
import com.nodezero.server.session.Session;
import com.nodezero.server.session.SessionHost;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exports a session's seed and accepted actions (ADR-0006: replay is verification).
 * Session ids are unguessable UUIDs; real authentication arrives with deployment (slice 7).
 */
@RestController
class ReplayController {

    private final SessionHost host;

    ReplayController(SessionHost host) {
        this.host = host;
    }

    @GetMapping("/api/sessions/{id}/replay")
    ResponseEntity<ServerMessage.ReplayLog> replay(@PathVariable String id) {
        return host.find(id)
                .map(ReplayController::toLog)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static ServerMessage.ReplayLog toLog(Session session) {
        List<ServerMessage.ReplayLog.Entry> entries = session.log().stream()
                .map(e -> new ServerMessage.ReplayLog.Entry(e.tick(), ClientMessage.fromCommand(e.command())))
                .toList();
        return new ServerMessage.ReplayLog(session.id(), session.seed(), entries);
    }
}
