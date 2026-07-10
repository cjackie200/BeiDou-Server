/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gms.server.quest.actions;

import org.gms.client.Character;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.inventory.ItemConstants;
import org.gms.util.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.ItemInformationProvider;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestActionType;
import org.gms.util.PacketCreator;
import org.gms.util.Pair;
import org.gms.util.Randomizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Tyler (Twdtwd)
 * @author Ronan
 */
public class ItemAction extends AbstractQuestAction {
    private static final Logger log = LoggerFactory.getLogger(ItemAction.class);
    List<ItemData> items = new ArrayList<>();

    public ItemAction(Quest quest, Data data) {
        super(QuestActionType.ITEM, quest);
        processData(data);
    }


    @Override
    public void processData(Data data) {
        for (Data iEntry : data.getChildren()) {
            int id = DataTool.getInt(iEntry.getChildByPath("id"));
            int count = DataTool.getInt(iEntry.getChildByPath("count"), 1);
            if (count == 0) {
                log.warn("Ignored zero-quantity item action for item {} in quest {}", id, questID);
                continue;
            }
            int period = DataTool.getInt(iEntry.getChildByPath("period"), 0);

            Integer prop = null;
            Data propData = iEntry.getChildByPath("prop");
            if (propData != null) {
                prop = DataTool.getInt(propData);
            }

            int gender = 2;
            if (iEntry.getChildByPath("gender") != null) {
                gender = DataTool.getInt(iEntry.getChildByPath("gender"));
            }

            int job = -1;
            if (iEntry.getChildByPath("job") != null) {
                job = DataTool.getInt(iEntry.getChildByPath("job"));
            }

            items.add(new ItemData(Integer.parseInt(iEntry.getName()), id, count, prop, job, gender, period));
        }

        items.sort((o1, o2) -> o1.map - o2.map);
    }

    @Override
    public void run(Character chr, Integer extSelection) {
        List<ItemData> takeItem = new LinkedList<>();
        List<ItemData> giveItem = new LinkedList<>();

        int props = 0, rndProps = 0, accProps = 0;
        for (ItemData item : items) {
            if (!canGetItem(item, chr) || item.getProp() == null) {
                continue;
            }
            if (item.getProp() < -1) {
                log.error("Rejected invalid item action prop {} for item {} in quest {}",
                        item.getProp(), item.getId(), questID);
                chr.sendPacket(PacketCreator.enableActions());
                return;
            }
            if (item.getProp() > 0) {
                props += item.getProp();
            }
        }

        int extNum = 0;
        if (props > 0) {
            rndProps = Randomizer.nextInt(props);
        }
        for (ItemData iEntry : items) {
            if (!canGetItem(iEntry, chr)) {
                continue;
            }

            if (iEntry.getProp() != null) {
                if (iEntry.getProp() == -1) {
                    if (extSelection == null || extSelection != extNum++) {
                        continue;
                    }
                } else if (iEntry.getProp() > 0) {
                    accProps += iEntry.getProp();

                    if (accProps <= rndProps) {
                        continue;
                    } else {
                        accProps = Integer.MIN_VALUE;
                    }
                } else {
                    continue;
                }
            }

            if (iEntry.getCount() < 0) { // Remove Item
                takeItem.add(iEntry);
            } else {                    // Give Item
                giveItem.add(iEntry);
            }
        }

        // must take all needed items before giving others

        for (ItemData iEntry : takeItem) {
            int itemid = iEntry.getId(), count = iEntry.getCount();
            int quantity = count * -1; // Invert

            removeItem(chr, itemid, quantity);
            chr.sendPacket(PacketCreator.getShowItemGain(itemid, (short) count, true));
        }

        for (ItemData iEntry : giveItem) {
            int itemid = iEntry.getId(), count = iEntry.getCount(), period = iEntry.getPeriod();    // thanks Vcoc for noticing quest milestone item not getting removed from inventory after a while

            InventoryManipulator.addById(chr.getClient(), itemid, (short) count, "", -1, period > 0 ? (System.currentTimeMillis() + MINUTES.toMillis(period)) : -1);
            chr.sendPacket(PacketCreator.getShowItemGain(itemid, (short) count, true));
        }
    }

    @Override
    public boolean check(Character chr, Integer extSelection) {
        List<Pair<Item, InventoryType>> gainList = new LinkedList<>();
        List<Pair<Item, InventoryType>> selectList = new LinkedList<>();
        List<Pair<Item, InventoryType>> randomList = new LinkedList<>();

        for (ItemData item : items) {
            if (!canGetItem(item, chr)) {
                continue;
            }

            InventoryType type = ItemConstants.getInventoryType(item.getId());
            if (item.getProp() != null) {
                Item toItem = new Item(item.getId(), (short) 0, (short) item.getCount());

                if (item.getProp() < -1) {
                    log.error("Rejected invalid item action prop {} for item {} in quest {}",
                            item.getProp(), item.getId(), questID);
                    chr.sendPacket(PacketCreator.enableActions());
                    return false;
                }
                if (item.getProp() == -1) {
                    selectList.add(new Pair<>(toItem, type));
                } else if (item.getProp() > 0) {
                    randomList.add(new Pair<>(toItem, type));
                }

            } else {
                // Make sure they can hold the item.
                Item toItem = new Item(item.getId(), (short) 0, (short) item.getCount());
                gainList.add(new Pair<>(toItem, type));
            }
        }

        if (!selectList.isEmpty()) {
            if (extSelection == null || extSelection < 0 || extSelection >= selectList.size()) {
                log.warn("Rejected invalid item reward selection {} for quest {} and character {}; option count is {}",
                        extSelection, questID, chr.getId(), selectList.size());
                chr.sendPacket(PacketCreator.enableActions());
                return false;
            }
            Pair<Item, InventoryType> selected = selectList.get(extSelection);
            gainList.add(selected);
        }

        if (randomList.isEmpty()) {
            return canExecuteBranch(chr, gainList);
        }

        for (Pair<Item, InventoryType> randomItem : randomList) {
            List<Pair<Item, InventoryType>> possibleReward = new ArrayList<>(gainList);
            possibleReward.add(randomItem);
            if (!canExecuteBranch(chr, possibleReward)) {
                return false;
            }
        }
        return true;
    }

    private boolean canExecuteBranch(Character chr, List<Pair<Item, InventoryType>> actions) {
        if (!hasRequiredItems(chr, actions)) {
            return false;
        }

        if (!canHold(chr, actions)) {
            announceInventoryLimit(itemIds(actions), chr);
            return false;
        }

        return true;
    }

    private boolean hasRequiredItems(Character chr, List<Pair<Item, InventoryType>> actions) {
        Map<Integer, Integer> requiredByItemId = new LinkedHashMap<>();
        for (Pair<Item, InventoryType> action : actions) {
            Item item = action.getLeft();
            if (item.getQuantity() < 0) {
                requiredByItemId.merge(item.getItemId(), -((int) item.getQuantity()), Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> required : requiredByItemId.entrySet()) {
            int itemId = required.getKey();
            int quantity = required.getValue();
            InventoryType type = ItemConstants.getInventoryType(itemId);
            boolean hasEnough;
            if (type == InventoryType.EQUIP) {
                int carried = chr.getInventory(InventoryType.EQUIP).countById(itemId);
                int equipped = chr.getInventory(InventoryType.EQUIPPED).countById(itemId);
                hasEnough = carried + equipped >= quantity;
            } else {
                hasEnough = chr.getInventory(type).freeSlotCountById(itemId, quantity) != -1;
            }

            if (!hasEnough) {
                announceInventoryLimit(Collections.singletonList(itemId), chr);
                return false;
            }
        }

        return true;
    }

    private List<Integer> itemIds(List<Pair<Item, InventoryType>> actions) {
        List<Integer> itemIds = new LinkedList<>();
        for (Pair<Item, InventoryType> action : actions) {
            if (action.getLeft().getQuantity() > 0) {
                itemIds.add(action.getLeft().getItemId());
            }
        }
        return itemIds;
    }

    private void removeItem(Character chr, int itemId, int quantity) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        if (type != InventoryType.EQUIP) {
            InventoryManipulator.removeById(chr.getClient(), type, itemId, quantity, true, false);
            return;
        }

        int carried = chr.getInventory(InventoryType.EQUIP).countById(itemId);
        int removeCarried = Math.min(carried, quantity);
        if (removeCarried > 0) {
            InventoryManipulator.removeById(chr.getClient(), InventoryType.EQUIP, itemId, removeCarried, true, false);
        }

        int removeEquipped = quantity - removeCarried;
        if (removeEquipped > 0) {
            InventoryManipulator.removeById(chr.getClient(), InventoryType.EQUIPPED, itemId, removeEquipped, true, false);
        }
    }

    private void announceInventoryLimit(List<Integer> itemids, Character chr) {
        for (Integer id : itemids) {
            if (ItemInformationProvider.getInstance().isPickupRestricted(id) && chr.haveItemWithId(id, true)) {
                chr.dropMessage(1, "Please check if you already have a similar one-of-a-kind item in your inventory.");
                return;
            }
        }

        chr.dropMessage(1, I18nUtil.getMessage("ItemAction.Message1"));
    }

    private boolean canHold(Character chr, List<Pair<Item, InventoryType>> gainList) {
        List<Integer> toAddItemids = new LinkedList<>();
        List<Integer> toAddQuantity = new LinkedList<>();
        List<Integer> toRemoveItemids = new LinkedList<>();
        List<Integer> toRemoveQuantity = new LinkedList<>();

        for (Pair<Item, InventoryType> item : gainList) {
            Item it = item.getLeft();

            if (it.getQuantity() > 0) {
                toAddItemids.add(it.getItemId());
                toAddQuantity.add((int) it.getQuantity());
            } else if (it.getQuantity() < 0) {
                toRemoveItemids.add(it.getItemId());
                toRemoveQuantity.add(-1 * ((int) it.getQuantity()));
            }
        }

        // thanks onechord for noticing quests unnecessarily giving out "full inventory" from quests that also takes items from players
        return chr.getAbstractPlayerInteraction().canHoldAllAfterRemoving(toAddItemids, toAddQuantity, toRemoveItemids, toRemoveQuantity);
    }

    private boolean canGetItem(ItemData item, Character chr) {
        if (item.getGender() != 2 && item.getGender() != chr.getGender()) {
            return false;
        }

        if (item.job > 0) {
            final List<Integer> code = getJobBy5ByteEncoding(item.getJob());
            boolean jobFound = false;
            for (int codec : code) {
                if (codec / 100 == chr.getJob().getId() / 100) {
                    jobFound = true;
                    break;
                }
            }
            return jobFound;
        }

        return true;
    }

    public boolean restoreLostItem(Character chr, int itemid) {
        if (!ItemInformationProvider.getInstance().isQuestItem(itemid)) {
            return false;
        }

        // thanks danielktran (MapleHeroesD)
        for (ItemData item : items) {
            if (item.getId() == itemid) {
                int missingQty = item.getCount() - chr.countItem(itemid);
                if (missingQty > 0) {
                    if (!chr.canHold(itemid, missingQty)) {
                        chr.dropMessage(1, I18nUtil.getMessage("ItemAction.Message1"));
                        return false;
                    }

                    InventoryManipulator.addById(chr.getClient(), item.getId(), (short) missingQty);
                    log.debug("Chr {} obtained {}x {} from questId {}", chr, itemid, missingQty, questID);
                }
                return true;
            }
        }

        return false;
    }

    private class ItemData {
        private final int map, id, count, job, gender, period;
        private final Integer prop;

        public ItemData(int map, int id, int count, Integer prop, int job, int gender, int period) {
            this.map = map;
            this.id = id;
            this.count = count;
            this.prop = prop;
            this.job = job;
            this.gender = gender;
            this.period = period;
        }

        public int getId() {
            return id;
        }

        public int getCount() {
            return count;
        }

        public Integer getProp() {
            return prop;
        }

        public int getJob() {
            return job;
        }

        public int getGender() {
            return gender;
        }

        public int getPeriod() {
            return period;
        }
    }
} 
