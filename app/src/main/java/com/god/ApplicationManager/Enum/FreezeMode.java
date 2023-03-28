package com.god.ApplicationManager.Enum;

public enum FreezeMode {
    ALWAYS,

    /**
     * This app will never be frozen, even if it has been running in background for whatever time.
     */
    NEVER,

    /**
     * This app will be frozen if it was not used for a specific time but is running in background.
     */
    WHEN_INACTIVE

}
