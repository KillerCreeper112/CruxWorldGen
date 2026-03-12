package killercreepr.cruxworldgen.bukkit

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import killercreepr.crux.core.plugin.CruxPlugin
import killercreepr.crux.core.util.CruxWorldUtil
import killercreepr.cruxworldgen.api.noise.NoiseAutoInstaller
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.bukkit.generation.BukkitGenerationChunkGenerator
import killercreepr.cruxworldgen.bukkit.generation.WorldDetails
import killercreepr.cruxworldgen.core.biome.volumetric.VolumetricBiomeRegistry
import killercreepr.cruxworldgen.core.decor.SimpleDecorationPipeline
import killercreepr.cruxworldgen.core.decor.SimplePropPointGrid
import killercreepr.cruxworldgen.core.feature.SimpleFeaturePipeline
import killercreepr.cruxworldgen.core.generation.SimpleGenerationPipeline
import killercreepr.cruxworldgen.core.generation.chunk.SimpleChunkSampler
import killercreepr.cruxworldgen.core.noise.BaseNoiseModule
import killercreepr.cruxworldgen.core.noise.SimpleNoiseBank
import killercreepr.cruxworldgen.core.structure.SimpleStructurePipeline
import killercreepr.cruxworldgen.core.structure.SimpleStructureRegistry
import killercreepr.cruxworldgen.core.zone.SimpleZoneRegistry
import killercreepr.cruxworldgen.crux.block.CruxBlockResolver
import killercreepr.cruxworldgen.crux.block.CruxBlockSectionReader
import killercreepr.cruxworldgen.test.zone.TestZone
import org.bukkit.WorldCreator
import org.bukkit.entity.Player

class CruxWorldGenPlugin : CruxPlugin() {
  override fun onLoad() {
    super.onLoad()
    BukkitBlockAdapter.multiResolver().registerResolve(
      "minecraft", BukkitBlockResolver.INSTANCE
    )
    BukkitBlockAdapter.multiResolver().registerResolve(
      "crux", CruxBlockResolver.INSTANCE
    )

    BukkitBlockAdapter.multiReader().registerReader("crux", CruxBlockSectionReader.INSTANCE)

    lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS){ event ->
      event.registrar()
        .register(
          Commands.literal("cgentest")
            .then(
              Commands.argument("name", StringArgumentType.string())
                .executes { ctx ->
                  val sender = ctx.getSource().sender

                  val name = ctx.getArgument("name", String::class.java)
                  val got = server.getWorld(name)
                  if(got != null){
                    for (player in got.players) {
                      player.teleport(server.getWorld("world")!!.spawnLocation)
                    }
                  }
                  CruxWorldUtil.deleteWorld(name)

                  val zones = SimpleZoneRegistry(
                    listOf(TestZone())
                  )
                  val volBiomes = VolumetricBiomeRegistry(listOf(
                    /*SmoothSkyIslandsV2(
                      yRange = RelativeHeightFilter(0.4f, 0.9f)
                    ),*/
                    //EldritchIslands(yRange = RelativeHeightFilter(0.65f, 0.9f)),
                    //GlacialCaverns(yRange = RelativeHeightFilter(0.1f, 0.4f))
                  ))
                  val structureRegistry = SimpleStructureRegistry(listOf())
                  val generation = SimpleGenerationPipeline(
                    zones,
                    volBiomes
                  )
                  val decorations = SimpleDecorationPipeline(SimplePropPointGrid())
                  val structures = SimpleStructurePipeline(structureRegistry)

                  val seed = -3821261185915076750L//todo temp seed CruxMath.random().nextLong()
                  val noise = SimpleNoiseBank(seed)

                  BaseNoiseModule.install(noise)

                  //auto install noises
                  NoiseAutoInstaller(noise).apply {
                    installAllFromZones(zones)
                    installFromAll(volBiomes)
                  }

                  val worldDetails = WorldDetails(
                    62,
                    16, 16
                  )

                  val features = SimpleFeaturePipeline(listOf())

                  val generator = BukkitGenerationChunkGenerator(
                    generation,
                    decorations,
                    structures,
                    noise,
                    worldDetails,
                    features,
                    chunkSampler = SimpleChunkSampler(
                      generation, noise, worldDetails, biomeCellSize = 4, mediumCellSize = 4
                    )
                  )
                  val world = WorldCreator(name).generator(
                    generator
                  ).seed(seed).createWorld()
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