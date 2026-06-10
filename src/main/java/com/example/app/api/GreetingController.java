package com.example.app.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example REST endpoint. The Angular app calls this under the /api prefix.
 */
@RestController
@RequestMapping("/api")
public class GreetingController {

    public record Greeting(String message, Instant timestamp) {
    }

    @GetMapping("/greeting")
    public Greeting greeting(@RequestParam(defaultValue = "World") String name) {
        return new Greeting("Hello, " + name + "!", Instant.now());
    }
}
