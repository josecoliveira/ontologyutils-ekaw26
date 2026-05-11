package www.ontologyutils.repair;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.powerindex.*;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * An implementation of {@code OntologyRepair} using power indexes to select bad
 * axioms and weaker replacements. This extends the axiom weakening approach by
 * using a power index (e.g., Shapley value) to identify axioms with the highest
 * influence on inconsistency, and weakenings with the lowest influence.
 *
 * The algorithm:
 * 1. Takes a reference ontology using a strategy.
 * 2. While the ontology is not repaired:
 * 3. Select the bad axiom with the highest power index score.
 * 4. Get the set of weakened axioms of the bad axiom.
 * 5. Select the weaker axiom with the lowest power index score.
 * 6. Replace the bad axiom with the weaker axiom on the ontology.
 * 7. Return the repaired ontology.
 */
public class OntologyRepairWithPowerIndexes extends OntologyRepairWeakening {
    /**
     * Possible strategies for computing the reference ontology.
     */
    public static enum RefOntologyStrategy {
        /**
         * Compute all maximal consistent subsets and select one at random.
         */
        RANDOM_MCS,
        /**
         * Compute some (but not necessarily all) maximal consistent subsets and select
         * one at random.
         */
        SOME_MCS,
        /**
         * Compute one maximal consistent subsets and select it.
         */
        ONE_MCS,
        /**
         * Compute the largest maximal consistent subsets and select one at random.
         */
        LARGEST_MCS,
        /**
         * Compute all maximal consistent subsets and select the intersection of them.
         */
        INTERSECTION_OF_MCS,
        /**
         * Compute some (but not necessarily all) maximal consistent subsets and select
         * the intersection of them.
         */
        INTERSECTION_OF_SOME_MCS,
    }

    /**
     * Possible strategies for computing bad axioms.
     */
    public static enum BadAxiomStrategy {
        /**
         * Select the weaker axiom with the lowest Shapley inconsistency value (exact
         * computation).
         */
        SHAPLEY_EXACT,
        /**
         * Select the weaker axiom with the lowest Shapley inconsistency value
         * (approximate computation).
         */
        SHAPLEY_APPROXIMATE,
    }

    /**
     * Possible strategies for computing the weaker axiom to replace a bad axiom.
     */
    public static enum WeakerAxiomStrategy {
        /**
         * Select the weaker axiom with the lowest Shapley inconsistency value (exact
         * computation).
         */
        SHAPLEY_EXACT,
        /**
         * Select the weaker axiom with the lowest Shapley inconsistency value
         * (approximate computation).
         */
        SHAPLEY_APPROXIMATE,
    }

    // private final PowerIndex powerIndex;

    private final RefOntologyStrategy refOntologySource;
    private PowerIndex powerIndexBadAxiom;
    private PowerIndex powerIndexWeakerAxiom;

    /**
     * @param isRepaired
     *            The monotone predicate testing whether an ontology is repaired.
     * @param refOntologySource
     *            The strategy for computing the reference ontology.
     * @param badAxiomSource
     *            The strategy for computing bad axioms.
     * @param weakeningFlags
     *            The flags to use for weakening.
     * @param enhanceRef
     *            Use the reference ontology as a base ontology that is always
     *            included in the repair.
     * @param weakerAxiomStrategy
     *            The power index to use for selecting axioms.
     */
    public OntologyRepairWithPowerIndexes(
            Predicate<Ontology> isRepaired,
            RefOntologyStrategy refOntologySource,
            BadAxiomStrategy badAxiomSource,
            WeakerAxiomStrategy weakerAxiomStrategy,
            int weakeningFlags,
            boolean enhanceRef
        ) {
        super(isRepaired);
        this.refOntologySource = refOntologySource;
        selectPowerIndexBadAxiom(badAxiomSource);
        selectPowerIndexWeakerAxiom(weakerAxiomStrategy);
        this.weakeningFlags = weakeningFlags;
        this.enhanceRef = enhanceRef;
    }

    private void selectPowerIndexBadAxiom(BadAxiomStrategy strategy) {
        switch (strategy) {
            case SHAPLEY_EXACT -> this.powerIndexBadAxiom = new ShapleyInconsistencyValueExact();
            case SHAPLEY_APPROXIMATE -> this.powerIndexBadAxiom = new ShapleyInconsistencyValueApproximate();
            default -> throw new IllegalArgumentException("Unsupported bad axiom strategy: " + strategy);
        }
    }

    private void selectPowerIndexWeakerAxiom(WeakerAxiomStrategy strategy) {
        switch (strategy) {
            case SHAPLEY_EXACT -> this.powerIndexWeakerAxiom = new ShapleyInconsistencyValueExact();
            case SHAPLEY_APPROXIMATE -> this.powerIndexWeakerAxiom = new ShapleyInconsistencyValueApproximate();
            default -> throw new IllegalArgumentException("Unsupported weaker axiom strategy: " + strategy);
        }
    }

    /**
     * @param ontology
     *            The ontology to find a reference ontology for.
     * @return The set of axioms to include in the reference ontology to use for
     *         repairs.
     */
    public Stream<Set<OWLAxiom>> getRefAxioms(Ontology ontology) {
        switch (refOntologySource) {
            case INTERSECTION_OF_MCS: {
                return Stream.of(mcsPeekInfo(false, ontology.maximalConsistentSubsets(isRepaired)).reduce((a, b) -> {
                    a.removeIf(axiom -> !b.contains(axiom));
                    return a;
                }).get());
            }
            case INTERSECTION_OF_SOME_MCS: {
                return Stream
                        .of(mcsPeekInfo(false, ontology.someMaximalConsistentSubsets(isRepaired)).reduce((a, b) -> {
                            a.removeIf(axiom -> !b.contains(axiom));
                            return a;
                        }).get());
            }
            case LARGEST_MCS:
                return mcsPeekInfo(false, ontology.largestMaximalConsistentSubsets(isRepaired));
            case RANDOM_MCS:
                return mcsPeekInfo(false, ontology.maximalConsistentSubsets(isRepaired));
            case SOME_MCS:
                return mcsPeekInfo(false, ontology.someMaximalConsistentSubsets(isRepaired));
            case ONE_MCS: {
                var mcs = ontology.maximalConsistentSubset(isRepaired);
                if (mcs == null) {
                    return Stream.of();
                } else {
                    return Stream.of(mcs);
                }
            }
            default:
                throw new IllegalArgumentException("Unimplemented reference ontology choice strategy.");
        }
    }

    @Override
    public void repair(Ontology ontology) {
        infoMessage(Utils.formatOntologyState("Initial ontology state:", ontology));

        var refAxioms = Utils.randomChoice(getRefAxioms(ontology));
        infoMessage("Selected a reference ontology with " + refAxioms.size() + " axioms.");
        infoMessage(Utils.formatAxiomSet("Reference ontology:", refAxioms));
        if (enhanceRef) {
            ontology.addStaticAxioms(refAxioms);
        }

        try (var refOntology = ontology.cloneWithRefutable(refAxioms).withSeparateCache()) {
            var axiomWeakener = getWeakener(refOntology, ontology);
            while (!isRepaired(ontology)) {
                var currentAxioms = ontology.refutableAxioms().collect(Collectors.toSet());
                var badAxiomScores = computeBadAxiomScores(currentAxioms);
                infoMessage("Found " + badAxiomScores.size() + " possible bad axioms.");
                infoMessage(Utils.formatPowerIndexTable("Power index values for possible bad axioms:",
                        badAxiomScores.entrySet().stream(), false));
                var badAxiom = selectBadAxiom(badAxiomScores);
                var weakerAxioms = collectWeakeningCandidates(badAxiom, axiomWeakener);
                if (weakerAxioms.isEmpty()) {
                    throw new IllegalStateException(
                            "Could not weaken the selected bad axiom: " + Utils.prettyPrintAxiomDL(badAxiom));
                }
                infoMessage("Selected the bad axiom " + Utils.prettyPrintAxiomDL(badAxiom) + ".");
                var axiomSetWithoutBadAxiom = new HashSet<>(currentAxioms);
                axiomSetWithoutBadAxiom.remove(badAxiom);
                var weakerAxiomScores = scoreWeakeningCandidates(weakerAxioms, axiomSetWithoutBadAxiom);
                infoMessage("Found " + weakerAxioms.size() + " weaker axioms.");
                infoMessage(Utils.formatPowerIndexTable(
                        "Candidate weakenings and power index values for " + Utils.prettyPrintAxiomDL(badAxiom)
                                + ":",
                        weakerAxiomScores.entrySet().stream(), true));
                var weakerAxiom = selectWeakening(weakerAxiomScores, badAxiom);
                infoMessage("Selected the weaker axiom " + Utils.prettyPrintAxiomDL(weakerAxiom) + ".");

                ontology.replaceAxiom(badAxiom, weakerAxiom);
                infoMessage(Utils.formatOntologyState("Ontology state after weakening:", ontology));
            }
        }

        infoMessage(Utils.formatOntologyState("Final ontology state after repair:", ontology));
    }

    private Set<OWLAxiom> collectWeakeningCandidates(OWLAxiom axiom, AxiomWeakener weakener) {
        try {
            var weakenings = weakener.weakerAxioms(axiom).collect(Collectors.toSet());
            weakenings.remove(axiom);
            return weakenings;
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    private Map<OWLAxiom, Double> scoreWeakeningCandidates(Set<OWLAxiom> weakenings, Set<OWLAxiom> currentAxioms) {
        if (weakenings == null || weakenings.isEmpty()) {
            throw new IllegalStateException("No weakening found for axiom.");
        }
        return weakenings.stream()
                .collect(Collectors.toMap(ax -> ax, ax -> powerIndexWeakerAxiom.computeScore(currentAxioms, ax)));
    }

    private OWLAxiom selectWeakening(Map<OWLAxiom, Double> weakerAxiomScores, OWLAxiom badAxiom) {
        return weakerAxiomScores.entrySet().stream()
                .min(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(e -> Utils.prettyPrintAxiomDL(e.getKey())))
                .orElseThrow(() -> new IllegalStateException("Could not select a weakening for axiom: " + badAxiom))
                .getKey();
    }

    private Map<OWLAxiom, Double> computeBadAxiomScores(Set<OWLAxiom> currentAxioms) {
        if (currentAxioms == null || currentAxioms.isEmpty()) {
            throw new IllegalStateException("Could not find a bad axiom in ontology.");
        }
        return currentAxioms.stream()
                .collect(Collectors.toMap(ax -> ax, ax -> powerIndexBadAxiom.computeScore(currentAxioms, ax)));
    }

    private OWLAxiom selectBadAxiom(Map<OWLAxiom, Double> badAxiomScores) {
        return badAxiomScores.entrySet().stream()
                .max(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(entry -> entry.getValue())
                        .thenComparing(entry -> Utils.prettyPrintAxiomDL(entry.getKey())))
                .orElseThrow(() -> new IllegalStateException("Could not find a bad axiom in ontology."))
                .getKey();
    }
}

