package com.salyvn.omniward

import com.salyvn.omniward.command.WardCommand
import com.salyvn.omniward.config.WardConfig
import com.salyvn.omniward.listener.WardCaptureListener
import com.salyvn.omniward.ward.WardManager
import com.tcoded.folialib.FoliaLib
import org.bukkit.plugin.java.JavaPlugin

/**
 * OmniWard entry point.
 *
 * Wires together the config, the ward manager (which owns the repeating capture/aura task) and the
 * command + listener. `/ward reload` re-reads config.yml and safely restarts the repeating task.
 */
class OmniWardPlugin : JavaPlugin() {

    lateinit var foliaLib: FoliaLib
        private set
    lateinit var wardConfig: WardConfig
        private set
    lateinit var wardManager: WardManager
        private set

    override fun onEnable() {
        instance = this
        foliaLib = FoliaLib(this)

        saveDefaultConfig()
        wardConfig = WardConfig(this)
        wardConfig.load()

        wardManager = WardManager(this, foliaLib, wardConfig)
        wardManager.start()

        server.pluginManager.registerEvents(WardCaptureListener(wardManager), this)
        WardCommand(this).register()

        logger.info("OmniWard enabled. Aura effects loaded: ${wardConfig.auraEffects.size}.")
    }

    override fun onDisable() {
        if (::wardManager.isInitialized) wardManager.shutdown()
        logger.info("OmniWard disabled.")
    }

    /** Hot-reload: re-read config.yml, then restart the repeating task with the new settings. */
    fun reload() {
        wardConfig.load()
        wardManager.reload(wardConfig)
        logger.info("OmniWard reloaded.")
    }

    companion object {
        @JvmStatic
        lateinit var instance: OmniWardPlugin
            private set
    }
}
