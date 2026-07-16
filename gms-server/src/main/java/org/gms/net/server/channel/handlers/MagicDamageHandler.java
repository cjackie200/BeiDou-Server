package org.gms.net.server.channel.handlers;

import org.gms.client.BuffStat;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.client.status.MonsterStatus;
import org.gms.client.status.MonsterStatusEffect;
import org.gms.config.GameConfig;
import org.gms.constants.id.MapId;
import org.gms.constants.skills.Bishop;
import org.gms.constants.skills.Evan;
import org.gms.constants.skills.FPArchMage;
import org.gms.constants.skills.ILArchMage;
import org.gms.net.packet.InPacket;
import org.gms.net.packet.Packet;
import org.gms.server.StatEffect;
import org.gms.server.maps.MapleMap;
import org.gms.server.life.Monster;
import org.gms.util.PacketCreator;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.concurrent.TimeUnit.SECONDS;

public final class MagicDamageHandler extends AbstractDealDamageHandler {
    @Override
    public final void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();
        AttackInfo attack = parseDamage(p, chr, false, true);

        if (chr.getBuffEffect(BuffStat.MORPH) != null) {
            if (chr.getBuffEffect(BuffStat.MORPH).isMorphWithoutAttack()) {
                chr.getClient().disconnect(false, false);
                return;
            }
        }

        if (MapId.isDojo(chr.getMap().getId()) && attack.numAttacked > 0) {
            chr.setDojoEnergy(chr.getDojoEnergy() + +GameConfig.getServerInt("dojo_energy_atk"));
            c.sendPacket(PacketCreator.getEnergy("energy", chr.getDojoEnergy()));
        }

        int charge = (attack.skill == Evan.FIRE_BREATH || attack.skill == Evan.ICE_BREATH || attack.skill == FPArchMage.BIG_BANG || attack.skill == ILArchMage.BIG_BANG || attack.skill == Bishop.BIG_BANG) ? attack.charge : -1;
        Packet packet = PacketCreator.magicAttack(chr, attack.skill, attack.skilllevel, attack.stance, attack.numAttackedAndDamage, attack.allDamage, charge, attack.speed, attack.direction, attack.display);
        chr.getMap().broadcastMessage(chr, packet, false, true);
        StatEffect effect = attack.getAttackEffect(chr, null);
        Skill skill = SkillFactory.getSkill(attack.skill);
        StatEffect effect_ = skill.getEffect(chr.getSkillLevel(skill));
        if (effect_.getCooldown() > 0) {
            if (chr.skillIsCooling(attack.skill)) {
                return;
            } else {
                c.sendPacket(PacketCreator.skillCooldown(attack.skill, effect_.getCooldown()));
                chr.addCooldown(attack.skill, currentServerTime(), SECONDS.toMillis(effect_.getCooldown()));
            }
        }
        applyAttack(attack, chr, effect.getAttackCount());

        if (attack.skill == 2001004 || attack.skill == 2101005) {
            applyBounce(attack, chr);
        }

        Skill eaterSkill = SkillFactory.getSkill((chr.getJob().getId() - (chr.getJob().getId() % 10)) * 10000);
        int eaterLevel = chr.getSkillLevel(eaterSkill);
        if (eaterLevel > 0) {
            for (Integer singleDamage : attack.allDamage.keySet()) {
                eaterSkill.getEffect(eaterLevel).applyPassive(chr, chr.getMap().getMapObject(singleDamage), 0);
            }
        }
    }

    private void applyBounce(AttackInfo attack, Character chr) {
        MapleMap map = chr.getMap();
        Set<Integer> hitOids = attack.allDamage.keySet();

        Point primaryPos = null;
        int primaryDamage = 0;
        for (Map.Entry<Integer, List<Integer>> entry : attack.allDamage.entrySet()) {
            Monster m = map.getMonsterByOid(entry.getKey());
            if (m != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                primaryPos = m.getPosition();
                primaryDamage = entry.getValue().get(0);
                break;
            }
        }
        if (primaryPos == null || primaryDamage <= 0) return;
        final Point origin = primaryPos;

        List<Monster> candidates = new ArrayList<>();
        for (Monster m : map.getAllMonsters()) {
            if (m.isAlive() && !hitOids.contains(m.getObjectId())) {
                double dx = m.getPosition().x - origin.x;
                double dy = m.getPosition().y - origin.y;
                if (Math.sqrt(dx * dx + dy * dy) <= 200) {
                    candidates.add(m);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(m -> {
            double dx = m.getPosition().x - origin.x;
            double dy = m.getPosition().y - origin.y;
            return Math.sqrt(dx * dx + dy * dy);
        }));

        float[] decayRates = {0.95f, 0.90f, 0.85f, 0.80f, 0.75f};
        int bounceCount = calculateBounceCount(hitOids.size(), candidates.size());
        Skill skill = SkillFactory.getSkill(attack.skill);
        StatEffect bounceEffect = skill.getEffect(attack.skilllevel);
        boolean applyPoison = attack.skill == 2101005 && bounceEffect != null;

        for (int i = 0; i < bounceCount; i++) {
            Monster target = candidates.get(i);
            int bounceDmg = Math.max(1, (int) (primaryDamage * decayRates[i]));
            // Send a fake single-target magicAttack packet so all clients see
            // a projectile hitting this target (visual chain effect)
            byte bounceNum = (byte) ((1 << 4) | 1); // 1 target, 1 damage line
            Map<Integer, List<Integer>> bounceMap = Map.of(target.getObjectId(), List.of(bounceDmg));
            Packet bouncePacket = PacketCreator.magicAttack(chr, attack.skill, attack.skilllevel,
                    attack.stance, bounceNum, bounceMap, -1, attack.speed,
                    attack.direction, attack.display);
            map.broadcastMessage(bouncePacket);
            if (applyPoison && bounceEffect.makeChanceResult()) {
                Map<MonsterStatus, Integer> stati = bounceEffect.getMonsterStati();
                if (!stati.isEmpty()) {
                    target.applyStatus(chr, new MonsterStatusEffect(stati, skill, null, false),
                            bounceEffect.isPoison(), bounceEffect.getDuration());
                }
            }
            map.damageMonster(chr, target, bounceDmg);
        }
    }

    static int calculateBounceCount(int alreadyHitCount, int candidateCount) {
        if (candidateCount <= 0 || alreadyHitCount >= 6) {
            return 0;
        }
        return Math.min(5, Math.min(candidateCount, 6 - Math.max(0, alreadyHitCount)));
    }

}
