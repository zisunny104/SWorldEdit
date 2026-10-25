package ru.SocialMoods.SWorldEdit.commands;

import cn.nukkit.Player;
import cn.nukkit.math.Vector3;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.SWorldEdit;
import ru.SocialMoods.SWorldEdit.utils.StackOperation;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

public class StackCommand extends WECommand {
    private final Set<String> busy = new HashSet<>();
    public StackCommand() {
        super("/stack", "Repeat the selection in a direction");
        this.commandParameters.clear();
        this.commandParameters.put("default", new cn.nukkit.command.data.CommandParameter[]{
            cn.nukkit.command.data.CommandParameter.newType("count", cn.nukkit.command.data.CommandParamType.INT),
            cn.nukkit.command.data.CommandParameter.newEnum("direction", true,
                new String[]{"north", "south", "east", "west", "up", "down"}),
            cn.nukkit.command.data.CommandParameter.newEnum("ignoreAir", true, new String[]{"-a"})
        });
        this.commandParameters.put("ignore-air", new cn.nukkit.command.data.CommandParameter[]{
            cn.nukkit.command.data.CommandParameter.newType("count", cn.nukkit.command.data.CommandParamType.INT),
            cn.nukkit.command.data.CommandParameter.newEnum("ignoreAir", new String[]{"-a"})
        });
    }

    public static int[] direction(String name, Vector3 look) {
        if (name == null) {
            double x = Math.abs(look.x), y = Math.abs(look.y), z = Math.abs(look.z);
            if (y > x && y > z) return new int[]{0, look.y < 0 ? -1 : 1, 0};
            if (x > z) return new int[]{look.x < 0 ? -1 : 1, 0, 0};
            return new int[]{0, 0, look.z < 0 ? -1 : 1};
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "north", "n" -> new int[]{0, 0, -1};
            case "south", "s" -> new int[]{0, 0, 1};
            case "east", "e" -> new int[]{1, 0, 0};
            case "west", "w" -> new int[]{-1, 0, 0};
            case "up", "u" -> new int[]{0, 1, 0};
            case "down", "d" -> new int[]{0, -1, 0};
            default -> throw new IllegalArgumentException("Unknown direction: " + name);
        };
    }

    public record Arguments(int count, String direction, boolean skipAir) { }
    public static Arguments parse(String[] args) {
        if(args.length < 1 || args.length > 3) throw new IllegalArgumentException("Invalid stack arguments");
        boolean skipAir = args[args.length-1].equals("-a");
        int positional = args.length - (skipAir ? 1 : 0);
        if(positional < 1 || positional > 2) throw new IllegalArgumentException("Invalid stack arguments");
        String direction = positional == 2 ? args[1] : null;
        direction(direction,new Vector3()); // Reject unknown flags/directions before starting work.
        return new Arguments(Integer.parseInt(args[0]),direction,skipAir);
    }

    @Override public void execute(Player p, PlayerData data, String[] args) {
        if (args.length < 1 || args.length > 3) {
            p.sendMessage("Usage: //stack <count> [north|south|east|west|up|down] [-a]");
            return;
        }
        if (data == null || !data.hasSelection()) {
            p.sendMessage("Please select area first using //pos1 and //pos2");
            return;
        }
        String key = p.getName().toLowerCase(Locale.ROOT);
        if (busy.contains(key)) { p.sendMessage("Your previous stack is still running"); return; }
        try {
            Arguments parsed = parse(args);
            int count = parsed.count();
            int[] direction = direction(parsed.direction(), p.getDirectionVector());
            SWorldEdit plugin = SWorldEdit.get();
            StackOperation operation = new StackOperation(data, data.getSelection().getPosOne(),
                data.getSelection().getPosTwo(), count, direction[0], direction[1], direction[2],
                plugin.getConfig().getInt("stack.max-blocks", 100000),
                plugin.getConfig().getInt("stack.blocks-per-tick", 512), parsed.skipAir(),
                task -> plugin.getServer().getScheduler().scheduleDelayedTask(plugin, task, 1));
            busy.add(key);
            operation.start().whenComplete((changed, error) -> {
                busy.remove(key);
                if (error == null) p.sendMessage(String.format("Successfully stacked %1$s blocks", changed));
                else p.sendMessage(String.format("Stack failed; use //undo for partial changes: %1$s", error.getMessage()));
            });
        } catch (RuntimeException e) { p.sendMessage(String.format("Cannot stack: %1$s", e.getMessage())); }
    }
}

