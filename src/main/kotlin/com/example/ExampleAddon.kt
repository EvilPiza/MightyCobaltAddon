package com.example

import com.example.command.ExampleCommand
import com.example.module.ExampleModule
import org.cobalt.api.addon.Addon
import org.cobalt.api.command.CommandManager
import org.cobalt.api.module.Category
import org.cobalt.api.event.EventBus
import org.cobalt.api.module.ModuleManager

object ExampleAddon : Addon() {

  val CATEGORY = Category("Example", "/assets/exampleaddon/icon.svg")

  override fun onInitialize() {
    ModuleManager.addModules(ExampleModule)
    CommandManager.register(ExampleCommand)

    println("ExampleAddon initialized!")
  }

  override fun onUnload() {
    ModuleManager.removeModule(ExampleModule)
    CommandManager.removeCommand(ExampleCommand)
    EventBus.unregister(this)
    println("ExampelAddon unloaded!")
  }


}
