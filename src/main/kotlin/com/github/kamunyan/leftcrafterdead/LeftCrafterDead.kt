package com.github.kamunyan.leftcrafterdead

import com.github.kamunyan.leftcrafterdead.configs.LobbySpawnConfig
import com.github.kamunyan.leftcrafterdead.listener.DamageListener
import com.github.kamunyan.leftcrafterdead.listener.JoinQuitListener
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin

class LeftCrafterDead : JavaPlugin() {

    companion object {
        lateinit var instance: LeftCrafterDead
    }

    init {
        instance = this
    }

    override fun onEnable() {

        val log = this.logger
        log.info("${ChatColor.AQUA}-------------------------------------")
        log.info("${ChatColor.AQUA}LeftCrafterDead preparing...")

        //load configs
        LobbySpawnConfig.loadConfig()

        val manager = this.server.pluginManager
        manager.registerEvents(DamageListener(), this)
        manager.registerEvents(JoinQuitListener(), this)

        log.info("${ChatColor.AQUA}LeftCrafterDead Start!")
        log.info("${ChatColor.AQUA}-------------------------------------")

    }

    override fun onDisable() {

    }

    /**
     * 全プレイヤーにメッセージを表示する
     */
    fun sendBroadCastMessage(message: String) {
        Bukkit.broadcastMessage("[L4D]${message}")
    }
}