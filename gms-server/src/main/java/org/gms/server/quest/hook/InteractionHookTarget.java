package org.gms.server.quest.hook;

public record InteractionHookTarget(int questId, int npcId, InteractionHookAction action) {
}
