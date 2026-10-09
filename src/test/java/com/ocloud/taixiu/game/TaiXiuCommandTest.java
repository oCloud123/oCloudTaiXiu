package com.ocloud.taixiu.game;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaiXiuCommandTest {
    @Test void outcomeOverrideRequiresItsOwnPermission() throws Exception {
        var service = mock(TaiXiuService.class);
        var sender = mock(CommandSender.class);
        var command = new TaiXiuCommand(service);
        command.onCommand(sender, null, "tx", new String[]{"setnext", "tai"});
        verify(service, never()).setNextOutcome(any(), any());
        assertFalse(command.onTabComplete(sender, null, "tx", new String[]{""}).contains("setnext"));
        when(sender.hasPermission("ocloudtaixiu.setnext")).thenReturn(true);
        when(sender.getName()).thenReturn("admin");
        command.onCommand(sender, null, "tx", new String[]{"setnext", "boba"});
        verify(service).setNextOutcome(NextOutcome.TRIPLE, "admin");
        command.onCommand(sender, null, "tx", new String[]{"setnext", "random"});
        verify(service).setNextOutcome(null, "admin");
    }

    @Test void invalidOutcomeCannotClearAnExistingOverride() throws Exception {
        var service = mock(TaiXiuService.class);
        var sender = mock(CommandSender.class);
        when(sender.hasPermission("ocloudtaixiu.setnext")).thenReturn(true);
        new TaiXiuCommand(service).onCommand(sender, null, "tx", new String[]{"setnext", "invalid"});
        verify(service, never()).setNextOutcome(any(), any());
    }
}
