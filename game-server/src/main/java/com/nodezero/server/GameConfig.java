package com.nodezero.server;

import com.nodezero.server.protocol.CodexBook;
import com.nodezero.server.protocol.CounterCatalog;
import com.nodezero.sim.engine.Game;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GameConfig {

    /** Immutable and thread-safe: one instance serves every session (ADR-0014). */
    @Bean
    Game game() {
        return Game.chapterOne();
    }

    /** Resolved once for both languages; requests only filter it. */
    @Bean
    CounterCatalog counterCatalog(Game game) {
        return new CounterCatalog(game.rules());
    }

    @Bean
    CodexBook codexBook(Game game) {
        return new CodexBook(game.rules().content());
    }
}
