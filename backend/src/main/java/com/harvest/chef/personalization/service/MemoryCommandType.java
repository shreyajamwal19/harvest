package com.harvest.chef.personalization.service;

/** Every deterministic memory command Harvest recognizes - never routed through the LLM. */
public enum MemoryCommandType {
    REMEMBER_LIKE,
    REMEMBER_DISLIKE,
    REMEMBER_GENERAL,
    FORGET,
    SHOW_PREFERENCES,
    SHOW_HISTORY,
    RESET_PROFILE,
    CLEAR_HISTORY
}
