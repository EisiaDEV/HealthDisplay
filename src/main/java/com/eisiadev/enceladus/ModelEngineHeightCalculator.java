package com.eisiadev.enceladus;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class ModelEngineHeightCalculator {

    public double getModelHeight(Entity entity) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
        if (modeled == null || modeled.getModels() == null) {
            return 0;
        }

        double maxHeight = 0;
        Location entityLoc = entity.getLocation();

        for (ActiveModel model : modeled.getModels().values()) {
            if (model != null) {
                double modelHeight = model.getModeledEntity().getBase().getLocation().getY()
                        + model.getBlueprint().getMainHitbox().getHeight()
                        - entityLoc.getY();
                maxHeight = Math.max(maxHeight, modelHeight);
            }
        }

        return maxHeight;
    }
}