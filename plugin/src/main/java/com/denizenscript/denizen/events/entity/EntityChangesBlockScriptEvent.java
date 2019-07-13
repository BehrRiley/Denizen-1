package com.denizenscript.denizen.events.entity;

import com.denizenscript.denizen.objects.dCuboid;
import com.denizenscript.denizen.objects.dEntity;
import com.denizenscript.denizen.objects.dLocation;
import com.denizenscript.denizen.objects.dMaterial;
import com.denizenscript.denizen.utilities.debugging.Debug;
import com.denizenscript.denizen.BukkitScriptEntryData;
import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizencore.objects.dList;
import com.denizenscript.denizencore.objects.dObject;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.scripts.containers.ScriptContainer;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

public class EntityChangesBlockScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // entity changes block
    // entity changes block (into <material>)
    // entity changes <material> (into <material>)
    // <entity> changes block (into <material>)
    // <entity> changes <material> (into <material>)
    //
    // @Regex ^on [^\s]+ changes [^\s]+( into [^\s]+)?$
    // @Switch in <area>
    //
    // @Cancellable true
    //
    // @Triggers when an entity changes the material of a block.
    //
    // @Context
    // <context.entity> returns the dEntity that changed the block.
    // <context.location> returns the dLocation of the changed block.
    // <context.old_material> returns the old material of the block.
    // <context.new_material> returns the new material of the block.
    //
    // @Player when the entity that changed the block is a player.
    //
    // -->

    public EntityChangesBlockScriptEvent() {
        instance = this;
    }

    public static EntityChangesBlockScriptEvent instance;
    public dEntity entity;
    public dLocation location;
    public dMaterial old_material;
    public dMaterial new_material;
    public dList cuboids;
    public EntityChangeBlockEvent event;

    @Override
    public boolean couldMatch(ScriptContainer scriptContainer, String s) {
        String lower = CoreUtilities.toLowerCase(s);
        return CoreUtilities.xthArgEquals(1, lower, "changes");
    }

    @Override
    public boolean matches(ScriptPath path) {
        String entName = path.eventArgLowerAt(0);

        if (!tryEntity(entity, entName)) {
            return false;
        }

        if (!tryMaterial(old_material, path.eventArgLowerAt(2))) {
            return false;
        }

        if (path.eventArgLowerAt(3).equals("into")) {
            String mat2 = path.eventArgLowerAt(4);
            if (mat2.isEmpty()) {
                Debug.echoError("Invalid event material [" + getName() + "]: '" + path.event + "' for " + path.container.getName());
                return false;
            }
            else if (!tryMaterial(new_material, mat2)) {
                return false;
            }
        }

        if (!runInCheck(path, location)) {
            return false;
        }

        return true;
    }

    @Override
    public String getName() {
        return "EntityChangesBlock";
    }

    @Override
    public boolean applyDetermination(ScriptContainer container, String determination) {
        return super.applyDetermination(container, determination);
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(entity.isPlayer() ? dEntity.getPlayerFrom(event.getEntity()) : null,
                entity.isCitizensNPC() ? dEntity.getNPCFrom(event.getEntity()) : null);
    }

    @Override
    public dObject getContext(String name) {
        if (name.equals("entity")) {
            return entity;
        }
        else if (name.equals("cuboids")) { // NOTE: Deprecated
            if (cuboids == null) {
                cuboids = new dList();
                for (dCuboid cuboid : dCuboid.getNotableCuboidsContaining(location)) {
                    cuboids.add(cuboid.identifySimple());
                }
            }
            return cuboids;
        }
        else if (name.equals("location")) {
            return location;
        }
        else if (name.equals("new_material")) {
            return new_material;
        }
        else if (name.equals("old_material")) {
            return old_material;
        }
        return super.getContext(name);
    }

    @EventHandler
    public void onEntityChangesBlock(EntityChangeBlockEvent event) {
        entity = new dEntity(event.getEntity());
        location = new dLocation(event.getBlock().getLocation());
        old_material = new dMaterial(location.getBlock());
        new_material = new dMaterial(event.getTo());
        cuboids = null;
        this.event = event;
        fire(event);
    }
}
