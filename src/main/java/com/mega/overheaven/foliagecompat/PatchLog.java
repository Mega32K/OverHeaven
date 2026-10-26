package com.mega.overheaven.foliagecompat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PatchLog {
    public static final Logger LOGGER = LogManager.getLogger("OverHeaven Foliage Compat");
    public static final boolean DIAGNOSTICS = Boolean.getBoolean("overheaven_foliage_compat.diagnostics");

    private PatchLog() { }
}
