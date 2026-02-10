package killercreepr.cruxworldgen.test

interface AbyssBiome {
    val densityModifiers: List<DensityModifier>
    val surfaceRule: SurfaceRule

    val temp : Double
    val humidity : Double
    val continental : Double
    val erosion : Double
    val weirdness : Double
    val scale : Double
        get() = 1.0

    // New properties for biome-specific base density control
    /*val offsetX: Double
    val offsetZ: Double
    val continentalScale: Double
    val detailScale: Double*/
}

