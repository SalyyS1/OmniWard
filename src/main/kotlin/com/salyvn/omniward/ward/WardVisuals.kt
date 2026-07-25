package com.salyvn.omniward.ward

import com.salyvn.omniward.model.Ward
import com.tcoded.folialib.FoliaLib
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle

/**
 * Renders ward visuals and manages the (optional) physical marker block.
 *
 * All block reads/writes are dispatched onto the block's owning region thread via FoliaLib,
 * so this class is safe under both Paper and Folia. Particle spawns are also location-scheduled.
 */
class WardVisuals(private val foliaLib: FoliaLib) {

    /** Place the marker block at the ward location on its region thread. */
    fun placeMarker(ward: Ward, material: Material) {
        val loc = ward.location.block.location
        foliaLib.scheduler.runAtLocation(loc) {
            loc.block.type = material
        }
    }

    /** Restore the original block (if one was replaced) on its region thread. */
    fun removeMarker(ward: Ward) {
        val original = ward.originalMaterial ?: return
        val loc = ward.location.block.location
        foliaLib.scheduler.runAtLocation(loc) {
            // Only restore if the block is still our marker-ish; always safe to set back to original.
            loc.block.type = original
        }
    }

    /**
     * Draw a horizontal particle ring at the ward's radius. [contested] switches the particle
     * to the capture colour. Runs on the ward location's region thread.
     */
    fun drawRing(ward: Ward, points: Int, ownerParticle: Particle, captureParticle: Particle, contested: Boolean) {
        val center = ward.location.clone().add(0.0, 0.5, 0.0)
        val world = center.world ?: return
        val radius = ward.radius
        val particle = if (contested) captureParticle else ownerParticle
        foliaLib.scheduler.runAtLocation(center) {
            var i = 0
            while (i < points) {
                val angle = 2.0 * Math.PI * i / points
                val x = center.x + radius * Math.cos(angle)
                val z = center.z + radius * Math.sin(angle)
                val point = Location(world, x, center.y, z)
                world.spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0)
                i++
            }
        }
    }
}
