package ru.SocialMoods.SWorldEdit.commands;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreenCommandTest {
    @Test void parsesDefaultsDecimalsHeightAndAdvertisedFlagPositions(){
        assertEquals(new GreenCommand.Arguments(10,384,false),GreenCommand.parse(new String[0],10,384));
        for(String[] args:new String[][]{{"-f","2.5","8"},{"2.5","-f","8"},{"2.5","8","-f"}})
            assertEquals(new GreenCommand.Arguments(2.5,8,true),GreenCommand.parse(args,10,384));
        assertEquals(new GreenCommand.Arguments(10,384,true),GreenCommand.parse(new String[]{"-f"},10,384));
    }
    @Test void rejectsDuplicateUnknownFlagsAndInvalidNumericArguments(){
        for(String[] args:new String[][]{{"-f","-f"},{"-a"},{"NaN"},{"Infinity"},{"0"},{"-1"},{"2","0"},{"2","1.5"},{"1","2","3"}})
            assertThrows(IllegalArgumentException.class,()->GreenCommand.parse(args,10,384));
    }
}
