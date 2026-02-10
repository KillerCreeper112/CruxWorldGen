package killercreepr.cruxworldgen.api.world

import killercreepr.crux.api.data.CruxKeyed
import killercreepr.crux.core.Crux
import killercreepr.cruxworldgen.test.AbyssBiome
import net.kyori.adventure.key.Key

interface GenerationStage : CruxKeyed {
    companion object{
        val TERRAIN = SimpleGenerationStage(Crux.key("terrain"))
        val CARVING = SimpleGenerationStage(Crux.key("carving"))
        val SURFACE = SimpleGenerationStage(Crux.key("surface"))
        val DECORATION = SimpleGenerationStage(Crux.key("decoration"))

        fun values() : Array<GenerationStage> = arrayOf(TERRAIN, CARVING, SURFACE, DECORATION)
    }

    /*TERRAIN,      // Base terrain height
    CARVING,      // Caves, rifts, tunnels
    SURFACE,      // Surface blocks (grass, fungi, ash)
    DECORATION    // Features: trees, pillars, pools*/
}

class SimpleGenerationStage(val key : Key) : GenerationStage {
    override fun key(): Key = key
}