package org.gms.client.command.commands.gm2;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.inventory.ItemConstants;

public class ElementWeaponsCommand extends Command {
    private static final int[] ELEMENT_WEAPON_IDS = {
            1372035, 1372036, 1372037, 1372038, 1372047,
            1382045, 1382046, 1382047, 1382048, 1382061,
            1372039, 1372040, 1372041, 1372042, 1372048,
            1382049, 1382050, 1382051, 1382052, 1382063,
            1372059, 1372060, 1372061, 1372062, 1372063
    };

    {
        setDescription("获取全部元素杖装备");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (player == null) {
            return;
        }

        short freeSlots = player.getInventory(InventoryType.EQUIP).getNumFreeSlot();
        if (freeSlots < ELEMENT_WEAPON_IDS.length) {
            player.yellowMessage("装备栏至少需要 " + ELEMENT_WEAPON_IDS.length + " 个空位，当前剩余 " + freeSlots + " 个。");
            return;
        }

        short flag = 0;
        if (player.gmLevel() < 3) {
            flag |= ItemConstants.ACCOUNT_SHARING;
            flag |= ItemConstants.UNTRADEABLE;
        }

        int added = 0;
        for (int itemId : ELEMENT_WEAPON_IDS) {
            if (InventoryManipulator.addById(c, itemId, (short) 1, player.getName(), -1, flag, -1)) {
                added++;
            }
        }

        player.yellowMessage("已发放全部元素杖装备：" + added + " / " + ELEMENT_WEAPON_IDS.length + " 把。");
    }
}
