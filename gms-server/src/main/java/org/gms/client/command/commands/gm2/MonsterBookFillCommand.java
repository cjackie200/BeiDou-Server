package org.gms.client.command.commands.gm2;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.MonsterBook;
import org.gms.client.command.Command;
import org.gms.util.I18nUtil;


public class MonsterBookFillCommand extends Command {
    {
        setDescription(I18nUtil.getMessage("MonsterBookFillCommand.message1"));
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (player == null) {
            return;
        }
        int sets = 1;
        if (params.length >= 1) {
            sets = Math.max(1, Math.min(1000, Integer.parseInt(params[0])));
        }

        MonsterBook book = player.getMonsterBook();
        if (book == null) {
            player.yellowMessage("No monster book found. Kill one monster first.");
            return;
        }

        if (book.getCardSet().isEmpty()) {
            player.yellowMessage("Monster book is empty. Kill one monster first.");
            return;
        }

        int filled = 0;
        for (var entry : book.getCardSet()) {
            int cardId = entry.getKey();
            int current = entry.getValue();
            if (current < sets) {
                // Add cards up to the desired count (max 5 per card)
                int toAdd = Math.min(sets, 5) - current;
                for (int i = 0; i < toAdd; i++) {
                    book.addCard(c, cardId);
                }
            }
            filled++;
        }

        player.yellowMessage("Filled " + filled + " card types to at least " + Math.min(sets, 5) + " cards each.");
    }
}
