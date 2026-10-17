package com.nodezero.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodezero.sim.engine.Command;
import com.nodezero.sim.engine.Game;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class ProtocolTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsPlayerActions() {
        assertThat(json.readValue("{\"type\":\"start_wave\",\"wave\":3}", ClientMessage.class))
                .isEqualTo(new ClientMessage.StartWave(3));
        assertThat(json.readValue("{\"type\":\"apply_counter\",\"counter\":\"create-index\",\"option\":\"concurrently\"}",
                ClientMessage.class))
                .isEqualTo(new ClientMessage.ApplyCounter("create-index", "concurrently"));
        assertThat(json.readValue("{\"type\":\"pause\"}", ClientMessage.class)).isEqualTo(new ClientMessage.Pause());
    }

    @Test
    void rejectsUnknownTypes() {
        assertThatThrownBy(() -> json.readValue("{\"type\":\"unlock_insight\",\"insight\":\"pg-stat-statements\"}",
                ClientMessage.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void mapsActionsToCommandsAndBack() {
        ClientMessage action = new ClientMessage.ApplyCounter("prometheus", null);
        Command command = ClientMessage.toCommand(action);

        assertThat(command).isEqualTo(new Command.ApplyCounter("prometheus", null));
        assertThat(ClientMessage.fromCommand(command)).isEqualTo(action);
        assertThat(ClientMessage.toCommand(new ClientMessage.Pause())).isNull();
    }

    @Test
    void snapshotCarriesTheObservationWithATypeTag() {
        Game game = Game.chapterOne();
        String out = json.writeValueAsString(new ServerMessage.Snapshot(game.observe(game.newState(1))));

        assertThat(out).startsWith("{\"type\":\"snapshot\"").contains("\"shield\":").contains("\"phase\":\"PREPARATION\"");
    }
}
