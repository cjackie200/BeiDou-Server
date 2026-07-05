package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.net.packet.Packet;
import org.gms.property.ServiceProperty;
import org.gms.server.quest.hook.InteractionHookProgressEntry;
import org.gms.server.maps.MapItem;
import org.gms.service.ConfigService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElementalResonanceQuestTest {
    private static final int PROGRESS_KEY = 0;
    private static final int TIER_ONE_FIRST_BOSS_STEP = 29950;
    private static final int TIER_ONE_SECOND_BOSS_STEP = 29951;
    private static final int TIER_ONE_THIRD_BOSS_STEP = 29952;
    private static final int TIER_ONE_MATERIAL_STEP = 29953;
    private static final int TIER_TWO_FIRST_BOSS_STEP = 29954;
    private static final int TIER_TWO_SECOND_BOSS_STEP = 29955;
    private static final int TIER_TWO_THIRD_BOSS_STEP = 29956;
    private static final int TIER_TWO_MATERIAL_STEP = 29957;

    @BeforeAll
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void setUpApplicationContext() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        ServiceProperty serviceProperty = new ServiceProperty();
        MessageSource messageSource = mock(MessageSource.class);
        ConfigService configService = mock(ConfigService.class);
        Map<Class<?>, Object> beans = new HashMap<>();

        when(configService.loadGameConfigs()).thenReturn(List.of());
        when(messageSource.getMessage(anyString(), any(Object[].class), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        beans.put(ServiceProperty.class, serviceProperty);
        beans.put(MessageSource.class, messageSource);
        beans.put(ConfigService.class, configService);

        doAnswer(invocation -> bean(beans, invocation.getArgument(0)))
                .when(context).getBean(any(Class.class));
        doAnswer(invocation -> bean(beans, invocation.getArgument(1)))
                .when(context).getBean(anyString(), any(Class.class));

        Field field = org.gms.manager.ServerManager.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }

    @Test
    void heldStaffUnlocksNextStageWithoutAutoStarting() {
        Character chr = newMage(100);
        addItem(chr, 1372035, 1);

        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertQuest(chr, 29991, QuestStatus.Status.COMPLETED);
        assertQuest(chr, TIER_TWO_FIRST_BOSS_STEP, QuestStatus.Status.NOT_STARTED);
        assertEquals(TIER_TWO_FIRST_BOSS_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
    }

    @Test
    void thirdStageOpensAtLevel130() {
        Character chr = newMage(129);
        addItem(chr, 1382045, 1);

        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertTrue(ElementalResonanceQuest.resolveCurrentQuestId(chr).isEmpty());

        chr.setLevel(130);

        assertEquals(29958, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
    }

    @Test
    void tierOneOpensForLevel70Magician() {
        Character chr = newMage(70);
        chr.setJob(Job.MAGICIAN);

        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertEquals(TIER_ONE_FIRST_BOSS_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertTrue(ElementalResonanceQuest.validateStart(chr, TIER_ONE_FIRST_BOSS_STEP).isOk());
        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_ONE_FIRST_BOSS_STEP).success());
        assertStartedProgress(chr, TIER_ONE_FIRST_BOSS_STEP, "000");
    }

    @Test
    void startedTierOneUsesVirtualReadyProgress() {
        Character chr = newMage(70);

        var start = ElementalResonanceQuest.startStage(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertTrue(start.success());
        assertStartedProgress(chr, TIER_ONE_FIRST_BOSS_STEP, "000");

        addItem(chr, 4033012, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertStartedProgress(chr, TIER_ONE_FIRST_BOSS_STEP, "001");

        var firstBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertTrue(firstBossStep.success());
        assertQuest(chr, TIER_ONE_FIRST_BOSS_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(TIER_ONE_SECOND_BOSS_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertEquals(0, chr.getItemQuantity(4033012, false));

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_ONE_SECOND_BOSS_STEP).success());
        addItem(chr, 4033013, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, TIER_ONE_SECOND_BOSS_STEP, "001");

        var secondBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_ONE_SECOND_BOSS_STEP);
        assertTrue(secondBossStep.success());
        assertQuest(chr, TIER_ONE_SECOND_BOSS_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(TIER_ONE_THIRD_BOSS_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertEquals(0, chr.getItemQuantity(4033013, false));

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_ONE_THIRD_BOSS_STEP).success());
        addItem(chr, 4033014, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, TIER_ONE_THIRD_BOSS_STEP, "001");

        var thirdBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_ONE_THIRD_BOSS_STEP);
        assertTrue(thirdBossStep.success());
        assertQuest(chr, TIER_ONE_THIRD_BOSS_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(TIER_ONE_MATERIAL_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertEquals(0, chr.getItemQuantity(4033014, false));

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_ONE_MATERIAL_STEP).success());
        addTierOneBaseRequirements(chr);
        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertStartedProgress(chr, TIER_ONE_MATERIAL_STEP, "001");

        var materialStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_ONE_MATERIAL_STEP);
        assertTrue(materialStep.success());
        assertQuest(chr, TIER_ONE_MATERIAL_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(29991, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertTrue(ElementalResonanceQuest.startStage(chr, 29991).success());
        assertStartedProgress(chr, 29991, "001");
        assertTrue(ElementalResonanceQuest.validateCompletion(chr, 29991, 0).isOk());
        assertEquals(0, chr.getItemQuantity(4000059, false));
    }

    @Test
    void hookProgressConditionsReflectCurrentStepReadiness() {
        Character chr = newMage(70);

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_ONE_FIRST_BOSS_STEP).success());

        InteractionHookProgressEntry missing = elementalProgressEntry(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertFalse(allConditionsMet(missing));

        addItem(chr, 4033012, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);

        InteractionHookProgressEntry ready = elementalProgressEntry(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertTrue(allConditionsMet(ready));
        assertTrue(ready.conditions().stream().anyMatch(condition ->
                condition.text().contains("#o2220000#")
                        && condition.text().contains("#i4033012#")
                        && condition.text().contains("#t4033012#")
                        && !condition.text().contains("目标：")));
    }

    @Test
    void npcDialogsUseQuestNarrativeStyle() {
        Character chr = newMage(70);

        String startPrompt = ElementalResonanceQuest.startPrompt(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertQuestDialogStyle(startPrompt);
        assertTrue(startPrompt.contains("元素矿石里传来了最初的回声。"));
        assertTrue(startPrompt.contains("#t4033012#"));
        assertTrue(startPrompt.contains("接受这一步委托吗？"));

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_ONE_FIRST_BOSS_STEP).success());

        String progressBeforeReady = ElementalResonanceQuest.progressText(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertQuestDialogStyle(progressBeforeReady);
        assertTrue(progressBeforeReady.contains("#t4033012#"));
        assertFalse(progressBeforeReady.contains("材料不足"), progressBeforeReady);
        assertFalse(progressBeforeReady.contains("需要："), progressBeforeReady);

        addItem(chr, 4033012, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);

        String advancePrompt = ElementalResonanceQuest.advancePrompt(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertQuestDialogStyle(advancePrompt);
        assertTrue(advancePrompt.contains("交给我吗？"));

        var firstBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertQuestDialogStyle(firstBossStep.message());
        assertTrue(firstBossStep.message().contains("回声已经记录下来"));
    }

    @Test
    void rewardProgressUsesNarrativeWhenPreviousStaffIsMissing() {
        Character chr = newMage(100);
        putTierTwoReadyForReward(chr);
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "000");

        String progress = ElementalResonanceQuest.progressText(chr, 29992);

        assertQuestDialogStyle(progress);
        assertTrue(progress.contains("没有找到上一阶段元素杖"), progress);
        assertFalse(progress.contains("你没有上一阶段元素杖"), progress);
    }

    @Test
    void completionValidationRejectsEquippedPreviousStaff() {
        Character chr = newMage(100);
        addItem(chr, InventoryType.EQUIPPED, 1372035, 1);
        putTierTwoReadyForReward(chr);
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "001");

        var validation = ElementalResonanceQuest.validateCompletion(chr, 29992, 0);

        assertFalse(validation.isOk());
        assertTrue(validation.getMessage().contains("卸下"));
    }

    @Test
    void completionValidationRejectsMultipleElementalStaffs() {
        Character chr = newMage(100);
        addItem(chr, 1372035, 1);
        addItem(chr, 1372036, 1);
        putTierTwoReadyForReward(chr);
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "001");

        var validation = ElementalResonanceQuest.validateCompletion(chr, 29992, 0);

        assertFalse(validation.isOk());
        assertTrue(validation.getMessage().contains("多个元素杖"));
    }

    @Test
    void crossElementRequiresExtraMaterialsAndMeso() {
        Character chr = newMage(100);
        addItem(chr, 1372035, 1);
        putTierTwoReadyForReward(chr);
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "001");

        var sameElement = ElementalResonanceQuest.validateCompletion(chr, 29992, 0);
        assertTrue(sameElement.isOk());
        assertFalse(sameElement.switchElement());
        assertEquals(0, sameElement.requiredMeso());

        var missingExtra = ElementalResonanceQuest.validateCompletion(chr, 29992, 1);
        assertFalse(missingExtra.isOk());

        addItem(chr, 4021008, 1);
        addItem(chr, 4021009, 1);
        chr.setMeso(2_000_000);

        var switchedElement = ElementalResonanceQuest.validateCompletion(chr, 29992, 1);
        assertTrue(switchedElement.isOk());
        assertTrue(switchedElement.switchElement());
        assertEquals(2_000_000, switchedElement.requiredMeso());
    }

    @Test
    void discardingStaffAllowsTierOneRestart() {
        Character chr = newMage(70);
        putQuest(chr, 29991, QuestStatus.Status.COMPLETED, "001");
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "000");
        putQuest(chr, TIER_ONE_FIRST_BOSS_STEP, QuestStatus.Status.COMPLETED, null);
        putQuest(chr, TIER_ONE_SECOND_BOSS_STEP, QuestStatus.Status.COMPLETED, null);
        putQuest(chr, TIER_ONE_THIRD_BOSS_STEP, QuestStatus.Status.COMPLETED, null);
        putQuest(chr, TIER_ONE_MATERIAL_STEP, QuestStatus.Status.COMPLETED, null);

        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertQuest(chr, 29991, QuestStatus.Status.NOT_STARTED);
        assertQuest(chr, 29992, QuestStatus.Status.NOT_STARTED);
        assertQuest(chr, TIER_ONE_FIRST_BOSS_STEP, QuestStatus.Status.NOT_STARTED);
        assertQuest(chr, TIER_ONE_SECOND_BOSS_STEP, QuestStatus.Status.NOT_STARTED);
        assertQuest(chr, TIER_ONE_THIRD_BOSS_STEP, QuestStatus.Status.NOT_STARTED);
        assertQuest(chr, TIER_ONE_MATERIAL_STEP, QuestStatus.Status.NOT_STARTED);
        assertEquals(TIER_ONE_FIRST_BOSS_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
    }

    @Test
    void tierTwoAdvancesBossAndMaterialStepsBeforeRewardSelection() {
        Character chr = newMage(100);
        addItem(chr, 1372035, 1);

        var start = ElementalResonanceQuest.startStage(chr, TIER_TWO_FIRST_BOSS_STEP);
        assertTrue(start.success());

        addItem(chr, 4033015, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, TIER_TWO_FIRST_BOSS_STEP, "001");

        var firstBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_TWO_FIRST_BOSS_STEP);
        assertTrue(firstBossStep.success());
        assertQuest(chr, TIER_TWO_FIRST_BOSS_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(TIER_TWO_SECOND_BOSS_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertEquals(0, chr.getItemQuantity(4033015, false));
        assertFalse(ElementalResonanceQuest.validateCompletion(chr, 29992, 0).isOk());

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_TWO_SECOND_BOSS_STEP).success());
        addItem(chr, 4033016, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, TIER_TWO_SECOND_BOSS_STEP, "001");

        var secondBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_TWO_SECOND_BOSS_STEP);
        assertTrue(secondBossStep.success());
        assertQuest(chr, TIER_TWO_SECOND_BOSS_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(TIER_TWO_THIRD_BOSS_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertEquals(0, chr.getItemQuantity(4033016, false));

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_TWO_THIRD_BOSS_STEP).success());
        addItem(chr, 4033017, 1);
        ElementalResonanceQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, TIER_TWO_THIRD_BOSS_STEP, "001");

        var thirdBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_TWO_THIRD_BOSS_STEP);
        assertTrue(thirdBossStep.success());
        assertQuest(chr, TIER_TWO_THIRD_BOSS_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(TIER_TWO_MATERIAL_STEP, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertEquals(0, chr.getItemQuantity(4033017, false));

        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_TWO_MATERIAL_STEP).success());
        addTierTwoBaseMaterials(chr);
        chr.setMeso(8_000_000);
        ElementalResonanceQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, TIER_TWO_MATERIAL_STEP, "001");

        var materialStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_TWO_MATERIAL_STEP);
        assertTrue(materialStep.success());
        assertQuest(chr, TIER_TWO_MATERIAL_STEP, QuestStatus.Status.COMPLETED);
        assertEquals(29992, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
        assertTrue(ElementalResonanceQuest.startStage(chr, 29992).success());
        assertStartedProgress(chr, 29992, "001");
        assertEquals(0, chr.getItemQuantity(4000144, false));
        assertEquals(0, chr.getMeso());
        assertTrue(ElementalResonanceQuest.validateCompletion(chr, 29992, 0).isOk());
    }

    @Test
    void onlyCurrentBossTokenCanDropAndBePickedUp() {
        Character chr = newMage(70);

        var start = ElementalResonanceQuest.startStage(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertTrue(start.success());

        assertTrue(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 2220000, 4033012, TIER_ONE_FIRST_BOSS_STEP));
        assertTrue(ElementalResonanceQuest.needBossToken(chr, 4033012, TIER_ONE_FIRST_BOSS_STEP));
        assertFalse(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 3220000, 4033013, TIER_ONE_FIRST_BOSS_STEP));
        assertFalse(ElementalResonanceQuest.needBossToken(chr, 4033013, TIER_ONE_FIRST_BOSS_STEP));

        addItem(chr, 4033012, 1);
        assertFalse(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 2220000, 4033012, TIER_ONE_FIRST_BOSS_STEP));

        var firstBossStep = ElementalResonanceQuest.advanceCurrentStep(chr, TIER_ONE_FIRST_BOSS_STEP);
        assertTrue(firstBossStep.success());

        assertFalse(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 2220000, 4033012, TIER_ONE_FIRST_BOSS_STEP));
        assertTrue(ElementalResonanceQuest.startStage(chr, TIER_ONE_SECOND_BOSS_STEP).success());
        assertTrue(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 3220000, 4033013, TIER_ONE_SECOND_BOSS_STEP));
        assertTrue(ElementalResonanceQuest.needBossToken(chr, 4033013, TIER_ONE_SECOND_BOSS_STEP));
    }

    @Test
    void bossTokenMapDropIsVisibleAndPickableOnlyByOwner() {
        Character owner = newMage(70);
        Character teammate = newMage(70);
        setCharacterId(owner, 10001);
        setCharacterId(teammate, 10002);
        assertTrue(ElementalResonanceQuest.startStage(owner, TIER_ONE_FIRST_BOSS_STEP).success());
        assertTrue(ElementalResonanceQuest.startStage(teammate, TIER_ONE_FIRST_BOSS_STEP).success());

        MapItem drop = new MapItem(new Item(4033012, (short) 0, (short) 1),
                new Point(0, 0), owner, owner, owner.getClient(), (byte) 0, false, TIER_ONE_FIRST_BOSS_STEP);
        drop.setDropTime(0);

        assertTrue(drop.isVisibleTo(owner));
        assertTrue(drop.canBePickedBy(owner));
        assertFalse(drop.isVisibleTo(teammate));
        assertFalse(drop.canBePickedBy(teammate));
    }

    @Test
    void questItemWithQuestIdUsesPersonalPickupOwnership() {
        Character owner = newMage(120);
        Character teammate = newMage(120);
        setCharacterId(owner, 11001);
        setCharacterId(teammate, 11002);

        MapItem drop = new MapItem(new Item(4033000, (short) 0, (short) 1),
                new Point(0, 0), owner, owner, owner.getClient(), (byte) 0, false, 5100);
        drop.setDropTime(0);

        assertTrue(drop.isPersonalQuestDrop());
        assertTrue(drop.canBePickedBy(owner));
        assertFalse(drop.canBePickedBy(teammate));
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static Character newMage(int level) {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setLevel(level);
        chr.setJob(Job.FP_WIZARD);
        return chr;
    }

    private static void setCharacterId(Character chr, int id) {
        try {
            Field field = Character.class.getDeclaredField("id");
            field.setAccessible(true);
            field.setInt(chr, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void addTierOneBaseRequirements(Character chr) {
        addItem(chr, 4000059, 100);
        addItem(chr, 4000060, 100);
        addItem(chr, 4000061, 100);
        addItem(chr, 4021009, 1);
        chr.setMeso(2_000_000);
    }

    private static void putTierTwoReadyForReward(Character chr) {
        putQuest(chr, TIER_TWO_FIRST_BOSS_STEP, QuestStatus.Status.COMPLETED, null);
        putQuest(chr, TIER_TWO_SECOND_BOSS_STEP, QuestStatus.Status.COMPLETED, null);
        putQuest(chr, TIER_TWO_THIRD_BOSS_STEP, QuestStatus.Status.COMPLETED, null);
        putQuest(chr, TIER_TWO_MATERIAL_STEP, QuestStatus.Status.COMPLETED, null);
    }

    private static void addTierTwoBaseMaterials(Character chr) {
        addItem(chr, 4000144, 100);
        addItem(chr, 4000146, 100);
        addItem(chr, 4000176, 20);
        addItem(chr, 4021008, 2);
        addItem(chr, 4021009, 1);
    }

    private static short addItem(Character chr, int itemId, int quantity) {
        InventoryType type = org.gms.constants.inventory.ItemConstants.getInventoryType(itemId);
        return addItem(chr, type, itemId, quantity);
    }

    private static short addItem(Character chr, InventoryType type, int itemId, int quantity) {
        return chr.getInventory(type).addItem(new Item(itemId, (short) 0, (short) quantity));
    }

    private static void putQuest(Character chr, int questId, QuestStatus.Status status, String progress) {
        QuestStatus questStatus = new QuestStatus(Quest.getInstance(questId), status, ElementalResonanceQuest.NPC_ID);
        if (progress != null) {
            questStatus.setProgress(PROGRESS_KEY, progress);
        }
        chr.getQuests().put((short) questId, questStatus);
    }

    private static void assertQuest(Character chr, int questId, QuestStatus.Status status) {
        assertEquals(status.getId(), chr.getQuestStatus(questId), "quest " + questId);
    }

    private static void assertStartedProgress(Character chr, int questId, String progress) {
        assertQuest(chr, questId, QuestStatus.Status.STARTED);
        assertEquals(progress, chr.getQuest(Quest.getInstance(questId)).getProgress(PROGRESS_KEY), "quest " + questId);
    }

    private static InteractionHookProgressEntry elementalProgressEntry(Character chr, int questId) {
        return ElementalResonanceQuest.progressEntries(chr).stream()
                .filter(entry -> entry.questId() == questId)
                .findFirst()
                .orElseThrow();
    }

    private static boolean allConditionsMet(InteractionHookProgressEntry entry) {
        return entry.conditions().stream().allMatch(condition -> condition.current() >= condition.required());
    }

    private static void assertQuestDialogStyle(String text) {
        assertFalse(text.contains("任务列表"), text);
        assertFalse(text.contains("完成书本"), text);
        assertFalse(text.contains("当前目标："), text);
        assertFalse(text.contains("当前进度："), text);
        assertFalse(text.contains("完成方式："), text);
        assertFalse(text.contains("下一步："), text);
        assertFalse(text.contains("已完成步骤："), text);
    }

    private static final class CapturingClient extends Client {
        private CapturingClient() {
            super(null, -1, null, null, -123, -123);
        }

        @Override
        public void sendPacket(Packet packet) {
            // Tests only need quest state side effects.
        }
    }
}
