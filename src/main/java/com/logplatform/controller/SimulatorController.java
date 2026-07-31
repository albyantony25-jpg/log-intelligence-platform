package com.logplatform.controller;

import com.logplatform.service.LogSimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.RequestMapping;
import org.springframework.web.bind.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@RestController
@RequestMapping("/simulate")
public class SimulatorController {

    private final LogSimulatorService simulatorService;

    public SimulatorController(LogSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startSimulation() {
        simulatorService.startSimulation();
        return ResponseEntity.ok(Map.of("status", "Simulation started"));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stopSimulation() {
        simulatorService.stopSimulation();
        return ResponseEntity.ok(Map.of("status", "Simulation stopped"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of("running", simulatorService.isRunning()));
    }
}
