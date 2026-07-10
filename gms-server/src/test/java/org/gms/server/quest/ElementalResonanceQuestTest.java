package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.net.packet.Packet;
import org.gms.property.ServiceProperty;
import org.gms.server.quest.hook.InteractionHookAction;
import org.gms.server.quest.hook.InteractionHookProgressEntry;
import org.gms.server.maps.MapItem;
import org.gms.service.ConfigService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Point;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void onlyRewardCompletionRequiresPreviousStaffInBagAcrossUpgradeStages() {
        int[] firstBossQuestIds = {29954, 29958, 29962, 29966};
        int[] rewardQuestIds = {29992, 29993, 29994, 29995};
        int[] previousStaffIds = {1372035, 1382045, 1372039, 1382049};

        for (int stageIndex = 2; stageIndex <= 5; stageIndex++) {
            ElementalResonanceQuest.Stage stage = ElementalResonanceQuest.getStage(stageIndex);
            int firstBossQuestId = firstBossQuestIds[stageIndex - 2];
            int rewardQuestId = rewardQuestIds[stageIndex - 2];
            int previousStaffId = previousStaffIds[stageIndex - 2];

            Character equipped = newMage(stage.getRequiredLevel());
            addItem(equipped, InventoryType.EQUIPPED, previousStaffId, 1);
            putStageReadyForReward(equipped, stage, firstBossQuestId);
            putQuest(equipped, rewardQuestId, QuestStatus.Status.STARTED, "000");

            var equippedValidation = ElementalResonanceQuest.validateCompletion(equipped, rewardQuestId, 0);
            assertFalse(equippedValidation.isOk(),
                    "equipped staff must block reward completion " + rewardQuestId);
            assertTrue(equippedValidation.getMessage().contains("卸下"), equippedValidation.getMessage());

            Character inBag = newMage(stage.getRequiredLevel());
            addItem(inBag, previousStaffId, 1);
            putStageReadyForReward(inBag, stage, firstBossQuestId);
            putQuest(inBag, rewardQuestId, QuestStatus.Status.STARTED, "001");

            assertTrue(ElementalResonanceQuest.validateCompletion(inBag, rewardQuestId, 0).isOk(),
                    "staff in equip inventory must allow reward completion " + rewardQuestId);
        }
    }

    @Test
    void equippedPreviousStaffAllowsEveryBossAndMaterialTurnInAcrossUpgradeStages() {
        int[] firstBossQuestIds = {29954, 29958, 29962, 29966};
        int[] previousStaffIds = {1372035, 1382045, 1372039, 1382049};

        for (int stageIndex = 2; stageIndex <= 5; stageIndex++) {
            ElementalResonanceQuest.Stage stage = ElementalResonanceQuest.getStage(stageIndex);
            int firstBossQuestId = firstBossQuestIds[stageIndex - 2];
            int previousStaffId = previousStaffIds[stageIndex - 2];
            List<Integer> bossTokenIds = stage.getBossTokenIds();

            for (int bossIndex = 0; bossIndex < bossTokenIds.size(); bossIndex++) {
                Character chr = newMage(stage.getRequiredLevel());
                addItem(chr, InventoryType.EQUIPPED, previousStaffId, 1);
                for (int completedIndex = 0; completedIndex < bossIndex; completedIndex++) {
                    putQuest(chr, firstBossQuestId + completedIndex, QuestStatus.Status.COMPLETED, null);
                }

                int questId = firstBossQuestId + bossIndex;
                assertTrue(ElementalResonanceQuest.startStage(chr, questId).success(),
                        "equipped staff must allow starting boss step " + questId);
                addItem(chr, bossTokenIds.get(bossIndex), 1);
                ElementalResonanceQuest.syncQuestStateSilently(chr);

                assertStartedProgress(chr, questId, "001");
                assertTrue(ElementalResonanceQuest.validateStepAdvance(chr, questId).isOk(),
                        "equipped staff must allow turning in boss step " + questId);
                assertEquals(InteractionHookAction.QUERY_COMPLETE,
                        ElementalResonanceQuest.resolveCurrentAction(chr, questId));
                assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, questId).success(),
                        "equipped staff must allow completing boss step " + questId);
            }

            Character chr = newMage(stage.getRequiredLevel());
            addItem(chr, InventoryType.EQUIPPED, previousStaffId, 1);
            for (int bossIndex = 0; bossIndex < bossTokenIds.size(); bossIndex++) {
                putQuest(chr, firstBossQuestId + bossIndex, QuestStatus.Status.COMPLETED, null);
            }
            int materialQuestId = firstBossQuestId + bossTokenIds.size();
            assertTrue(ElementalResonanceQuest.startStage(chr, materialQuestId).success(),
                    "equipped staff must allow starting material step " + materialQuestId);
            for (ElementalResonanceQuest.Requirement requirement : stage.getBaseRequirements()) {
                addItem(chr, requirement.itemId(), requirement.count());
            }
            chr.setMeso(stage.getBaseMeso());
            ElementalResonanceQuest.syncQuestStateSilently(chr);

            assertStartedProgress(chr, materialQuestId, "001");
            assertTrue(ElementalResonanceQuest.validateStepAdvance(chr, materialQuestId).isOk(),
                    "equipped staff must allow turning in material step " + materialQuestId);
            assertEquals(InteractionHookAction.QUERY_COMPLETE,
                    ElementalResonanceQuest.resolveCurrentAction(chr, materialQuestId));
            assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, materialQuestId).success(),
                    "equipped staff must allow completing material step " + materialQuestId);
        }
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
    void retargetedBossCurveUsesStableBossesAndSharedPianusStep() {
        Character chr = newMage(190);
        addItem(chr, 1382049, 1);

        String prompt = ElementalResonanceQuest.startPrompt(chr, 29966);
        assertTrue(prompt.contains("去击败#o8510000#或#o8520000#"), prompt);

        assertTrue(ElementalResonanceQuest.startStage(chr, 29966).success());
        InteractionHookProgressEntry progress = elementalProgressEntry(chr, 29966);
        assertTrue(progress.conditions().stream().anyMatch(condition ->
                condition.text().contains("#o8510000#或#o8520000#")
                        && condition.text().contains("#t4033024#")), progress.toString());
        assertTrue(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 8510000, 4033024, 29966));
        assertTrue(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 8520000, 4033024, 29966));
        assertTrue(ElementalResonanceQuest.isBossTokenForMonster(8510000, 4033024, 29966));
        assertTrue(ElementalResonanceQuest.isBossTokenForMonster(8520000, 4033024, 29966));
        assertFalse(ElementalResonanceQuest.isAllowedBossTokenDrop(chr, 8500002, 4033024, 29966));
        assertFalse(ElementalResonanceQuest.isBossTokenForMonster(8150000, 4033021, 29962));
        assertFalse(ElementalResonanceQuest.isBossTokenForMonster(8130100, 4033020, 29960));
        assertFalse(ElementalResonanceQuest.isBossTokenForMonster(5220000, 4033015, 29954));
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
    void elementalResonanceWzMatchesLinearQuestChain() throws Exception {
        Path questDir = resolveQuestXml("wz-zh-CN/Quest.wz");
        Document info = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(questDir.resolve("QuestInfo.img.xml").toFile());
        Document check = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(questDir.resolve("Check.img.xml").toFile());
        Document act = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(questDir.resolve("Act.img.xml").toFile());

        int previousRewardQuestId = 0;
        for (ElementalStageSpec stage : elementalStages()) {
            int[] questIds = stage.questIds();
            for (int index = 0; index < questIds.length; index++) {
                int questId = questIds[index];
                boolean finalStep = index == questIds.length - 1;

                Element questInfo = topLevelImgDir(info, questId);
                assertEquals(stage.parent(), childValue(questInfo, "string", "parent"),
                        "elemental QuestInfo.parent must group stage " + questId + " in " + questDir);
                assertEquals(Integer.toString(index + 1), childValue(questInfo, "int", "order"),
                        "elemental QuestInfo.order must match stage order " + questId + " in " + questDir);
                assertEquals("31", childValue(questInfo, "int", "area"),
                        "elemental QuestInfo.area must stay in Legend Road " + questId + " in " + questDir);
                assertFalse(childValue(questInfo, "string", "summary").isBlank(),
                        "elemental summary must be visible for " + questId + " in " + questDir);
                assertFalse(childValue(questInfo, "string", "demandSummary").isBlank(),
                        "elemental demandSummary must be visible for " + questId + " in " + questDir);
                assertEquals(1, countOccurrences(childValue(questInfo, "string", "1"),
                                "@@BD_IH_PROGRESS:" + questId + "@@"),
                        "elemental detail must contain exactly one progress marker for " + questId);
                assertNoTechnicalQuestLabels(questInfo, questId);

                Element questCheck = topLevelImgDir(check, questId);
                Element start = childImgDir(questCheck, "0");
                assertEquals(Integer.toString(ElementalResonanceQuest.NPC_ID), childValue(start, "int", "npc"),
                        "elemental start npc must be Hans for " + questId + " in " + questDir);
                assertEquals(Integer.toString(stage.requiredLevel()), childValue(start, "int", "lvmin"),
                        "elemental lvmin must match stage level for " + questId + " in " + questDir);
                assertEquals("elementalResonance", childValue(start, "string", "startscript"),
                        "elemental startscript must use elementalResonance for " + questId);
                assertElementalMageJobGate(start, questId);
                if (index == 0) {
                    if (previousRewardQuestId == 0) {
                        assertNull(childImgDirOrNull(start, "quest"),
                                "first elemental step must not require a previous quest in " + questDir);
                    } else {
                        assertSingleCompletedPrerequisite(start, questId, previousRewardQuestId);
                    }
                } else {
                    assertSingleCompletedPrerequisite(start, questId, questIds[index - 1]);
                }

                Element complete = childImgDir(questCheck, "1");
                assertEquals(Integer.toString(ElementalResonanceQuest.NPC_ID), childValue(complete, "int", "npc"),
                        "elemental complete npc must be Hans for " + questId + " in " + questDir);
                assertEquals("elementalResonance", childValue(complete, "string", "endscript"),
                        "elemental endscript must use elementalResonance for " + questId);
                assertEquals("001", childValue(complete, "infoex", "0", "string", "value"),
                        "elemental completion must use ready progress gate for " + questId);

                Element completeAct = childImgDir(topLevelImgDir(act, questId), "1");
                if (finalStep) {
                    assertEquals("", childValue(completeAct, "int", "nextQuest"),
                            "elemental reward step must not define Act.nextQuest for " + questId);
                } else {
                    assertEquals(Integer.toString(questIds[index + 1]), childValue(completeAct, "int", "nextQuest"),
                            "elemental Act.nextQuest must chain to the next step for " + questId);
                }
            }
            previousRewardQuestId = questIds[questIds.length - 1];
        }
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
        putStageReadyForReward(chr, ElementalResonanceQuest.getStage(2), TIER_TWO_FIRST_BOSS_STEP);
    }

    private static void putStageReadyForReward(Character chr, ElementalResonanceQuest.Stage stage,
                                               int firstBossQuestId) {
        for (int stepIndex = 0; stepIndex <= stage.getBossTokenIds().size(); stepIndex++) {
            putQuest(chr, firstBossQuestId + stepIndex, QuestStatus.Status.COMPLETED, null);
        }
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

    private static List<ElementalStageSpec> elementalStages() {
        return List.of(
                new ElementalStageSpec("元素共鸣:初声", 70,
                        new int[]{29950, 29951, 29952, 29953, 29991}),
                new ElementalStageSpec("元素共鸣:回响", 100,
                        new int[]{29954, 29955, 29956, 29957, 29992}),
                new ElementalStageSpec("元素共鸣:裂隙", 130,
                        new int[]{29958, 29959, 29960, 29961, 29993}),
                new ElementalStageSpec("元素共鸣:风暴", 160,
                        new int[]{29962, 29963, 29964, 29965, 29994}),
                new ElementalStageSpec("元素共鸣:终章", 190,
                        new int[]{29966, 29967, 29968, 29969, 29970, 29995})
        );
    }

    private static void assertNoTechnicalQuestLabels(Element questInfo, int questId) {
        assertQuestDialogStyle(childValue(questInfo, "string", "0"));
        assertQuestDialogStyle(childValue(questInfo, "string", "1"));
        assertQuestDialogStyle(childValue(questInfo, "string", "summary"));
        assertQuestDialogStyle(childValue(questInfo, "string", "demandSummary"));
        assertFalse(childValue(questInfo, "string", "2").startsWith("已完成："),
                "elemental completion text must not use old completed prefix: " + questId);
        assertFalse(childValue(questInfo, "string", "1").contains("@@DB_IH_PROGRESS:"),
                "elemental detail must not use typo interaction hook marker: " + questId);
        assertFalse(childValue(questInfo, "string", "1").contains("@@BD_LP_PROGRESS:"),
                "elemental detail must not use life proof progress marker: " + questId);
    }

    private static void assertElementalMageJobGate(Element start, int questId) {
        Element job = childImgDir(start, "job");
        List<String> values = new ArrayList<>();
        NodeList children = job.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && "int".equals(child.getTagName())) {
                values.add(child.getAttribute("value"));
            }
        }
        assertEquals(List.of("210", "211", "212", "220", "221", "222", "230", "231", "232", "200"),
                values, "elemental job gate must include every mage job for " + questId);
    }

    private static void assertSingleCompletedPrerequisite(Element start, int questId, int requiredQuestId) {
        Element quest = childImgDir(start, "quest");
        List<Element> prerequisites = childImgDirs(quest);
        assertEquals(1, prerequisites.size(),
                "elemental start must have exactly one completed prerequisite for " + questId);
        Element prerequisite = prerequisites.get(0);
        assertEquals(Integer.toString(requiredQuestId), childValue(prerequisite, "int", "id"),
                "elemental start prerequisite id must chain linearly for " + questId);
        assertEquals("2", childValue(prerequisite, "int", "state"),
                "elemental start prerequisite must require completed state for " + questId);
    }

    private static Element topLevelImgDir(Document document, int questId) {
        Element found = childImgDirOrNull(document.getDocumentElement(), Integer.toString(questId));
        if (found == null) {
            throw new AssertionError("missing elemental quest node: " + questId);
        }
        return found;
    }

    private static Element childImgDir(Element parent, String childName) {
        Element found = childImgDirOrNull(parent, childName);
        if (found == null) {
            throw new AssertionError("missing imgdir " + childName + " under " + parent.getAttribute("name"));
        }
        return found;
    }

    private static Element childImgDirOrNull(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && "imgdir".equals(child.getTagName())
                    && childName.equals(child.getAttribute("name"))) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> childImgDirs(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && "imgdir".equals(child.getTagName())) {
                result.add(child);
            }
        }
        return result;
    }

    private static String childValue(Element parent, String tagName, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && tagName.equals(child.getTagName())
                    && childName.equals(child.getAttribute("name"))) {
                return child.getAttribute("value");
            }
        }
        return "";
    }

    private static String childValue(Element parent, String firstDirName, String secondDirName, String tagName,
                                     String childName) {
        return childValue(childImgDir(childImgDir(parent, firstDirName), secondDirName), tagName, childName);
    }

    private static int countOccurrences(String text, String needle) {
        if (text == null || text.isEmpty() || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Path resolveQuestXml(String relativePath) {
        Path modulePath = Path.of(relativePath);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server").resolve(relativePath);
    }

    private record ElementalStageSpec(String parent, int requiredLevel, int[] questIds) {
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
