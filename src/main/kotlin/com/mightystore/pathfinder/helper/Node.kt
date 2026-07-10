package com.mightystore.pathfinder.helper

data class Node(
  val data: NodeData,
  var gCost: Double,
  var hCost: Double,
  var parent: Node?
) {
  var fCost: Double = gCost + hCost
}
