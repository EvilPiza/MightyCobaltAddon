package com.mightystore

import com.mightystore.command.CommandHandler
import com.mightystore.module.MightyRotations
import com.mightystore.pathfinder.PathWalker
import com.mightystore.pathfinder.helper.PathRenderer
import com.mightystore.pathfinder.helper.Rotation
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
