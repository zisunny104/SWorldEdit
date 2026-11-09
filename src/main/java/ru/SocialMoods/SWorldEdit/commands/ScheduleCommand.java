package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.level.Position;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.i18n.Messages;
import ru.SocialMoods.SWorldEdit.utils.Selection;
import java.util.Arrays;

/** Session-only delayed selection edits retain the submitted selection. */
public final class ScheduleCommand extends WECommand {
    public ScheduleCommand() {
        super("/schedule", Messages.text("work.description.schedule"));
        commandParameters.put("default", new CommandParameter[]{
                CommandParameter.newType("seconds", CommandParamType.INT),
                CommandParameter.newEnum("command", new cn.nukkit.command.data.CommandEnum("SWorldEditScheduledEdit",
                        new String[]{"set", "walls", "replace", "cut", "smooth", "naturalize"})),
                CommandParameter.newType("arguments", true, CommandParamType.RAWTEXT)});
    }

    private static Position snapshot(Position point) {
        return point == null ? null : new Position(point.x, point.y, point.z, point.level);
    }

    @Override public void execute(Player player, PlayerData owner, String[] args) {
        var plugin = SWorldEdit.get();
        String name = args[1].replaceFirst("^/+", "").toLowerCase(java.util.Locale.ROOT);
        var command = plugin.getServer().getCommandMap().getCommand("/" + name);
        if (!(command instanceof WECommand edit) || !java.util.Set.of(
                "set", "walls", "replace", "cut", "smooth", "naturalize").contains(name)) {
            player.sendMessage(Messages.text("work.schedule-command"));
            return;
        }
        String[] arguments = Arrays.copyOfRange(args, 2, args.length);
        Position first = snapshot(owner.getSelection().getPosOne());
        Position second = snapshot(owner.getSelection().getPosTwo());
        if (first == null || second == null || first.level != second.level) {
            player.sendMessage(Messages.text("text.please.select.area.first.using.pos1.and.pos2"));
            return;
        }
        Position origin = snapshot(player);
        String label = "//" + name + (arguments.length == 0 ? "" : " " + String.join(" ", arguments));
        long id = plugin.getWorkTracker().delay(owner, label, Integer.parseInt(args[0]),
                () -> owner.getPlayer().isOnline() && owner.getPlayer().hasPermission("we.command")
                        && plugin.getServer().getLevel(origin.level.getId()) == origin.level
                        && plugin.getServer().getLevel(first.level.getId()) == first.level
                        && owner.getPlayer().getLevel() == origin.level,
                () -> {
                    Player current = owner.getPlayer();
                    Selection selection = owner.getSelection();
                    Position previousFirst = selection.getPosOne(), previousSecond = selection.getPosTwo();
                    selection.setPosOne(first);
                    selection.setPosTwo(second);
                    try { edit.execute(current, edit.getName(), arguments); }
                    finally { selection.setPosOne(previousFirst); selection.setPosTwo(previousSecond); }
                });
        player.sendMessage(Messages.text("work.scheduled", id, args[0], label));
    }
}
