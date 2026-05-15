package tn.esprit.msinterview.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping({"/", ""})
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of("service", "MS-Interview", "status", "UP"));
    }
}
