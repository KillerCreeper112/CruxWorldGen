package killercreepr.cruxworldgen.test2

import killercreepr.cruxgeneration.util.CruxNoise
import org.bukkit.Material
import kotlin.math.floor

open class BaseBiome : VolumetricBiome {

    val features = mutableListOf<BiomeFeature>()

    override fun density(x:Int,y:Int,z:Int): Double {
        var d = baseHeight(x,z) - y + terrainNoise(x,y,z)
        for (f in features) d = f.modifyDensity(x,y,z,d)
        d += verticalBias(y)
        return d
    }

    override fun getBlock(x:Int,y:Int,z:Int,density:Double): Material {
        for (f in features) {
            val b = f.getBlock(x,y,z,density)
            if (b != null) return b
        }
        return Material.AIR
    }

    open fun isSurfaceVoxel(x:Int,y:Int,z:Int,density:Double): Boolean {
        val approx = kotlin.math.floor(baseHeight(x,z)).toInt()
        return density > 0.0 && y >= approx - 1 && y <= approx + 2
    }


    // Noise generators
    protected val heightNoise: CruxNoise = CruxNoise.fast()
    protected val terrainNoiseGen: CruxNoise = CruxNoise.fast(12345)

    // Base terrain height (2D)
    override fun baseHeight(x: Int, z: Int): Double {
        return 64.0 + heightNoise.noise(x.toDouble(), z.toDouble()) * 20.0
    }
    /*open fun isSurfaceVoxel(x: Int, y: Int, z: Int, density: Double): Boolean {
        val approxSurface = floor(baseHeight(x, z)).toInt()
        // allow a small band so mesas with strata still paint correctly
        return density > 0.0 && y >= approxSurface - 1 && y <= approxSurface + 2
    }*/


    // 3D terrain noise for overhangs, floating chunks
    override fun terrainNoise(x: Int, y: Int, z: Int): Double {
        return terrainNoiseGen.noise(x.toDouble(), y.toDouble(), z.toDouble()) * 5.0
    }

    // Default cave strength (can be overridden)
    override fun caveStrength(): Double = 0.5

    // Vertical bias to favor certain heights
    override fun verticalBias(y: Int): Double = 0.0

    // Compute density for a voxel
    /*override fun density(x: Int, y: Int, z: Int): Double {
        var d = baseHeight(x, z) - y + terrainNoise(x, y, z)
        for (feature in features) {
            d = feature.modifyDensity(x, y, z, d)
        }
        d += verticalBias(y)
        return d
    }

    // Decide which block to place based on features and density
    override fun getBlock(x: Int, y: Int, z: Int, density: Double): Material {
        for (feature in features) {
            val block = feature.getBlock(x, y, z, density)
            if (block != null) return block
        }
        return Material.AIR//if (density > 0) Material.STONE else Material.AIR
    }*/
}
