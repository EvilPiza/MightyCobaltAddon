package com.mighty

import com.mighty.command.CommandHandler
import com.mighty.module.Main
import com.mighty.module.MightyModule
import com.mighty.pathfinder.PathRenderer
import com.mighty.pathfinder.PathWalker
import com.mighty.pathfinder.helper.Rotation
import org.cobalt.api.addon.Addon
import org.cobalt.api.command.CommandManager
import org.cobalt.api.event.EventBus
import org.cobalt.api.module.Module

object MightyAddon : Addon() {

  // shoutout to Rohan's FishingAddon

  override fun onLoad() {
    CommandManager.register(CommandHandler)
    listOf(
      PathRenderer,
      PathWalker,
      Rotation,
      Main
    ).forEach(EventBus::register)

    println("Mighty Loaded!")
  }

  override fun onUnload() {
    println("Mighty Unloaded! (Why would you do that?)")
  }

  override fun getModules(): List<Module> {
    return listOf(MightyModule)
  }
}
