package com.nodezero.server;

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
}
