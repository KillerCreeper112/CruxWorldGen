package killercreepr.cruxworldgen.test2

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.test2.biome.decoration.DecorationContext
import killercreepr.cruxworldgen.test2.biome.decoration.DecoratorFeature
import org.bukkit.Material

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

    override val decorators = mutableListOf<DecoratorFeature>()

    // NEW: default decorate implementation
    override fun decorate(chunkX: Int, chunkZ: Int, context: DecorationContext) {
        // default: run each decorator using the provided origin==target
        for (d in decorators) {
            d.placeFromOrigin(chunkX, chunkZ, chunkX, chunkZ, context)
        }
    }




    // Noise generators
    val heightNoise: CruxNoise = CruxNoise.fast()
    val terrainNoiseGen: CruxNoise = CruxNoise.fast(12345)

    // Base terrain height (2D)
    override fun baseHeight(x: Int, z: Int): Double {
        return 64.0 + heightNoise.noise(x.toDouble(), z.toDouble()) * 20.0
    }

    // 3D terrain noise for overhangs, floating chunks
    override fun terrainNoise(x: Int, y: Int, z: Int): Double {
        return terrainNoiseGen.noise(x.toDouble(), y.toDouble(), z.toDouble()) * 5.0
    }

    // Vertical bias to favor certain heights
    override fun verticalBias(y: Int): Double = 0.0
    override fun habitatWeight(sx: Int, sy: Int, sz: Int): Double {
        TODO("Not yet implemented")
    }
}
