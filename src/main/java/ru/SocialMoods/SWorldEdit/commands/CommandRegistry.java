package ru.SocialMoods.SWorldEdit.commands;

import ru.SocialMoods.SWorldEdit.commands.geometry.*;
import ru.SocialMoods.SWorldEdit.service.SchematicService;
import java.util.List;

public final class CommandRegistry {
    private CommandRegistry() { }
    public static List<WECommand> create(SchematicService schematics) {
        var commands = List.of(new HelpCommand(), new PosOneCommand(), new PosTwoCommand(), new WandCommand(), new ToolCommand(),
                new CopyCommand(), new StackCommand(), new PasteCommand(), new SetCommand(), new WallsCommand(),
                new CutCommand(), new UndoCommand(), new DeselCommand(), new ReplaceCommand(), new RotateCommand(),
                new CylCommand(), new HCylCommand(), new SphereCommand(), new HSphereCommand(), new SchematicCommand(schematics));
        commands.forEach(command -> command.setUsage(command.syntax()));
        return commands;
    }
}
