package com.nodezero.server.session;

import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Observation;

/** Where a session reports to; the WebSocket layer implements it. Called on the session's tick thread. */
public interface SessionListener {

    void onObservation(Observation observation);

    void onRejected(Command command, String reason);

    void onPauseChanged(boolean paused);

    /** The session hit an unexpected error and was removed. */
    void onFailure(Throwable error);
}
