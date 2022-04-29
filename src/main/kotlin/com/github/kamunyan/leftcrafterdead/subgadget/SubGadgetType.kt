package com.github.kamunyan.leftcrafterdead.subgadget

import org.bukkit.ChatColor
import org.bukkit.Material

enum class SubGadgetType(val material: Material, val itemName: String, val lore: List<String>) {
    HEAL_POTION(Material.POTION, "�񕜂̃|�[�V����", listOf("${ChatColor.AQUA}���g�̗̑͂����ʉ񕜂���")),
    TRIP_MINE(
        Material.TNT, "TRIP MINE", listOf(
            "${ChatColor.AQUA}���g�̑����ɒn����ݒu����",
            "${ChatColor.AQUA}�G���n���ɐG��邩�A",
            "${ChatColor.AQUA}�n�����U������ƋN������"
        )
    ),
    SENTRY_GUN(
        Material.PUMPKIN, "�Z���g���[�K��", listOf(
            "${ChatColor.AQUA}���g�̑����ɃZ���g���[�K����ݒu����",
            "${ChatColor.AQUA}�Η͂͐ݒu�����v���C���[��",
            "${ChatColor.AQUA}�U���͂Ɉˑ�����"
        )
    ),
    PAIN_KILLER(Material.PUMPKIN_SEEDS,"���ɍ�", listOf("${ChatColor.AQUA}���g�̗̑͂��ꎞ�I�ɑ��₷"));
}