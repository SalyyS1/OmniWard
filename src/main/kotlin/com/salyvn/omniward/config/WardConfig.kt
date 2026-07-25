package com.salyvn.omniward.config

import com.salyvn.omniward.model.AuraEffect
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType

/**
 * Loads and holds all tunable settings from config.yml. Hot-reloadable via [load].
 *
 * Every getter is a plain property re-populated on [load] so callers always read the latest values
 * after a `/ward reload`. Invalid material / effect / particle names are logged and skipped or
 * defaulted rather than throwing, so a single typo never disables the plugin.
 */
class WardConfig(private val plugin: JavaPlugin) {

    // --- ward geometry / limits ---
    var radius: Double = 8.0; private set
    var maxWardsPerPlayer: Int = 2; private set
    var maxTotalWards: Int = 50; private set
    var lifetimeSeconds: Int = 300; private set
    var cooldownSeconds: Int = 30; private set

    // --- capture mechanics ---
    var captureSeconds: Int = 5; private set
    var captureMode: String = "FLIP"; private set // FLIP or DESTROY
    var tickIntervalTicks: Long = 10L; private set

    // --- aura ---
    var auraEffects: List<AuraEffect> = emptyList(); private set
    var auraDurationTicks: Int = 40; private set

    // --- visuals ---
    var markerBlock: Material? = Material.BEACON; private set
    var placeBlock: Boolean = true; private set
    var ownerParticle: Particle = Particle.HAPPY_VILLAGER; private set
    var captureParticle: Particle = Particle.ANGRY_VILLAGER; private set
    var particlePoints: Int = 24; private set

    // --- messages ---
    private val messages = HashMap<String, String>()

    fun load() {
        plugin.reloadConfig()
        val c = plugin.config

        radius = c.getDouble("ward.radius", 8.0).coerceIn(1.0, 64.0)
        maxWardsPerPlayer = c.getInt("ward.max-per-player", 2).coerceIn(1, 100)
        maxTotalWards = c.getInt("ward.max-total", 50).coerceIn(1, 10000)
        lifetimeSeconds = c.getInt("ward.lifetime-seconds", 300).coerceAtLeast(0)
        cooldownSeconds = c.getInt("ward.cooldown-seconds", 30).coerceAtLeast(0)

        captureSeconds = c.getInt("capture.seconds", 5).coerceAtLeast(1)
        captureMode = c.getString("capture.mode", "FLIP")!!.uppercase().let {
            if (it == "DESTROY") "DESTROY" else "FLIP"
        }
        tickIntervalTicks = c.getLong("capture.tick-interval-ticks", 10L).coerceIn(1L, 100L)

        auraDurationTicks = c.getInt("aura.duration-ticks", 40).coerceAtLeast(20)
        auraEffects = parseAuraEffects(c.getStringList("aura.effects"))

        placeBlock = c.getBoolean("visual.place-block", true)
        markerBlock = c.getString("visual.marker-block", "BEACON")?.let { parseMaterial(it) }
        ownerParticle = parseParticle(c.getString("visual.owner-particle", "HAPPY_VILLAGER"), Particle.HAPPY_VILLAGER)
        captureParticle = parseParticle(c.getString("visual.capture-particle", "ANGRY_VILLAGER"), Particle.ANGRY_VILLAGER)
        particlePoints = c.getInt("visual.particle-points", 24).coerceIn(4, 128)

        messages.clear()
        val msgSection = c.getConfigurationSection("messages")
        if (msgSection != null) {
            for (key in msgSection.getKeys(false)) {
                messages[key] = msgSection.getString(key, "") ?: ""
            }
        }
    }

    /** Ticks of channel required to complete a capture (capture seconds ÷ tick interval). */
    fun requiredCaptureTicks(): Int {
        val ticks = captureSeconds * 20L
        return (ticks / tickIntervalTicks).toInt().coerceAtLeast(1)
    }

    fun message(key: String, fallback: String): String = messages[key] ?: fallback

    private fun parseAuraEffects(raw: List<String>): List<AuraEffect> {
        val result = ArrayList<AuraEffect>()
        for (entry in raw) {
            // format: "TYPE:amplifier" e.g. "SPEED:1" (amplifier is 1-based in config, 0-based internally)
            val parts = entry.split(":")
            val typeName = parts[0].trim().uppercase()
            val type = PotionEffectType.getByName(typeName)
            if (type == null) {
                plugin.logger.warning("Unknown potion effect '$typeName' in aura.effects, skipping.")
                continue
            }
            val level = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 1 else 1
            val amplifier = (level - 1).coerceAtLeast(0)
            result.add(AuraEffect(type, amplifier))
        }
        return result
    }

    private fun parseMaterial(name: String): Material? {
        val mat = Material.matchMaterial(name.uppercase())
        if (mat == null || !mat.isBlock) {
            plugin.logger.warning("Invalid marker-block '$name', ward will use particles only.")
            return null
        }
        return mat
    }

    private fun parseParticle(name: String?, fallback: Particle): Particle {
        if (name == null) return fallback
        return runCatching { Particle.valueOf(name.uppercase()) }.getOrElse {
            plugin.logger.warning("Invalid particle '$name', using ${fallback.name}.")
            fallback
        }
    }
}
