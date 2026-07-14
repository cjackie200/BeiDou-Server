package org.gms.server;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.id.ItemId;
import org.gms.constants.inventory.ItemConstants;
import org.gms.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class GlobalStorageService {
    public static final int PAGE_COUNT = 20;
    public static final int PAGE_SIZE = 200;

    private static final Object LOCK = new Object();

    private GlobalStorageService() {
    }

    public static int getPageCount() {
        return PAGE_COUNT;
    }

    public static int getPageSize() {
        return PAGE_SIZE;
    }

    public static int getTotalCapacity() {
        return PAGE_COUNT * PAGE_SIZE;
    }

    public static int getRemainingCapacity() {
        return Math.max(0, getTotalCapacity() - countAllItems());
    }

    public static int getRemainingCapacity(int pageNo) {
        if (!isValidPage(pageNo)) {
            return 0;
        }
        return Math.max(0, PAGE_SIZE - countPageItems(pageNo));
    }

    public static List<Entry> list(int pageNo) {
        if (!isValidPage(pageNo)) {
            return Collections.emptyList();
        }

        String sql = """
                SELECT id, page_no, itemid, inventorytype, quantity, owner, petid, flag, expiration, gift_from,
                       deposit_account_id, deposit_char_id, deposit_char_name, create_time
                FROM global_storage_items
                WHERE page_no = ?
                ORDER BY id
                LIMIT ?
                """;
        List<Entry> entries = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, pageNo);
            ps.setInt(2, PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list global storage page " + pageNo, e);
        }
        return entries;
    }

    public static List<Entry> listByInventoryGroup(int group, int viewPageNo, int viewPageSize) {
        if (viewPageNo < 1 || viewPageSize < 1) {
            return Collections.emptyList();
        }

        String condition = inventoryGroupCondition(group);
        if (condition == null) {
            return Collections.emptyList();
        }

        String sql = """
                SELECT id, page_no, itemid, inventorytype, quantity, owner, petid, flag, expiration, gift_from,
                       deposit_account_id, deposit_char_id, deposit_char_name, create_time
                FROM global_storage_items
                WHERE %s
                ORDER BY inventorytype, id
                LIMIT ? OFFSET ?
                """.formatted(condition);
        List<Entry> entries = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, viewPageSize);
            ps.setInt(2, (viewPageNo - 1) * viewPageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list global storage group " + group, e);
        }
        return entries;
    }

    public static int countByInventoryGroup(int group) {
        String condition = inventoryGroupCondition(group);
        if (condition == null) {
            return 0;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM global_storage_items WHERE " + condition)) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count global storage group " + group, e);
        }
    }

    public static Result deposit(Character player, int inventoryType, int slot, int quantity, int pageNo) {
        if (!isValidPage(pageNo)) {
            return Result.fail("\u9875\u7b7e\u4e0d\u5b58\u5728\u3002");
        }

        InventoryType type = InventoryType.getByType((byte) inventoryType);
        if (!isNormalInventoryType(type)) {
            return Result.fail("\u53ea\u80fd\u4ece\u666e\u901a\u80cc\u5305\u5b58\u5165\u7269\u54c1\u3002");
        }
        if (slot < 1 || quantity < 1) {
            return Result.fail("\u5b58\u5165\u6570\u91cf\u6216\u683c\u5b50\u4e0d\u6b63\u786e\u3002");
        }

        Client client = player.getClient();
        Inventory inventory = player.getInventory(type);
        Item storedItem;

        synchronized (LOCK) {
            inventory.lockInventory();
            try {
                Item source = inventory.getItem((short) slot);
                if (source == null) {
                    return Result.fail("\u8fd9\u4e2a\u683c\u5b50\u6ca1\u6709\u7269\u54c1\u3002");
                }
                if (source.getInventoryType() != type) {
                    return Result.fail("\u80cc\u5305\u7c7b\u578b\u4e0d\u6b63\u786e\u3002");
                }

                int itemId = source.getItemId();
                if (!isAllowed(source)) {
                    return Result.fail("\u8be5\u7269\u54c1\u4e0d\u80fd\u653e\u5165\u5168\u670d\u4ed3\u5e93\u3002");
                }
                if (ItemConstants.isRechargeable(itemId) || type == InventoryType.EQUIP) {
                    quantity = source.getQuantity();
                }
                if (quantity > source.getQuantity()) {
                    return Result.fail("\u5b58\u5165\u6570\u91cf\u8d85\u8fc7\u6301\u6709\u6570\u91cf\u3002");
                }
                if (countPageItems(pageNo) >= PAGE_SIZE && !hasStack(itemId, type.getType())) {
                    return Result.fail("\u5f53\u524d\u5185\u90e8\u5206\u7247\u5df2\u6ee1\u3002");
                }

                storedItem = source.copy();
                storedItem.setQuantity((short) quantity);
                try {
                    insertItemAndLog(storedItem, pageNo, player);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to store global storage item", e);
                }
                InventoryManipulator.removeFromSlot(client, type, (short) slot, (short) quantity, false);
            } finally {
                inventory.unlockInventory();
            }
        }

        return Result.ok("\u5df2\u5b58\u5165 #v" + storedItem.getItemId() + "# #z" + storedItem.getItemId() + "# x" + storedItem.getQuantity() + "\u3002");
    }

    public static Result depositAuto(Character player, int inventoryType, int slot, int quantity) {
        synchronized (LOCK) {
            InventoryType type = InventoryType.getByType((byte) inventoryType);
            Item source = isNormalInventoryType(type) ? player.getInventory(type).getItem((short) slot) : null;
            int pageNo = source == null ? -1 : findStoragePage(source);
            if (pageNo < 1) {
                return Result.fail("\u5168\u670d\u4ed3\u5e93\u5df2\u6ee1\u3002");
            }
            return deposit(player, inventoryType, slot, quantity, pageNo);
        }
    }

    public static Result withdraw(Character player, long entryId) {
        return withdraw(player, entryId, Integer.MAX_VALUE);
    }

    public static Result withdraw(Character player, long entryId, int requestedQuantity) {
        if (requestedQuantity < 1) {
            return Result.fail("取出数量不正确。");
        }
        synchronized (LOCK) {
            try (Connection con = DatabaseConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    Entry entry = loadEntryForUpdate(con, entryId);
                    if (entry == null) {
                        con.rollback();
                        return Result.fail("\u7269\u54c1\u5df2\u7ecf\u4e0d\u5b58\u5728\uff0c\u53ef\u80fd\u88ab\u5176\u4ed6\u73a9\u5bb6\u53d6\u8d70\u4e86\u3002");
                    }

                    int quantity = entry.inventoryType == InventoryType.EQUIP.getType()
                            ? 1
                            : Math.min(entry.quantity, Math.min(requestedQuantity, Short.MAX_VALUE));
                    Item item = loadItem(con, entryId, entry.itemId, entry.inventoryType, quantity, entry.owner,
                            entry.petId, entry.flag, entry.expiration, entry.giftFrom);
                    if (!InventoryManipulator.checkSpace(player.getClient(), item.getItemId(), item.getQuantity(), item.getOwner())) {
                        con.rollback();
                        return Result.fail("\u80cc\u5305\u7a7a\u95f4\u4e0d\u8db3\u3002");
                    }

                    if (quantity >= entry.quantity) {
                        deleteItem(con, entryId);
                    } else {
                        updateQuantity(con, entryId, entry.quantity - quantity);
                    }
                    logAction(con, "WITHDRAW", entry.pageNo, entryId, item, player);
                    con.commit();

                    if (!InventoryManipulator.addFromDrop(player.getClient(), item, false)) {
                        return Result.fail("\u53d6\u51fa\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u80cc\u5305\u7a7a\u95f4\u3002");
                    }
                    return Result.ok("\u5df2\u53d6\u51fa #v" + item.getItemId() + "# #z" + item.getItemId() + "# x" + item.getQuantity() + "\u3002");
                } catch (Exception e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to withdraw global storage item " + entryId, e);
            }
        }
    }

    public static List<Item> getDepositableItems(Character player, int inventoryType) {
        InventoryType type = InventoryType.getByType((byte) inventoryType);
        if (!isNormalInventoryType(type)) {
            return Collections.emptyList();
        }

        List<Item> items = new ArrayList<>();
        for (Item item : player.getInventory(type).list()) {
            if (isAllowed(item)) {
                items.add(item);
            }
        }
        return items;
    }

    public static List<Item> getDepositableItems(Character player) {
        List<Item> items = new ArrayList<>();
        for (InventoryType type : normalInventoryTypes()) {
            items.addAll(getDepositableItems(player, type.getType()));
        }
        items.sort(Comparator
                .comparingInt((Item item) -> item.getInventoryType().getType())
                .thenComparingInt(Item::getPosition));
        return items;
    }

    public static BatchResult depositAllAuto(Character player, int inventoryType) {
        InventoryType type = InventoryType.getByType((byte) inventoryType);
        if (!isNormalInventoryType(type)) {
            return BatchResult.fail("\u53ea\u80fd\u4ece\u666e\u901a\u80cc\u5305\u5b58\u5165\u7269\u54c1\u3002");
        }

        int stored = 0;
        int skipped = 0;
        boolean storageFull = false;
        Client client = player.getClient();
        Inventory inventory = player.getInventory(type);

        synchronized (LOCK) {
            inventory.lockInventory();
            try {
                List<Item> candidates = new ArrayList<>(inventory.list());
                candidates.sort(Comparator.comparingInt(Item::getPosition));
                for (Item candidate : candidates) {
                    Item source = inventory.getItem(candidate.getPosition());
                    if (source == null || source.getInventoryType() != type || !isAllowed(source)) {
                        skipped++;
                        continue;
                    }
                    int pageNo = findStoragePage(source);
                    if (pageNo < 1) {
                        storageFull = true;
                        break;
                    }

                    Item storedItem = source.copy();
                    storedItem.setQuantity(source.getQuantity());
                    try {
                        insertItemAndLog(storedItem, pageNo, player);
                    } catch (SQLException e) {
                        throw new RuntimeException("Failed to batch store global storage item", e);
                    }
                    InventoryManipulator.removeFromSlot(client, type, source.getPosition(), source.getQuantity(), false);
                    stored++;
                }
            } finally {
                inventory.unlockInventory();
            }
        }

        if (stored == 0 && skipped == 0 && !storageFull) {
            return BatchResult.fail("\u8fd9\u4e2a\u80cc\u5305\u91cc\u6ca1\u6709\u53ef\u4ee5\u653e\u5165\u5168\u670d\u4ed3\u5e93\u7684\u7269\u54c1\u3002");
        }
        String message = "\u5df2\u5b58\u5165 " + stored + " \u683c\u7269\u54c1\u3002";
        if (skipped > 0) {
            message += "\r\n\u8df3\u8fc7 " + skipped + " \u683c\u4e0d\u53ef\u5b58\u5165\u7684\u7269\u54c1\u3002";
        }
        if (storageFull) {
            message += "\r\n\u5168\u670d\u4ed3\u5e93\u5df2\u6ee1\uff0c\u5269\u4f59\u7269\u54c1\u6ca1\u6709\u7ee7\u7eed\u5b58\u5165\u3002";
        }
        return BatchResult.ok(stored, skipped, storageFull, message);
    }

    public static String getItemName(int itemId) {
        String name = ItemInformationProvider.getInstance().getName(itemId);
        return name == null ? String.valueOf(itemId) : name;
    }

    public static int getEquipJobCategory(int itemId) {
        Map<String, Integer> stats = ItemInformationProvider.getInstance().getEquipStats(itemId);
        Integer reqJob = stats == null ? null : stats.get("reqJob");
        if (reqJob == null || reqJob <= 0) {
            return 0;
        }
        if ((reqJob & 1) != 0) {
            return 1;
        }
        if ((reqJob & 2) != 0) {
            return 2;
        }
        if ((reqJob & 4) != 0) {
            return 3;
        }
        if ((reqJob & 8) != 0) {
            return 4;
        }
        if ((reqJob & 16) != 0) {
            return 5;
        }
        return 0;
    }

    public static boolean isAllowed(Item item) {
        int itemId = item.getItemId();
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (item.getInventoryType() == InventoryType.CASH || ii.isCash(itemId) || ItemConstants.isPet(itemId)) {
            return false;
        }
        if (item.getPetId() > -1 || ItemId.isWeddingRing(itemId) || ItemId.isWeddingToken(itemId)) {
            return false;
        }
        if (ii.isDropRestricted(itemId) || ii.isPickupRestricted(itemId) || ii.isStorageRestricted(itemId) || ii.isUnmerchable(itemId)) {
            return false;
        }
        short flag = item.getFlag();
        return (flag & (ItemConstants.LOCK | ItemConstants.UNTRADEABLE | ItemConstants.ACCOUNT_SHARING | ItemConstants.MERGE_UNTRADEABLE)) == 0;
    }

    private static boolean isValidPage(int pageNo) {
        return pageNo >= 1 && pageNo <= PAGE_COUNT;
    }

    private static List<InventoryType> normalInventoryTypes() {
        return List.of(InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP, InventoryType.ETC);
    }

    private static boolean isNormalInventoryType(InventoryType type) {
        return type == InventoryType.EQUIP || type == InventoryType.USE || type == InventoryType.SETUP || type == InventoryType.ETC;
    }

    private static String inventoryGroupCondition(int group) {
        return switch (group) {
            case 1 -> "inventorytype = 1";
            case 2 -> "inventorytype = 2";
            case 3 -> "inventorytype IN (3, 4)";
            default -> null;
        };
    }

    private static int findAvailablePage() {
        for (int pageNo = 1; pageNo <= PAGE_COUNT; pageNo++) {
            if (countPageItems(pageNo) < PAGE_SIZE) {
                return pageNo;
            }
        }
        return -1;
    }

    private static int findStoragePage(Item item) {
        if (item.getInventoryType() != InventoryType.EQUIP) {
            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT page_no FROM global_storage_items WHERE itemid = ? AND inventorytype = ? ORDER BY id LIMIT 1")) {
                ps.setInt(1, item.getItemId());
                ps.setInt(2, item.getInventoryType().getType());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find global storage stack", e);
            }
        }
        return findAvailablePage();
    }

    private static boolean hasStack(int itemId, int inventoryType) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM global_storage_items WHERE itemid = ? AND inventorytype = ? LIMIT 1")) {
            ps.setInt(1, itemId);
            ps.setInt(2, inventoryType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check global storage stack", e);
        }
    }

    private static int countAllItems() {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM global_storage_items")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count global storage items", e);
        }
    }

    private static int countPageItems(int pageNo) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM global_storage_items WHERE page_no = ?")) {
            ps.setInt(1, pageNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count global storage page " + pageNo, e);
        }
    }

    private static long insertItemAndLog(Item item, int pageNo, Character player) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                long id = mergeOrInsertItem(con, item, pageNo, player);
                logAction(con, "DEPOSIT", pageNo, id, item, player);
                con.commit();
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private static long mergeOrInsertItem(Connection con, Item item, int pageNo, Character player) throws SQLException {
        if (item.getInventoryType() != InventoryType.EQUIP) {
            Long existingId = findStackForUpdate(con, item.getItemId(), item.getInventoryType().getType());
            if (existingId != null) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE global_storage_items SET quantity = quantity + ? WHERE id = ?")) {
                    ps.setInt(1, item.getQuantity());
                    ps.setLong(2, existingId);
                    ps.executeUpdate();
                }
                return existingId;
            }
        }
        return insertItem(con, item, pageNo, player);
    }

    private static Long findStackForUpdate(Connection con, int itemId, int inventoryType) throws SQLException {
        String sql = "SELECT id FROM global_storage_items WHERE itemid = ? AND inventorytype = ? ORDER BY id LIMIT 1 FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, inventoryType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static long insertItem(Connection con, Item item, int pageNo, Character player) throws SQLException {
        String sql = """
                INSERT INTO global_storage_items
                (page_no, itemid, inventorytype, quantity, owner, petid, flag, expiration, gift_from,
                 deposit_account_id, deposit_char_id, deposit_char_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pageNo);
            ps.setInt(2, item.getItemId());
            ps.setInt(3, item.getInventoryType().getType());
            ps.setInt(4, item.getQuantity());
            ps.setString(5, item.getOwner());
            ps.setInt(6, item.getPetId());
            ps.setInt(7, item.getFlag());
            ps.setLong(8, item.getExpiration());
            ps.setString(9, item.getGiftFrom());
            ps.setInt(10, player.getAccountId());
            ps.setInt(11, player.getId());
            ps.setString(12, player.getName());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("No generated id for global storage item");
                }
                long id = rs.getLong(1);
                if (item instanceof Equip equip) {
                    insertEquip(con, id, equip);
                }
                return id;
            }
        }
    }

    private static void insertEquip(Connection con, long itemId, Equip equip) throws SQLException {
        String sql = """
                INSERT INTO global_storage_equipment
                (global_storage_item_id, upgradeslots, level, str, dex, `int`, luk, hp, mp, watk, matk, wdef, mdef,
                 acc, avoid, hands, speed, jump, locked, vicious, itemlevel, itemexp, ringid)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            ps.setInt(2, equip.getUpgradeSlots());
            ps.setInt(3, equip.getLevel());
            ps.setInt(4, equip.getStr());
            ps.setInt(5, equip.getDex());
            ps.setInt(6, equip.getInt());
            ps.setInt(7, equip.getLuk());
            ps.setInt(8, equip.getHp());
            ps.setInt(9, equip.getMp());
            ps.setInt(10, equip.getWatk());
            ps.setInt(11, equip.getMatk());
            ps.setInt(12, equip.getWdef());
            ps.setInt(13, equip.getMdef());
            ps.setInt(14, equip.getAcc());
            ps.setInt(15, equip.getAvoid());
            ps.setInt(16, equip.getHands());
            ps.setInt(17, equip.getSpeed());
            ps.setInt(18, equip.getJump());
            ps.setInt(19, 0);
            ps.setInt(20, equip.getVicious());
            ps.setInt(21, equip.getItemLevel());
            ps.setInt(22, (int) equip.getItemExp());
            ps.setInt(23, equip.getRingId());
            ps.executeUpdate();
        }
    }

    private static Entry loadEntryForUpdate(Connection con, long id) throws SQLException {
        String sql = """
                SELECT id, page_no, itemid, inventorytype, quantity, owner, petid, flag, expiration, gift_from,
                       deposit_account_id, deposit_char_id, deposit_char_name, create_time
                FROM global_storage_items
                WHERE id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readEntry(rs) : null;
            }
        }
    }

    private static Item loadItem(Connection con, long storageItemId, int itemId, int inventoryType, int quantity,
                                 String owner, int petId, int flag, long expiration, String giftFrom) throws SQLException {
        InventoryType type = InventoryType.getByType((byte) inventoryType);
        Item item;
        if (type == InventoryType.EQUIP) {
            Equip equip = new Equip(itemId, (short) 0);
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM global_storage_equipment WHERE global_storage_item_id = ?")) {
                ps.setLong(1, storageItemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
                        equip.setLevel(rs.getByte("level"));
                        equip.setStr(rs.getShort("str"));
                        equip.setDex(rs.getShort("dex"));
                        equip.setInt(rs.getShort("int"));
                        equip.setLuk(rs.getShort("luk"));
                        equip.setHp(rs.getShort("hp"));
                        equip.setMp(rs.getShort("mp"));
                        equip.setWatk(rs.getShort("watk"));
                        equip.setMatk(rs.getShort("matk"));
                        equip.setWdef(rs.getShort("wdef"));
                        equip.setMdef(rs.getShort("mdef"));
                        equip.setAcc(rs.getShort("acc"));
                        equip.setAvoid(rs.getShort("avoid"));
                        equip.setHands(rs.getShort("hands"));
                        equip.setSpeed(rs.getShort("speed"));
                        equip.setJump(rs.getShort("jump"));
                        equip.setVicious(rs.getShort("vicious"));
                        equip.setItemLevel(rs.getByte("itemlevel"));
                        equip.setItemExp(rs.getInt("itemexp"));
                        equip.setRingId(rs.getInt("ringid"));
                    }
                }
            }
            item = equip;
        } else {
            item = new Item(itemId, (short) 0, (short) quantity, petId);
        }
        item.setOwner(owner);
        item.setFlag((short) flag);
        item.setExpiration(expiration);
        item.setGiftFrom(giftFrom);
        item.setQuantity((short) quantity);
        return item;
    }

    private static void deleteItem(Connection con, long id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM global_storage_items WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static void updateQuantity(Connection con, long id, int quantity) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE global_storage_items SET quantity = ? WHERE id = ?")) {
            ps.setInt(1, quantity);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static void logAction(Connection con, String action, int pageNo, long storageItemId, Item item, Character player) throws SQLException {
        String sql = """
                INSERT INTO global_storage_logs
                (action, page_no, storage_item_id, itemid, quantity, account_id, char_id, char_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, action);
            ps.setInt(2, pageNo);
            ps.setLong(3, storageItemId);
            ps.setInt(4, item.getItemId());
            ps.setInt(5, item.getQuantity());
            ps.setInt(6, player.getAccountId());
            ps.setInt(7, player.getId());
            ps.setString(8, player.getName());
            ps.executeUpdate();
        }
    }

    private static Entry readEntry(ResultSet rs) throws SQLException {
        return new Entry(
                rs.getLong("id"),
                rs.getInt("page_no"),
                rs.getInt("itemid"),
                rs.getInt("inventorytype"),
                rs.getInt("quantity"),
                rs.getString("owner"),
                rs.getInt("petid"),
                rs.getInt("flag"),
                rs.getLong("expiration"),
                rs.getString("gift_from"),
                rs.getInt("deposit_account_id"),
                rs.getInt("deposit_char_id"),
                rs.getString("deposit_char_name")
        );
    }

    public record Entry(long id, int pageNo, int itemId, int inventoryType, int quantity, String owner,
                        int petId, int flag, long expiration, String giftFrom, int depositAccountId,
                        int depositCharId, String depositCharName) {
        public String getName() {
            return getItemName(itemId);
        }
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public record BatchResult(boolean success, int stored, int skipped, boolean pageFull, String message) {
        public static BatchResult ok(int stored, int skipped, boolean pageFull, String message) {
            return new BatchResult(true, stored, skipped, pageFull, message);
        }

        public static BatchResult fail(String message) {
            return new BatchResult(false, 0, 0, false, message);
        }
    }
}
