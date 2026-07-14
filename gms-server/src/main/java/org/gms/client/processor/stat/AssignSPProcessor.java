/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    Copyleft (L) 2016 - 2019 RonanLana (HeavenMS)

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
package org.gms.client.processor.stat;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.client.Job;
import org.gms.model.pojo.SkillEntry;
import org.gms.client.autoban.AutobanFactory;
import org.gms.constants.game.GameConstants;
import org.gms.constants.skills.Aran;
import org.gms.server.quest.SkillBreakthroughService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gms.util.PacketCreator;

/**
 * @author RonanLana - synchronization of SP transaction modules
 */
public class AssignSPProcessor {
    private static final Logger log = LoggerFactory.getLogger(AssignSPProcessor.class);

    public static boolean canSPAssign(Client c, int skillid) {
        if (skillid == Aran.HIDDEN_FULL_DOUBLE || skillid == Aran.HIDDEN_FULL_TRIPLE || skillid == Aran.HIDDEN_OVER_DOUBLE || skillid == Aran.HIDDEN_OVER_TRIPLE) {
            c.sendPacket(PacketCreator.enableActions());
            return false;
        }

        Character player = c.getPlayer();
        if ((!GameConstants.isPqSkillMap(player.getMapId()) && GameConstants.isPqSkill(skillid)) || (!player.isGM() && GameConstants.isGMSkills(skillid)) || (!GameConstants.isInJobTree(skillid, player.getJob().getId()) && !player.isGM())) {
            AutobanFactory.PACKET_EDIT.alert(player, "tried to packet edit in distributing sp.");
            log.warn("Chr {} tried to use skill {} without it being in their job.", c.getPlayer().getName(), skillid);

            c.disconnect(true, false);
            return false;
        }

        return true;
    }

    public static void SPAssignAction(Client c, int skillid) {
        c.lockClient();
        try {
            if (!canSPAssign(c, skillid)) {
                return;
            }

            Character player = c.getPlayer();
            int remainingSp = player.getRemainingSps()[GameConstants.getSkillBook(skillid / 10000)];
            boolean isBeginnerSkill = false;

            if (skillid % 10000000 > 999 && skillid % 10000000 < 1003) {
                int total = 0;
                for (int i = 0; i < 3; i++) {
                    total += player.getSkillLevel(SkillFactory.getSkill(player.getJobType() * 10000000 + 1000 + i));
                }
                remainingSp = Math.min((player.getLevel() - 1), 6) - total;
                isBeginnerSkill = true;
            }
            Skill skill = SkillFactory.getSkill(skillid);
            int curLevel = player.getSkillLevel(skill);
            int nextLevel = curLevel + 1;
            if (!hasRequiredEarlierJobSp(player, skillid)) {
                player.sendPacket(PacketCreator.enableActions());
                return;
            }
            if (!SkillBreakthroughService.canAssignLevel(player, skill, nextLevel)) {
                player.sendPacket(PacketCreator.enableActions());
                return;
            }
            if ((remainingSp > 0 && nextLevel <= (skill.isFourthJob() ? player.getMasterLevel(skill) : skill.getMaxLevel()))) {
                if (!isBeginnerSkill) {
                    player.gainSp(-1, GameConstants.getSkillBook(skillid / 10000), false);
                } else {
                    player.sendPacket(PacketCreator.enableActions());
                }
                if (skill.getId() == Aran.FULL_SWING) {
                    player.changeSkillLevel(skill, (byte) nextLevel, player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_FULL_DOUBLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_FULL_TRIPLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                } else if (skill.getId() == Aran.OVER_SWING) {
                    player.changeSkillLevel(skill, (byte) nextLevel, player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_OVER_DOUBLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_OVER_TRIPLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                } else {
                    player.changeSkillLevel(skill, (byte) nextLevel, player.getMasterLevel(skill), player.getSkillExpiration(skill));
                }
            }
        } finally {
            c.unlockClient();
        }
    }

    private static boolean hasRequiredEarlierJobSp(Character player, int skillId) {
        int targetStage = GameConstants.getJobBranch(Job.getById(skillId / 10000));
        if (targetStage <= 1 || targetStage > 4) {
            return true;
        }

        int[] invested = new int[5];
        for (var entry : player.getSkills().entrySet()) {
            Skill learnedSkill = entry.getKey();
            SkillEntry learned = entry.getValue();
            if (learned == null || learned.skillLevel <= 0) {
                continue;
            }
            int stage = GameConstants.getJobBranch(Job.getById(learnedSkill.getId() / 10000));
            if (stage >= 1 && stage <= 4) {
                invested[stage] += learned.skillLevel;
            }
        }

        int firstJobRequirement = player.getJob().isA(Job.MAGICIAN) ? 67 : 61;
        int[] required = {0, firstJobRequirement, 121, 151, 0};
        for (int stage = 1; stage < targetStage; stage++) {
            int missing = required[stage] - invested[stage];
            if (missing > 0) {
                player.message(stage + "转技能总投入不足：当前已投入 " + invested[stage]
                        + " 点，还需要投入 " + missing + " 点后才能学习" + targetStage + "转技能。");
                return false;
            }
        }
        return true;
    }
}
