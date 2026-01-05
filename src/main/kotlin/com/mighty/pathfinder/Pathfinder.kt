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
import org.cobalt.Cobalt
import org.cobalt.api.pathfinder.IPathExec

class Pathfinder() : IPathExec {
  private val mc = MinecraftClient.getInstance()

  private lateinit var goal: Node
  private lateinit var start: Node
  private val openQueue: PriorityQueue<Node>
  private val openSet: HashMap<BlockPos, Node>
  private val closestSet: HashMap<BlockPos, Node>
  private val customSet: HashMap<BlockPos, NodeData>
  private val cachedCosts: HashMap<BlockPos, Double>
  private val cachedPenalties: HashMap<BlockPos, Double>
  private val stateCache: HashMap<BlockPos, BlockState>
  private val directions: ArrayList<BlockPos>

  init {
    //Cobalt.setPathExec(Pathfinder()) <- Spams StackOverflowError: null TODO: fix ig idk

    openQueue = PriorityQueue(
      compareBy<Node> { it.fCost }
        .thenBy { it.hCost }
    )

    openSet = HashMap()
    closestSet = HashMap()
    customSet = HashMap()
    cachedCosts = HashMap()
    cachedPenalties = HashMap()
    stateCache = HashMap()
    directions = ArrayList()

    directions.apply {
      add(BlockPos(1, 0, 0))
      add(BlockPos(-1, 0, 0))
      add(BlockPos(0, 0, 1))
      add(BlockPos(0, 0, -1))
    }
  }

  fun calculatePath(beginData: NodeData, end: NodeData): List<Node>? {
    start = Node(beginData, 0.0, Double.MAX_VALUE, null)
    goal = Node(end, Double.MAX_VALUE, 0.0, null)

    start.hCost = getHeuristic(beginData)
    start.fCost = start.gCost + start.hCost

    openQueue.add(start)
    openSet[start.data.pos] = start

    while (openQueue.isNotEmpty()) {
      val current = openQueue.poll()
      val currentPos = current.data.pos

      openSet.remove(currentPos)

      if (currentPos == goal.data.pos) {
        goal.parent = current
        return smoothPath(reconstructPath(goal))
      }

      closestSet[currentPos] = current

      for (neighbourData in getNeighbours(current.data)) {
        if (closestSet.containsKey(neighbourData.pos)) {
          continue
        }

        val newGCost = current.gCost + neighbourData.movement + getPenalty(neighbourData)

        if (!openSet.containsKey(neighbourData.pos)) {
          val newNode = Node(neighbourData, newGCost, getHeuristic(neighbourData), current)
          newNode.fCost = newNode.gCost + newNode.hCost
          openQueue.add(newNode)
          openSet[neighbourData.pos] = newNode
        } else {
          val neighbourNode = openSet[neighbourData.pos]!!
          if (newGCost < neighbourNode.gCost) {
            openQueue.remove(neighbourNode)

            val updatedNode = Node(neighbourData, newGCost, neighbourNode.hCost, current)
            updatedNode.fCost = updatedNode.gCost + updatedNode.hCost
            openQueue.add(updatedNode)
            openSet[neighbourData.pos] = updatedNode
          }
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
    var dataBlock = getCachedState(pos).block

    if (dataBlock is AbstractRailBlock) {
      dataBlock = getCachedState(pos.down()).block
    }

    if (dataBlock is FenceBlock ||
      dataBlock is FenceGateBlock ||
      dataBlock.translationKey.contains("fence")) {
      return false
    }

    val state = getCachedState(pos)

    if (dataBlock !is FluidBlock &&
      (state.blocksMovement() || Registries.BLOCK.getRawId(dataBlock) == 78)) {
      return hasEnoughHeadroom(pos)
    }

    return false
  }

  private fun hasEnoughHeadroom(pos: BlockPos): Boolean {
    val baseBlock = getCachedState(pos).block
    val baseState = getCachedState(pos)
    val baseShape = baseState.getCollisionShape(mc.world, pos)
    val baseHeight = if (baseShape.isEmpty) 0.0 else baseShape.boundingBox.maxY

    val block1 = getCachedState(pos.up()).block
    val block2 = getCachedState(pos.up(2)).block

    if (block1 is FenceBlock || block1 is FenceGateBlock ||
      block2 is FenceBlock || block2 is FenceGateBlock) {
      return false
    }

    if (block1 is AbstractRailBlock || block2 is AbstractRailBlock) {
      return true
    }

    val requiredClearance = 1.8
    var availableClearance = 0.0

    val state1 = getCachedState(pos.up())
    val shape1 = state1.getCollisionShape(mc.world, pos.up())
    if (block1 == Blocks.AIR) {
      availableClearance += 1.0
    } else if (!shape1.isEmpty) {
      availableClearance += (1.0 - shape1.boundingBox.maxY)
    }

    val state2 = getCachedState(pos.up(2))
    val shape2 = state2.getCollisionShape(mc.world, pos.up(2))
    if (block2 == Blocks.AIR) {
      availableClearance += 1.0
    } else if (!shape2.isEmpty) {
      availableClearance += (1.0 - shape2.boundingBox.maxY)
    }

    return availableClearance >= requiredClearance
  }

  fun getNeighbours(data: NodeData): ArrayList<NodeData> {
    val neighbours = ArrayList<NodeData>()
    val dataBlock = getCachedState(data.pos).block
    val dataState = getCachedState(data.pos)
    val dataShape = dataState.getCollisionShape(mc.world, data.pos)
    val fromTop = if (dataShape.isEmpty) 0.0 else dataShape.boundingBox.maxY

    for (blockPos in directions) {
      val direction = data.pos.add(blockPos)

      if (direction == goal.data.pos) {
        neighbours.add(NodeData(direction, 2.0, false))
        continue
      }

      var airCount = 0
      var totalAir = 0

      for (y in 1 downTo -6) {
        val pos = direction.add(0, y, 0)
        val block = getCachedState(pos).block
        val state = getCachedState(pos)

        val shape = state.getCollisionShape(mc.world, pos)
        val toTop = if (shape.isEmpty) 0.0 + y else shape.boundingBox.maxY + y
        val requiredJump = toTop - fromTop

        if (requiredJump > 1.2) {
          continue
        }

        if (isWalkable(pos)) {
          if (y < -1 && airCount != totalAir) {
            break
          }

          val blockHeight = state.getCollisionShape(mc.world, pos).let {
            if (it.isEmpty) 0.0 else it.boundingBox.maxY
          }
          var cost = if (y == 1 && blockHeight != 0.5) 4.0 else 2.0
          if (y < -1) {
            cost *= abs(y).toDouble()
          }

          var finalPos = pos
          if (block is AbstractRailBlock) {
            finalPos = pos.down()
          }

          neighbours.add(NodeData(finalPos, cost, false))
          break
        }

        totalAir++
        if (block == Blocks.AIR) {
          airCount++
        }
      }
    }

    return neighbours
  }

  private fun getPenalty(data: NodeData): Double {
    if (cachedPenalties.containsKey(data.pos)) {
      return cachedPenalties[data.pos]!!
    }

    var cost = 0.0
    val belowBlock = getCachedState(data.pos).block

    for (x in -1..1) {
      for (z in -1..1) {
        val checkBlockPos = data.pos.add(x, 1, z)
        val checkBlock = getCachedState(checkBlockPos).block

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
    if (!cachedCosts.containsKey(data.pos)) {
      val pos = data.pos
      val dX = (pos.x - goal.data.pos.x).toDouble()
      val dY = (pos.y - goal.data.pos.y).toDouble()
      val dZ = (pos.z - goal.data.pos.z).toDouble()
      cachedCosts[data.pos] = sqrt(dX * dX + dY * dY + dZ * dZ)
    }

    return cachedCosts[data.pos]!!
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
          val fromBlock = getCachedState(currentPos).block
          val toBlock = getCachedState(candidatePos).block

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
      val block = getCachedState(checkPos).block
      val state = getCachedState(checkPos)

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
