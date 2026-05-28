package www.ontologyutils.repair.powerindex;

import java.util.Map;

import www.ontologyutils.toolbox.Utils;

/**
 * Minimal factory for creating PowerIndex instances from built-in types.
 * ServiceLoader/plugin discovery is postponed and can be added later.
 */
public final class PowerIndexRegistry {
    private PowerIndexRegistry() {}

    public static PowerIndex create(PowerIndexType type) {
        return create(type, Map.of());
    }

    public static PowerIndex create(PowerIndexType type, Map<String, Object> config) {
        switch (type) {
            case SHAPLEY_EXACT:
                return new ShapleyInconsistencyValueExact();
            case SHAPLEY_APPROXIMATE: {
                int samples = config.containsKey("samples") ? (Integer)config.get("samples") : 735;
                long seed = config.containsKey("seed") ? (Long)config.get("seed") : Utils.randomLong();
                return new ShapleyInconsistencyValueApproximate(samples, seed);
            }
            case BANZHAF_APPROXIMATE: {
                int samples = config.containsKey("samples") ? (Integer)config.get("samples") : 735;
                long seed = config.containsKey("seed") ? (Long)config.get("seed") : Utils.randomLong();
                return new BanzhafInconsistencyValueApproximate(samples, seed);
            }
            default:
                throw new IllegalArgumentException("Unsupported PowerIndexType: " + type);
        }
    }
}

