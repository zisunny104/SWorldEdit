package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.i18n.Messages;

public final class WorkCommand extends WECommand {
    public enum Mode { CANCEL, STATUS, QUEUE }
    private final Mode mode;

    public WorkCommand(Mode mode) {
        super("/" + mode.name().toLowerCase(java.util.Locale.ROOT),
                Messages.text("work.description." + mode.name().toLowerCase(java.util.Locale.ROOT)));
        this.mode = mode;
    }

    @Override public void execute(Player player, PlayerData owner, String[] args) {
        var tracker = SWorldEdit.get().getWorkTracker();
        if (mode == Mode.CANCEL) {
            player.sendMessage(Messages.text(tracker.cancel(owner) ? "work.cancel-requested" : "work.empty"));
            return;
        }
        boolean everyone = mode == Mode.QUEUE && player.hasPermission("we.queue.others");
        var lines = tracker.status(owner, everyone);
        if (lines.isEmpty()) player.sendMessage(Messages.text("work.empty"));
        else lines.forEach(player::sendMessage);
    }
}
