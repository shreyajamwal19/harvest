package com.harvest.chef.personalization.entity;

/** Where a stored preference came from - drives how confidently it's trusted. */
public enum PreferenceSource {
    /** The user stated it directly ("I'm vegetarian", "remember I hate mushrooms"). */
    EXPLICIT,
    /** Learned gradually from behaviour (repeated recipe selections, cooking history). */
    INFERRED
}
