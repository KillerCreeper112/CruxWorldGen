## Overview

One of the first projects I built from scratch to gain more experience with Kotlin.
The basic premise of this terrain generation is that biomes control the shape.

A 2D biome decides the base shape of a chunk. 3D biomes can then alter that shape. The borders of each biomes are blended to make transitions a little more seamless.

This project was designed to function as a standalone system that other projects can hook into and use. It has built-in support for [Bukkit](https://dev.bukkit.org/) and [Crux](https://github.com/KillerCreeper112/Crux2.0/tree/master/CruxBlocks).

This repository was originally built for personal use and was not intended to be public, so some commit history and internal structure may be rough around the edges.
