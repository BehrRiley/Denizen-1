package com.denizenscript.denizen.scripts.commands.core;

import com.denizenscript.denizen.scripts.containers.core.InteractScriptHelper;
import com.denizenscript.denizen.utilities.DenizenAPI;
import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizen.utilities.debugging.Debug;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.objects.core.DurationTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ScriptTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

public class ZapCommand extends AbstractCommand implements Listener {

    public ZapCommand() {
        setName("zap");
        setSyntax("zap (<script>) [<step>] (<duration>)");
        setRequiredArguments(0, 3);
    }

    // <--[command]
    // @Name Zap
    // @Syntax zap (<script>) [<step>] (<duration>)
    // @Required 0
    // @Maximum 3
    // @Short Changes the current interact script step.
    // @Group core
    // @Guide https://guide.denizenscript.com/guides/npcs/interact-scripts.html
    //
    // @Description
    // Changes the current interact script step for the linked player.
    //
    // The step name input should match the name of a step in the interact script.
    //
    // If used inside an interact script, will default to the current interact script.
    // For anywhere else, you must specify the script by name.
    //
    // Optionally specify a duration. When the duration is up, the script will zap back to the step it was previously on.
    // If any zap commands are used during the duration, that duration will be discarded.
    //
    // The command's name was inspired by a command in the language "ZZT-OOP", from a 1991 DOS game enjoyed by the original developer of Denizen.
    //
    // @Tags
    // <ScriptTag.step[<player>]>
    //
    // @Usage
    // Use to change the step to 2
    // - zap 2
    //
    // @Usage
    // Use to change the step to 3 in a script called Interact_Example.
    // - zap 3 Interact_Example
    //
    // @Usage
    // Use to change the step to 1 for the defined player in a script called InteractScript.
    // - zap 1 InteractScript player:<[player]>
    // -->

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry.getProcessedArgs()) {

            // If the scripter uses the 'script:step' format, handle it.
            if (!scriptEntry.hasObject("script")
                    && !scriptEntry.hasObject("step")
                    && arg.hasPrefix()
                    && arg.getPrefix().matchesArgumentType(ScriptTag.class)) {
                scriptEntry.addObject("script", arg.getPrefix().asType(ScriptTag.class));
                scriptEntry.addObject("step", arg.asElement());
            }

            // If a script is found, use that to ZAP
            else if (!scriptEntry.hasObject("script")
                    && arg.matchesArgumentType(ScriptTag.class)
                    && !arg.matchesPrefix("step")) {
                scriptEntry.addObject("script", arg.asType(ScriptTag.class));
            }

            // Add argument as step
            else if (!scriptEntry.hasObject("step")) {
                scriptEntry.addObject("step", arg.asElement());
            }

            // Lastly duration
            else if (!scriptEntry.hasObject("duration")
                    && arg.matchesArgumentType(DurationTag.class)) {
                scriptEntry.addObject("duration", arg.asType(DurationTag.class));
            }
            else {
                arg.reportUnhandled();
            }
        }

        // Add default script if none was specified.
        scriptEntry.defaultObject("script", scriptEntry.getScript());

        // Check if player is valid
        if (!Utilities.entryHasPlayer(scriptEntry) || !Utilities.getEntryPlayer(scriptEntry).isValid()) {
            throw new InvalidArgumentsException("Must have player context!");
        }
    }

    //"PlayerName,ScriptName", TaskID
    private static Map<String, Integer> durations = new HashMap<>();

    @Override
    public void execute(final ScriptEntry scriptEntry) {

        final ScriptTag script = scriptEntry.getObjectTag("script");
        DurationTag duration = scriptEntry.getObjectTag("duration");

        if (scriptEntry.dbCallShouldDebug()) {

            Debug.report(scriptEntry, getName(), Utilities.getEntryPlayer(scriptEntry).debug() + script.debug()
                    + (scriptEntry.hasObject("step")
                    ? scriptEntry.getElement("step").debug() : ArgumentHelper.debugObj("step", "++ (inc)"))
                    + (duration != null ? duration.debug() : ""));

        }

        String step = scriptEntry.hasObject("step") ? scriptEntry.getElement("step").asString() : null;

        // Let's get the current step for reference.
        String currentStep = InteractScriptHelper.getCurrentStep(Utilities.getEntryPlayer(scriptEntry), script.getName());

        // Special-case for backwards compatibility: ability to use ZAP to count up steps.
        if (step == null) {
            // Okay, no step was identified.. that means we should count up,
            // ie. if currentStep = 1, new step should = 2
            // If the currentStep is a number, increment it. If not, set it
            // to '1' so it can be incremented next time.
            if (ArgumentHelper.matchesInteger(currentStep)) {
                step = String.valueOf(Integer.parseInt(currentStep) + 1);
            }
            else {
                step = "1";
            }
        }

        if (step.equalsIgnoreCase(currentStep)) {
            Debug.echoError(scriptEntry.getResidingQueue(), "Zapping to own current step!");
            return;
        }

        // If the durationsMap already contains an entry for this player/script combination,
        // cancel the task since it's probably not desired to change back anymore if another
        // ZAP for this script is taking place.
        String durationKey = Utilities.getEntryPlayer(scriptEntry).getSaveName() + "," + script.getName();
        Integer durationObj = durations.get(durationKey);
        if (durationObj != null) {
            try {
                DenizenAPI.getCurrentInstance().getServer().getScheduler().cancelTask(durationObj);
            }
            catch (Exception ex) {
                Debug.echoError(ex);
            }
        }

        // One last thing... check for duration.
        if (duration != null && duration.getSeconds() > 0) {
            // If a DURATION is specified, the currentStep should be remembered and
            // restored after the duration.
            scriptEntry.addObject("step", new ElementTag(currentStep));
            // And let's take away the duration that was set to avoid a re-duration
            // inception-ion-ion-ion-ion... ;)
            scriptEntry.addObject("duration", DurationTag.ZERO);

            // Now let's add a delayed task to set it back after the duration

            // Delays are in ticks, so let's multiply our duration (which is in seconds) by 20.
            // 20 ticks per second.
            long delay = (long) (duration.getSeconds() * 20);

            // Set delayed task and put id in a map
            Debug.log("Setting delayed task 'RESET ZAP' for '" + script.identify() + "'");
            durations.put(durationKey,
                    DenizenAPI.getCurrentInstance().getServer().getScheduler().scheduleSyncDelayedTask(DenizenAPI.getCurrentInstance(),
                            new Runnable() {
                                @Override
                                public void run() {
                                    Debug.log("Running delayed task 'RESET ZAP' for '" + script.identify() + "'");
                                    durations.remove(durationKey);
                                    execute(scriptEntry);
                                }
                            }, delay));
        }

        //
        // FINALLY! ZAP! Change the step in Saves... your step is now ZAPPED!
        // Fun fact: ZAP is named in homage of ZZT-OOPs ZAP command. Google it.
        //
        DenizenAPI.getCurrentInstance().getSaves().set("Players." + Utilities.getEntryPlayer(scriptEntry).getSaveName()
                + ".Scripts." + script.getName().toUpperCase() + "." + "Current Step", step);
    }
}
