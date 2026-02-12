package killercreepr.cruxworldgen.core

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import killercreepr.crux.core.plugin.CruxPlugin
import killercreepr.crux.core.util.CruxWorldUtil
import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.core.world.CruxNoiseProvider
import killercreepr.cruxworldgen.test.*
import killercreepr.cruxworldgen.test6.BukkitGen
import killercreepr.cruxworldgen.test6.DecorationPipeline
import killercreepr.cruxworldgen.test6.GenerationPipeline
import killercreepr.cruxworldgen.test6.prop.PropPointGrid
import killercreepr.cruxworldgen.test6.zone.TestZone
import killercreepr.cruxworldgen.test6.zone.ZoneRegistry
import org.bukkit.WorldCreator
import org.bukkit.entity.Player

class CruxWorldGenPlugin : CruxPlugin() {
  override fun onLoad() {
    super.onLoad()
    lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS){ event ->
      event.registrar()
        .register(
          Commands.literal("cgentest")
            .then(
              Commands.argument("name", StringArgumentType.string())
                .executes { ctx ->
                  val sender = ctx.getSource().sender

                  val noise = CruxNoiseProvider(
                    CruxNoise.fast(Math.random().toInt())
                      .frequency(0.005)
                      .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                      .fractalType(CruxNoise.FractalType.FBm)
                      .fractalOctaves(5),
                    CruxNoise.fast(Math.random().toInt())
                      .frequency(0.005)
                      .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                      .fractalType(CruxNoise.FractalType.FBm)
                      .fractalOctaves(5)
                  )

                  /*val plague = PlagueMireBiome(noise)
                  val charred = CharredWastesBiome(
                    CruxNoiseProvider(
                      CruxNoise.fast(Math.random().toInt())
                        .frequency(0.001)
                        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                        .fractalType(CruxNoise.FractalType.FBm)
                        .fractalOctaves(3),
                      CruxNoise.fast(Math.random().toInt())
                        .frequency(0.001)
                        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                        .fractalType(CruxNoise.FractalType.FBm)
                        .fractalOctaves(3)
                    )
                  )*/

                  val plague = PlagueMireBiome(noise)
                  val charred = CharredWastesBiome(noise)

                  val biomeResolver = ExpandableAbyssBiomeResolver(
                    listOf(
                      BiomeEntry(plague) { x, y, z, n -> (n.noise2D(x*0.0008, z*0.0008) + 1.0)/2.0 },
                      BiomeEntry(charred) { x, y, z, n -> 1.0 - (n.noise2D(x*0.0008, z*0.0008) + 1.0)/2.0 }
                    ),
                    noise
                  )

                  val climateNoise = ClimateNoiseProvider(
                    temperatureNoise = CruxNoiseProvider(
                      CruxNoise.fast(1234)
                        .frequency(0.004)
                        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                        .fractalType(CruxNoise.FractalType.FBm)
                        .fractalOctaves(4)
                    ),
                    humidityNoise = CruxNoiseProvider(
                      CruxNoise.fast(5678)
                        .frequency(0.004)
                        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                        .fractalType(CruxNoise.FractalType.FBm)
                        .fractalOctaves(4)
                    ),
                    continentalNoise = CruxNoiseProvider(
                      CruxNoise.fast(9012)
                        .frequency(0.0012)
                        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                        .fractalType(CruxNoise.FractalType.FBm)
                        .fractalOctaves(5)
                    ),
                    erosionNoise = CruxNoiseProvider(
                      CruxNoise.fast(3456)
                        .frequency(0.01)
                        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                        .fractalType(CruxNoise.FractalType.FBm)
                        .fractalOctaves(3)
                    ),
                    weirdNoise = CruxNoiseProvider(
                      CruxNoise.fast(7890)
                        .frequency(0.02)
                        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
                        .fractalType(CruxNoise.FractalType.Ridged)
                        .fractalOctaves(2)
                    )
                  )


                  val generator = ClimateAbyssTerrainGenerator(
                    noise,
                    climateNoise,
                    listOf(
                      plague, charred
                    )
                  )



                  val name = ctx.getArgument("name", String::class.java)
                  val got = server.getWorld(name)
                  if(got != null){
                    for (player in got.players) {
                      player.teleport(server.getWorld("world")!!.spawnLocation)
                    }
                  }
                  CruxWorldUtil.deleteWorld(name)
                  /*CruxChunkGenerator(
                      BiomeRegistry(123456)
                    )*/

                  val world = WorldCreator(name).generator(
                    BukkitGen(
                      GenerationPipeline(
                        ZoneRegistry(listOf(
                          TestZone()
                        ))
                      ),
                      DecorationPipeline(PropPointGrid())
                    )
                  ).createWorld()
                  (sender as? Player)?.teleport(world!!.spawnLocation)
                  sender.sendMessage("Deeeed it")
                  1
                }
            )
            .build()
        )
    }
  }
}