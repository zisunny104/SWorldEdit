package ru.SocialMoods.SWorldEdit;

import cn.nukkit.plugin.PluginBase;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import ru.SocialMoods.SWorldEdit.commands.*;
import ru.SocialMoods.SWorldEdit.commands.geometry.*;
import ru.SocialMoods.SWorldEdit.repository.SchematicRepository;
import ru.SocialMoods.SWorldEdit.repository.impl.SchematicRepositoryImpl;
import ru.SocialMoods.SWorldEdit.service.SchematicService;
import ru.SocialMoods.SWorldEdit.service.impl.SchematicServiceImpl;

@Slf4j
public class SWorldEdit extends PluginBase {

    private final Map<String, PlayerData> players = new HashMap<>();
    private static SWorldEdit instance;
    @Getter private SchematicRepository schematicRepository;
    @Getter private SchematicService schematicService;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        try {
            this.getServer().getPluginManager().registerEvents(new Listener(), this);

            schematicRepository = new SchematicRepositoryImpl(getDataFolder());
            schematicService = new SchematicServiceImpl(schematicRepository);

            registerCommands();

            this.getLogger().info("SWorldEdit has been enabled successfully");

        } catch (Exception e) {
            this.getLogger().error("Error enabling SWorldEdit: " + e);
        }
    }

    @Override
    public void onDisable() {
        try {
            this.getLogger().info("SWorldEdit has been disabled");
        } catch (Exception e) {
            this.getLogger().error("Error disabling SWorldEdit: " + e);
        }
    }

    private void registerCommands() {
        try {
            this.getServer().getCommandMap().register("worldedit", new HelpCommand());
            this.getServer().getCommandMap().register("worldedit", new PosOneCommand());
            this.getServer().getCommandMap().register("worldedit", new PosTwoCommand());
            this.getServer().getCommandMap().register("worldedit", new WandCommand());
            this.getServer().getCommandMap().register("worldedit", new CopyCommand());
            this.getServer().getCommandMap().register("worldedit", new StackCommand());
            this.getServer().getCommandMap().register("worldedit", new PasteCommand());
            this.getServer().getCommandMap().register("worldedit", new SetCommand());
            this.getServer().getCommandMap().register("worldedit", new WallsCommand());
            this.getServer().getCommandMap().register("worldedit", new CutCommand());
            this.getServer().getCommandMap().register("worldedit", new UndoCommand());
            this.getServer().getCommandMap().register("worldedit", new DeselCommand());
            this.getServer().getCommandMap().register("worldedit", new ReplaceCommand());
            this.getServer().getCommandMap().register("worldedit", new RotateCommand());
            this.getServer().getCommandMap().register("worldedit", new CylCommand());
            this.getServer().getCommandMap().register("worldedit", new HCylCommand());
            this.getServer().getCommandMap().register("worldedit", new SphereCommand());
            this.getServer().getCommandMap().register("worldedit", new HSphereCommand());
            this.getServer().getCommandMap().register("worldedit", new SchematicCommand(schematicService));
        } catch (Exception e) {
            this.getLogger().error("Error registering commands: " + e.getMessage());
        }
    }

    public static SWorldEdit get() {
        return instance;
    }

    public PlayerData getWEPlayer(cn.nukkit.Player player) {
        if (player == null) {
            return null;
        }

        String playerName = player.getName().toLowerCase();
        PlayerData data = this.players.get(playerName);

        if (data == null) {
            try {
                data = new PlayerData(player);
                this.players.put(playerName, data);
            } catch (Exception e) {
                this.getLogger().error("Error creating WEPlayer for " + playerName + ": " + e.getMessage());
                return null;
            }
        }

        return data;
    }
}
