package www.ontologyutils.apps;

import java.util.List;

/**
 * Runs the comparison reported in the paper: the three baselines A1, A2 and A3
 * plus the Shapley-Shapley repair, where the bad axiom and the weaker
 * replacement are both chosen by their Shapley inconsistency value.
 *
 * <p>
 * The other arms of the grid are still available in
 * {@link SingleTrialAblationExperiment}. The Shapley-Shapley arm keeps the id
 * {@code B5} it has in the full grid, so its results stay comparable with earlier
 * runs and the analysis scripts do not need to know which experiment produced
 * them.
 */
public class SingleTrialExperiment extends SingleTrialAblationExperiment {
    private static final List<BRepairSpec> SHAPLEY_SPECS = List.of(
            new BRepairSpec("B5", BadSelectorKind.SHAPLEY_APPROX, WeakerSelectorKind.SHAPLEY_APPROX));

    @Override
    protected List<BRepairSpec> repairSpecs() {
        return SHAPLEY_SPECS;
    }

    public static void main(String[] args) {
        new SingleTrialExperiment().run(args);
    }
}
