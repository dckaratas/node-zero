package com.nodezero.sim.engine;

import com.nodezero.sim.model.Demand;
import com.nodezero.sim.state.ResourceId;
import java.util.Map;

/**
 * Output of the request pipeline for one tick: resource demands plus what the budget needs to know.
 *
 * @param arrivalsRps requests arriving at the station
 * @param servedRps requests that reached the application (past the rate limiter and Tomcat)
 * @param edgeFailedRps failures decided in the pipeline: 429s, downtime, statement timeouts, write locks
 * @param writeFraction share of requests that write (they fail when the disk is full)
 * @param threadHoldMs time a request spends inside the application; with thread wait, its latency
 */
record Plan(
        Map<ResourceId, Demand> demands,
        double arrivalsRps,
        double servedRps,
        double edgeFailedRps,
        double writeFraction,
        double threadHoldMs) {}
