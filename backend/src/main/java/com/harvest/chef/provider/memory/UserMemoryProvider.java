package com.harvest.chef.provider.memory;

import java.util.List;

/** Durable-ish user context pulled from past conversations. Full profile-fact extraction is a later phase. */
public interface UserMemoryProvider {
    List<String> recentContextFor(Long userId, Long excludingSessionId);
}
