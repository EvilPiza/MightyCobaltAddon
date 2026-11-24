package com.mightystore.pathfinder.helper

import net.minecraft.core.BlockPos

data class Node(
  val step: PathStep,
  var gCost: Double,
  var hCost: Double,
  var parent: Node?
) {
  val pos: BlockPos
    get() = step.target

  var fCost: Double = gCost + hCost
}
