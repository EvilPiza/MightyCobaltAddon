package com.mighty

import com.mighty.command.CommandHandler
import com.mighty.module.MightyRotations
import com.mighty.pathfinder.PathWalker
import com.mighty.pathfinder.helper.PathRenderer
import com.mighty.pathfinder.helper.Rotation
import org.cobalt.addon.Addon
import org.cobalt.command.CommandManager
import org.cobalt.event.EventBus
import org.cobalt.module.ModuleManager

object MightyAddon : Addon {

  // shoutout to Rohan's FishingAddon

  override fun onLoad() {
    CommandManager.registerCommand(CommandHandler)

    listOf(
      PathRenderer,
      PathWalker,
      Rotation,
      MightyRotations
    ).forEach(EventBus::register)

    listOf(
      MightyRotations
    ).forEach(ModuleManager::addModule)

    println("Mighty Loaded!")
  }

  override fun onUnload() {
    println("Mighty Unloaded! (Why would you do that?)")
  }
}
