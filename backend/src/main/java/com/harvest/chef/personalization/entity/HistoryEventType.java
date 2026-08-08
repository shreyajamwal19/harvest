package com.harvest.chef.personalization.entity;

/**
 * Every kind of recipe interaction the cooking-history log can record.
 * Only a subset of these are currently emitted by the conversational
 * pipeline (see {@code CookingHistoryService}) - the rest exist so a
 * future explicit "mark as cooked / save / reject" surface (Phase 6B or
 * later) can slot straight into the same history model without another
 * schema change.
 */
public enum HistoryEventType {
    VIEWED,
    SELECTED,
    COOKED,
    SAVED,
    REJECTED,
    REPEATED,
    ADAPTED
}
