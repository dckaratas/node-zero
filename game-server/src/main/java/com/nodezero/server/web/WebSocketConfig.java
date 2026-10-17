package com.nodezero.server.web;

import com.nodezero.server.ServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Plain WebSocket at {@code /ws/game} (decision M: no STOMP). */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final GameSocketHandler handler;
    private final ServerProperties properties;

    WebSocketConfig(GameSocketHandler handler, ServerProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/game")
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
    }
}
