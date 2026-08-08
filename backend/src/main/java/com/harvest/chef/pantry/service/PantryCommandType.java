package com.harvest.chef.pantry.service;

/** Every deterministic pantry command Harvest recognizes - never routed through the LLM. */
public enum PantryCommandType {
    ADD,
    REMOVE,
    CONSUME,
    SHOW,
    CLEAR
}
