package org.gms.server.hpchallenge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeProofQuestTest {

    @Test
    void questIdsFollowStageJobBlockFormula() {
        assertEquals(30100, LifeProofQuest.questId(1, HpChallengeService.JobBranch.WARRIOR, 0));
        assertEquals(30125, LifeProofQuest.questId(1, HpChallengeService.JobBranch.MAGE, 0));
        assertEquals(30225, LifeProofQuest.questId(2, HpChallengeService.JobBranch.WARRIOR, 0));
        assertEquals(30950, LifeProofQuest.questId(7, HpChallengeService.JobBranch.PIRATE, 0));
        assertEquals(30970, LifeProofQuest.questId(7, HpChallengeService.JobBranch.PIRATE, LifeProofQuest.REWARD_SLOT));
    }

    @Test
    void visibleAndHiddenQuestCountsMatchLayout() {
        assertEquals(785, LifeProofQuest.allQuestMetas().size());
        assertEquals(645, LifeProofQuest.allVisibleQuests().size());

        int bridge = LifeProofQuest.questId(1, HpChallengeService.JobBranch.WARRIOR, LifeProofQuest.BRIDGE_SLOT_START);
        assertTrue(LifeProofQuest.isQuestId(bridge));
        assertFalse(LifeProofQuest.isVisibleQuestId(bridge));
    }

    @Test
    void firstStageWarriorVisitsOwnInstructorLast() {
        List<LifeProofQuest.QuestMeta> quests = LifeProofQuest.stageBranchVisibleQuests(1, HpChallengeService.JobBranch.WARRIOR);

        assertEquals(30100, quests.get(0).questId());
        assertEquals(List.of(1012100), quests.get(0).objective().targetIds());
        assertEquals(30104, quests.get(4).questId());
        assertEquals(List.of(1022000), quests.get(4).objective().targetIds());
    }

    @Test
    void mapTasksAreConvertedToCollectionItems() {
        LifeProofQuest.QuestMeta t2Common = LifeProofQuest.stageBranchVisibleQuests(2, HpChallengeService.JobBranch.WARRIOR)
                .stream()
                .filter(meta -> meta.questId() == 30225)
                .findFirst()
                .orElseThrow();
        assertEquals(LifeProofQuest.ObjectiveType.ITEM, t2Common.objective().type());
        assertEquals(4033001, t2Common.objective().itemId());
        assertEquals(20, t2Common.objective().requiredCount());

        LifeProofQuest.QuestMeta t1OptionalMap = LifeProofQuest.stageBranchVisibleQuests(1, HpChallengeService.JobBranch.WARRIOR)
                .stream()
                .filter(meta -> meta.questId() == 30115)
                .findFirst()
                .orElseThrow();
        assertEquals(LifeProofQuest.ObjectiveType.ITEM, t1OptionalMap.objective().type());
        assertEquals(4033000, t1OptionalMap.objective().itemId());
        assertEquals(50, t1OptionalMap.objective().requiredCount());
    }

    @Test
    void itemCollectionDefinitionsMatchDynamicDropPlan() {
        assertEquals(11, LifeProofQuest.itemCollections().size());

        LifeProofQuest.ItemCollection temple = LifeProofQuest.itemCollections().get("visit_temple_maps");
        assertEquals(4033009, temple.itemId());
        assertEquals(75, temple.requiredCount());
        assertEquals(List.of(8200005, 8200006, 8200007, 8200008, 8200009, 8200010, 8200011, 8200012),
                temple.droppers());
    }
}
