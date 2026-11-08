package com.fsrforgeopti;

import java.util.Arrays;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.BlockPos;

/** /fsr <on/off> [escala 40-100 | 0.4-1.0] [nitidez 0.0-1.0] */
public class FSRCommand extends CommandBase {
    @Override public String getCommandName() { return "fsr"; }

    @Override public String getCommandUsage(ICommandSender s) {
        return "/fsr <on/off> [escala 40-100] [nitidez 0.0-1.0]";
    }

    @Override public boolean canCommandSenderUseCommand(ICommandSender s) { return true; }

    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) { status(); return; }

        if (args[0].equalsIgnoreCase("on")) FSRConfig.enabled = true;
        else if (args[0].equalsIgnoreCase("off")) FSRConfig.enabled = false;
        else throw new WrongUsageException(getCommandUsage(sender));

        if (args.length > 1) {
            double v = parseDouble(args[1]);
            if (v > 1.0) v /= 100.0;
            FSRConfig.scale = FSRConfig.clamp((float) v, 0.40f, 1.0f);
        }
        if (args.length > 2) {
            FSRConfig.sharpness = FSRConfig.clamp((float) parseDouble(args[2]), 0f, 1f);
        }
        FSRConfig.save();
        status();
    }

    private void status() {
        FSRForgeOpti.chat("FSR " + (FSRConfig.enabled ? "\u00a7aON" : "\u00a7cOFF")
            + "\u00a7f | escala " + Math.round(FSRConfig.scale * 100f) + "%"
            + " | nitidez " + String.format("%.2f", FSRConfig.sharpness)
            + " | OptiFine: " + (OptiFineCompat.isPresent() ? "sim" : "nao"));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender s, String[] args, BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "on", "off") : null;
    }
}
