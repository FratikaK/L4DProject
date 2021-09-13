package com.github.kamunyan.leftcrafterdead.weapons.primary

import com.github.kamunyan.leftcrafterdead.weapons.GunCategory
import org.bukkit.entity.Entity

class AssaultRifle(weaponTitle: String) :PrimaryWeapon(weaponTitle){

    override fun getGunCategory(): GunCategory {
        return GunCategory.ASSAULT_RIFLE
    }

    override fun loadWeaponCapabilities() {
    }

    override fun specialEffects(entity: Entity) {
    }
}