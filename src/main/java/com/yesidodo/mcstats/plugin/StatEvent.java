package com.yesidodo.mcstats.plugin;

import java.util.UUID;

public record StatEvent(UUID uuid, String metric, long delta, long timestampUtc) {
}
