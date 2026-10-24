package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.Player;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import lombok.extern.slf4j.Slf4j;
import ru.SocialMoods.SWorldEdit.PlayerData;
import ru.SocialMoods.SWorldEdit.service.SchematicService;

@Slf4j
public class SchematicCommand extends WECommand {
    private final SchematicService schematicService;

    public SchematicCommand(SchematicService schematicService) {
        super("/schem", Messages.text("text.save.and.load.schematics"));
        this.schematicService = schematicService;

        this.setAliases(java.util.List.of("/schematic").toArray(new String[0]));
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                CommandParameter.newEnum("action", new String[]{"save", "paste"}),
                CommandParameter.newType("name", CommandParamType.STRING)
        });
    }

    @Override
    public void execute(Player player, PlayerData dat, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Messages.text("text.usage.schem.save.paste.name.x.y.z"));
            return;
        }

        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        String name = args[1];

        switch (action) {
            case "save" -> {
                player.sendMessage(Messages.text("text.saving.schematic"));
                schematicService.save(dat, name).whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("Error while saving schematic {}", name, ex);
                        player.sendMessage(Messages.text("text.error", ex.getMessage()));
                        return;
                    }
                    player.sendMessage(Messages.text("text.schematic.has.been.saved", name));
                });
            }
            case "paste" -> {
                int x = player.getFloorX();
                int y = player.getFloorY();
                int z = player.getFloorZ();
                var center = player.getLevel().getBlock(x, y, z).getLocation();
                player.sendMessage(Messages.text("text.loading.schematic"));
                schematicService.load(dat, center, name).whenComplete((placed, ex) -> {
                    if (ex != null) {
                        log.error("Error while pasting schematic {}", name, ex);
                        player.sendMessage(Messages.text("text.error", ex.getMessage()));
                        return;
                    }
                    player.sendMessage(Messages.text("text.schematic.has.been.pasted.blocks", name, placed));
                });
            }
            default -> player.sendMessage(Messages.text("text.unknown.action", action));
        }
    }
}
