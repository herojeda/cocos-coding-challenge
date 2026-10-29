package com.cocoscap.challenge.adapter.in.web.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller that exposes endpoints designed to provoke error responses.
 */
@RestController
@RequestMapping("/__test__/errors")
class ErrorHandlingTestController {

    record SampleBody(@Positive int amount, @NotBlank String label) {
    }

    @GetMapping("/ok")
    ResponseEntity<String> ok() {
        return ResponseEntity.ok("ok");
    }

    @PostMapping(path = "/bodies", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> createBody(
            @RequestHeader("X-Required") String requiredHeader, @Valid @RequestBody SampleBody body) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/items/{id}")
    ResponseEntity<String> item(@PathVariable long id) {
        return ResponseEntity.ok("item-" + id);
    }

    @GetMapping("/boom")
    ResponseEntity<Void> boom() {
        throw new RuntimeException("secret-internal-detail");
    }
}
