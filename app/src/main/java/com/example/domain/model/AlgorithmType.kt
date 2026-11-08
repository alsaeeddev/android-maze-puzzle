package com.example.domain.model

/**
 * Procedural generation algorithm used for the maze layout.
 * Provides distinct maze characteristics (long winding corridors in Backtracker,
 * high branching in Prim's, tree-like corridors in Kruskal's).
 */
enum class AlgorithmType {
    BACKTRACKER,
    PRIM,
    KRUSKAL
}
