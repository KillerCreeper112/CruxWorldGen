package killercreepr.cruxworldgen.test

import org.bukkit.Material

fun interface SurfaceRule {
    fun resolve(x: Int, y: Int, z: Int, density: Double, biome : AbyssBiome): Material
}
