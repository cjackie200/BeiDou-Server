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
package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * @author Tyler (Twdtwd)
 */
public class EndDateRequirement extends AbstractQuestRequirement {
    private static final long HISTORICAL_DATA_CUTOFF = 1732027571809L;
    private static final DateTimeFormatter QUEST_TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuuMMddHH")
            .withResolverStyle(ResolverStyle.STRICT);
    private String timeStr;


    public EndDateRequirement(Quest quest, Data data) {
        super(QuestRequirementType.END_DATE);
        processData(data);
    }

    /**
     * @param data
     */
    @Override
    public void processData(Data data) {
        timeStr = DataTool.getString(data);
    }


    @Override
    public boolean check(Character chr, Integer npcid) {
        long endTime = parseTimeMillis(timeStr);
        if (endTime < 0) {
            return false;
        }
        // 如果结束时间小于2024-11-19 22:46:11，则认为是历史数据，结束时间无效
        if (endTime < HISTORICAL_DATA_CUTOFF) {
            return true;
        }
        return endTime >= System.currentTimeMillis();
    }

    static long parseTimeMillis(String value) {
        if (value == null || value.length() != 10) {
            return -1;
        }
        try {
            return LocalDateTime.parse(value, QUEST_TIME_FORMAT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return -1;
        }
    }
}
