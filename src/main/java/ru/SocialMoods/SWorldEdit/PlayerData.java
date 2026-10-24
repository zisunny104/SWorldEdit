package ru.SocialMoods.SWorldEdit;

import java.util.ArrayList;

import lombok.Getter;
import ru.SocialMoods.SWorldEdit.utils.BlocksArray;
import ru.SocialMoods.SWorldEdit.utils.Selection;

@Getter
public class PlayerData {
    private final cn.nukkit.Player player;
    private final Selection selection;
    private final ru.SocialMoods.SWorldEdit.utils.ToolBindings tools = new ru.SocialMoods.SWorldEdit.utils.ToolBindings();
    public BlocksArray copiedBlocks = null;
    public final ArrayList<BlocksArray> undoSteps = new ArrayList<>();

    public PlayerData(cn.nukkit.Player p) {
        this.player = p;
        this.selection = new Selection();
    }

    public void addUndoBlocks(BlocksArray blocks) {
        this.undoSteps.add(blocks);
    }

    public boolean hasSelection() {
        return this.getSelection().getPosOne() != null && this.getSelection().getPosTwo() != null;
    }
}

