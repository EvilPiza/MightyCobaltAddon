package com.mighty.pathfinder

import com.mighty.pathfinder.helper.Node
import com.mighty.pathfinder.helper.NodeData
import net.minecraft.block.*
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShapes
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

class Pathfinder() {
  private val mc = MinecraftClient.getInstance()

  private lateinit var goal: Node
  private lateinit var start: Node

  private val openQueue: PriorityQueue<Node> = PriorityQueue(
    compareBy<Node> { it.fCost }
      .thenBy { it.hCost }
  )
  private val openSet: HashMap<BlockPos, Node> = HashMap()
  private val closedSet: HashMap<BlockPos, Node> = HashMap()
  private val cachedHeuristics: HashMap<BlockPos, Double> = HashMap()
  private val cachedPenalties: HashMap<BlockPos, Double> = HashMap()
  private val stateCache: HashMap<BlockPos, BlockState> = HashMap()
  private val directions: ArrayList<BlockPos> = ArrayList()

  init {
    directions.apply {
      add(BlockPos(1, 0, 0))
      add(BlockPos(-1, 0, 0))
      add(BlockPos(0, 0, 1))
      add(BlockPos(0, 0, -1))
    }
  }

  private fun resetSearchState() {
    openQueue.clear()
    openSet.clear()
    closedSet.clear()
    cachedHeuristics.clear()
    cachedPenalties.clear()
    stateCache.clear()
  }

  fun calculatePath(beginData: NodeData, end: NodeData): List<Node>? {
    resetSearchState()

    start = Node(beginData, 0.0, Double.MAX_VALUE, null)
    goal = Node(end, Double.MAX_VALUE, 0.0, null)

    start.hCost = getHeuristic(beginData)
    start.fCost = start.gCost + start.hCost

    openQueue.add(start)
    openSet[start.data.pos] = start

    while (openQueue.isNotEmpty()) {
      val current = openQueue.poll()
      val currentPos = current.data.pos

      if (openSet[currentPos] !== current) {
        continue
      }
      openSet.remove(currentPos)

      if (currentPos == goal.data.pos) {
        goal.parent = current
        return smoothPath(reconstructPath(goal))
      }

      closedSet[currentPos] = current

      for (neighbourData in getNeighbours(current.data)) {
        if (closedSet.containsKey(neighbourData.pos)) {
          continue
        }

        val newGCost = current.gCost + neighbourData.movement + getPenalty(neighbourData)
        val existingNeighbour = openSet[neighbourData.pos]

        if (existingNeighbour == null) {
          val newNode = Node(neighbourData, newGCost, getHeuristic(neighbourData), current)
          newNode.fCost = newNode.gCost + newNode.hCost
          openQueue.add(newNode)
          openSet[neighbourData.pos] = newNode
        } else if (newGCost < existingNeighbour.gCost) {
          val updatedNode = Node(neighbourData, newGCost, existingNeighbour.hCost, current)
          updatedNode.fCost = updatedNode.gCost + updatedNode.hCost
          openQueue.add(updatedNode)
          openSet[neighbourData.pos] = updatedNode
        }
      }

      for (direction in directions) {
        val goalCheck = currentPos.add(direction)
        if (goalCheck == goal.data.pos) {
          goal.parent = current
          return smoothPath(reconstructPath(goal))
        }
      }
    }

    return null
  }

  private fun getCachedState(pos: BlockPos): BlockState {
    return stateCache.getOrPut(pos) {
      mc.world?.getBlockState(pos) ?: Blocks.AIR.defaultState
    }
  }

  fun isWalkable(pos: BlockPos): Boolean {
    var state = getCachedState(pos)
    var block = state.block

    if (block is AbstractRailBlock) {
      state = getCachedState(pos.down())
      block = state.block
    }

    if (block is FenceBlock ||
      block is FenceGateBlock ||
      block.translationKey.contains("fence")) {
      return false
    }

    state = getCachedState(pos)

    if (block !is FluidBlock &&
      (state.blocksMovement() || Registries.BLOCK.getRawId(block) == 78)) {
      return hasEnoughHeadroom(pos)
    }

    return false
  }

  private fun hasEnoughHeadroom(pos: BlockPos): Boolean {
    val stateAbove1 = getCachedState(pos.up())
    val stateAbove2 = getCachedState(pos.up(2))
    val blockAbove1 = stateAbove1.block
    val blockAbove2 = stateAbove2.block

    if (blockAbove1 is FenceBlock || blockAbove1 is FenceGateBlock ||
      blockAbove2 is FenceBlock || blockAbove2 is FenceGateBlock) {
      return false
    }

    if (blockAbove1 is AbstractRailBlock || blockAbove2 is AbstractRailBlock) {
      return true
    }

    val requiredClearance = 1.8
    var availableClearance = 0.0

    val shapeAbove1 = stateAbove1.getCollisionShape(mc.world, pos.up())
    if (blockAbove1 == Blocks.AIR) {
      availableClearance += 1.0
    } else if (!shapeAbove1.isEmpty) {
      availableClearance += (1.0 - shapeAbove1.boundingBox.maxY)
    }

    val shapeAbove2 = stateAbove2.getCollisionShape(mc.world, pos.up(2))
    if (blockAbove2 == Blocks.AIR) {
      availableClearance += 1.0
    } else if (!shapeAbove2.isEmpty) {
      availableClearance += (1.0 - shapeAbove2.boundingBox.maxY)
    }

    return availableClearance >= requiredClearance
  }

  fun getNeighbours(data: NodeData): ArrayList<NodeData> {
    val neighbours = ArrayList<NodeData>()
    val originState = getCachedState(data.pos)
    val originShape = originState.getCollisionShape(mc.world, data.pos)
    val originTopY = if (originShape.isEmpty) 0.0 else originShape.boundingBox.maxY

    for (offset in directions) {
      val neighbourColumn = data.pos.add(offset)

      if (neighbourColumn == goal.data.pos) {
        neighbours.add(NodeData(neighbourColumn, 2.0, false))
        continue
      }

      var airStreak = 0
      var totalChecked = 0

      for (y in 1 downTo -6) {
        val candidatePos = neighbourColumn.add(0, y, 0)
        val candidateState = getCachedState(candidatePos)
        val candidateBlock = candidateState.block

        val candidateShape = candidateState.getCollisionShape(mc.world, candidatePos)
        val candidateTopY = if (candidateShape.isEmpty) 0.0 + y else candidateShape.boundingBox.maxY + y
        val requiredJump = candidateTopY - originTopY

        if (requiredJump > 1.2) {
          continue
        }

        if (isWalkable(candidatePos)) {
          if (y < -1 && airStreak != totalChecked) {
            break
          }

          val candidateHeight = if (candidateShape.isEmpty) 0.0 else candidateShape.boundingBox.maxY
          var cost = if (y == 1 && candidateHeight != 0.5) 4.0 else 2.0
          if (y < -1) {
            cost *= abs(y).toDouble()
          }

          var landingPos = candidatePos
          if (candidateBlock is AbstractRailBlock) {
            landingPos = candidatePos.down()
          }

          neighbours.add(NodeData(landingPos, cost, false))
          break
        }

        totalChecked++
        if (candidateBlock == Blocks.AIR) {
          airStreak++
        }
      }
    }

    return neighbours
  }

  private fun getPenalty(data: NodeData): Double {
    cachedPenalties[data.pos]?.let { return it }

    var cost = 0.0
    val belowBlock = getCachedState(data.pos).block

    for (x in -1..1) {
      for (z in -1..1) {
        val checkPos = data.pos.add(x, 1, z)
        val checkBlock = getCachedState(checkPos).block

        val id = Registries.BLOCK.getRawId(checkBlock)
        if (id != 0 && id != 78 && id != 80) {
          cost += 2.0
        }

        if (belowBlock == Blocks.AIR && checkBlock == Blocks.AIR) {
          cost += 2.0
        }
      }
    }

    cachedPenalties[data.pos] = cost
    return cost
  }

  private fun getHeuristic(data: NodeData): Double {
    return cachedHeuristics.getOrPut(data.pos) {
      val pos = data.pos
      val dX = (pos.x - goal.data.pos.x).toDouble()
      val dY = (pos.y - goal.data.pos.y).toDouble()
      val dZ = (pos.z - goal.data.pos.z).toDouble()
      sqrt(dX * dX + dY * dY + dZ * dZ)
    }
  }

  private fun reconstructPath(currentNode: Node): ArrayList<Node> {
    val path = ArrayList<Node>()
    var node: Node? = currentNode

    while (node != null) {
      path.add(0, node)
      node = node.parent
    }

    return path
  }

  private fun smoothPath(nodes: List<Node>?): List<Node>? {
    if (nodes == null || nodes.size <= 2) {
      return nodes
    }

    val smoothed = ArrayList<Node>()
    smoothed.add(nodes[0])

    var current = 0

    while (current < nodes.size - 1) {
      var nextIndex = current + 1
      val maxLookAhead = minOf(20, nodes.size - current - 1)

      for (lookAhead in maxLookAhead downTo 1) {
        val candidateIndex = current + lookAhead
        if (candidateIndex >= nodes.size) continue

        val currentPos = nodes[current].data.pos
        val candidatePos = nodes[candidateIndex].data.pos

        val yDiff = candidatePos.y - currentPos.y
        var canSkip = true

        if (yDiff < -1) {
          val fromState = getCachedState(currentPos)
          val toState = getCachedState(candidatePos)

          val fromHeight = fromState.getCollisionShape(mc.world, currentPos).boundingBox.maxY
          val toHeight = toState.getCollisionShape(mc.world, candidatePos).boundingBox.maxY

          val hasSlabs = abs(fromHeight - 0.5) < 0.01 || abs(toHeight - 0.5) < 0.01

          if (hasSlabs && lookAhead > 3) {
            canSkip = false
          }
        }

        if (canSkip && hasLineOfSight(currentPos, candidatePos)) {
          nextIndex = candidateIndex
          break
        }
      }

      current = nextIndex
      smoothed.add(nodes[current])
    }

    return smoothed
  }

  private fun hasLineOfSight(from: BlockPos, to: BlockPos): Boolean {
    val dx = (to.x - from.x).toDouble()
    val dy = (to.y - from.y).toDouble()
    val dz = (to.z - from.z).toDouble()
    val distance = sqrt(dx * dx + dy * dy + dz * dz)

    val steps = ceil(distance * 2).toInt()

    for (i in 0..steps) {
      val t = i / steps.toDouble()
      val x = floor(from.x + dx * t).toInt()
      val y = floor(from.y + dy * t).toInt()
      val z = floor(from.z + dz * t).toInt()

      val checkPos = BlockPos(x, y + 1, z)
      val state = getCachedState(checkPos)
      val block = state.block

      if (block != Blocks.AIR &&
        state.blocksMovement() &&
        state.getCollisionShape(mc.world, checkPos) == VoxelShapes.fullCube() &&
        block !is FluidBlock) {
        return false
      }

      val belowPos = BlockPos(x, y, z)
      if (!isWalkable(belowPos)) {
        return false
      }
    }

    return true
  }
}
