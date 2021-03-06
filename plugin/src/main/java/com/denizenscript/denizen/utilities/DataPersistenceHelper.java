package com.denizenscript.denizen.utilities;

import com.denizenscript.denizen.utilities.debugging.Debug;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.objects.ObjectTag;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;

/**
 * Helper class for PersistentDataContainers.
 */
public class DataPersistenceHelper {

    public static class DenizenObjectType implements PersistentDataType<String, ObjectTag> {
        @Override
        public Class<String> getPrimitiveType() {
            return String.class;
        }

        @Override
        public Class<ObjectTag> getComplexType() {
            return ObjectTag.class;
        }

        @Override
        public String toPrimitive(ObjectTag complex, PersistentDataAdapterContext context) {
            return complex.toString();
        }

        @Override
        public ObjectTag fromPrimitive(String primitive, PersistentDataAdapterContext context) {
            return ObjectFetcher.pickObjectFor(primitive);
        }
    }

    public static final DenizenObjectType PERSISTER_TYPE = new DenizenObjectType();

    public static void setDenizenKey(Entity entity, String keyName, ObjectTag keyValue) {
        entity.getPersistentDataContainer().set(new NamespacedKey(DenizenAPI.getCurrentInstance(), keyName), PERSISTER_TYPE, keyValue);
    }

    public static boolean hasDenizenKey(Entity entity, String keyName) {
        return entity.getPersistentDataContainer().has(new NamespacedKey(DenizenAPI.getCurrentInstance(), keyName), PERSISTER_TYPE);
    }

    public static ObjectTag getDenizenKey(Entity entity, String keyName) {
        try {
            return entity.getPersistentDataContainer().get(new NamespacedKey(DenizenAPI.getCurrentInstance(), keyName), PERSISTER_TYPE);
        }
        catch (NullPointerException ex) {
            return null;
        }
        catch (IllegalArgumentException ex) {
            Debug.echoError("Failed to read ObjectTag from entity key '" + keyName + "' for entity " + entity.getUniqueId() + "...");
            Debug.echoError(ex);
            return null;
        }
    }
}
