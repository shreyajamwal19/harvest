package com.harvest.chef.controller;

import com.harvest.chef.knowledge.manager.KnowledgeProviderRegistry;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read-only observability endpoint for the Knowledge Provider ecosystem -
 * which providers are registered, per category, and their current health.
 * Purely additive: does not change the /api/chef/chat contract.
 */
@RestController
@RequestMapping("/api/chef/providers")
@RequiredArgsConstructor
public class KnowledgeProviderStatusController {

    private final KnowledgeProviderRegistry registry;

    @GetMapping("/health")
    public ResponseEntity<Map<KnowledgeProviderType, Map<String, ProviderHealth>>> health() {
        return ResponseEntity.ok(registry.healthSnapshot());
    }
}
