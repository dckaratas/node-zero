package com.nodezero.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Node Zero game server: hosts sessions and streams fog-of-war observations over WebSocket. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NodeZeroServer {

    public static void main(String[] args) {
        SpringApplication.run(NodeZeroServer.class, args);
    }
}
