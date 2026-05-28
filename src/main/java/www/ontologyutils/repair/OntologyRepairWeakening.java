package www.ontologyutils.repair;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.strategy.ref.RefOntologySelector;
import www.ontologyutils.repair.strategy.bad.BadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.WeakerAxiomSelector;
import www.ontologyutils.toolbox.*;

/**
 * An implementation of {@code OntologyRepair} following closely (but not
 * strictly) the axiom weakening approach described in Nicolas Troquard, Roberto
 * Confalonieri, Pietro Galliani, Rafael Peñaloza, Daniele Porello, Oliver Kutz:
 * "Repairing Ontologies via Axiom Weakening", AAAI 2018.
 *
 * The ontology passed in parameter of {@code repair} should only contain
 * assertion or subclass axioms.
 */
public class OntologyRepairWeakening extends OntologyRepairRemoval {
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

    private RefOntologyStrategy refOntologySource;
    protected int weakeningFlags;
    /**
     * If true, retain all axioms in the reference ontology unchanged in the
     * generated repairs. That is, take a reference ontology and enhance it by
     * adding more data using axiom weakening.
     */
    protected boolean enhanceRef;
    // Strategy selectors (can be provided explicitly or constructed from enums)
    protected final RefOntologySelector refSelector;
    protected final BadAxiomSelector badSelector;
    protected final WeakerAxiomSelector weakerSelector;

    /**
     * Backwards-compatible constructor accepting enums. It constructs selector
     * implementations and delegates to the selector-based constructor.
     */
    public OntologyRepairWeakening(Predicate<Ontology> isRepaired, RefOntologyStrategy refOntologySource,
            BadAxiomStrategy badAxiomSource, int weakeningFlags, boolean enhanceRef) {
        super(isRepaired, badAxiomSource);
        this.refOntologySource = refOntologySource;
        // construct selectors from enums
        this.refSelector = new www.ontologyutils.repair.strategy.ref.McsRefOntologySelector(refOntologySource);
        this.badSelector = new BadAxiomSelector() {
            @Override
            public java.util.Optional<org.semanticweb.owlapi.model.OWLAxiom> selectBest(Ontology ontology, java.util.function.Predicate<Ontology> isRepaired) {
                var list = Utils.toList(findBadAxioms(ontology));
                if (list.isEmpty()) return java.util.Optional.empty();
                return java.util.Optional.of(Utils.randomChoice(list));
            }
        };
        this.weakerSelector = new www.ontologyutils.repair.strategy.weaker.RandomWeakerAxiomSelector();
        this.weakeningFlags = weakeningFlags;
        this.enhanceRef = enhanceRef;
    }

    /**
     * Primary constructor accepting explicit selector strategies.
     */
    public OntologyRepairWeakening(Predicate<Ontology> isRepaired,
            RefOntologySelector refSelector,
            BadAxiomSelector badSelector,
            WeakerAxiomSelector weakerSelector,
            int weakeningFlags,
            boolean enhanceRef) {
        super(isRepaired, null);
        this.refOntologySource = RefOntologyStrategy.ONE_MCS;
        this.refSelector = refSelector;
        this.badSelector = badSelector;
        this.weakerSelector = weakerSelector;
        this.weakeningFlags = weakeningFlags;
        this.enhanceRef = enhanceRef;
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing whether an ontology is repaired.
     */
    public OntologyRepairWeakening(Predicate<Ontology> isRepaired) {
        this(isRepaired, RefOntologyStrategy.ONE_MCS, BadAxiomStrategy.IN_SOME_MUS, AxiomWeakener.FLAG_DEFAULT, true);
    }

    /**
     * @return An instance of {@code OntologyRepairWeakening} that tries to make the
     *         ontology consistent.
     */
    public static OntologyRepair forConsistency() {
        return new OntologyRepairWeakening(Ontology::isConsistent);
    }

    /**
     * @return An instance of {@code OntologyRepairWeakening} that tries to make the
     *         ontology coherent.
     */
    public static OntologyRepair forCoherence() {
        return new OntologyRepairWeakening(Ontology::isCoherent);
    }

    /**
     * @param axioms
     *            The axioms that must not be entailed by the repaired ontology.
     * @return An instance of {@code OntologyRepairWeakening} that tries to remove
     *         all {@code axioms} from being entailed by the ontology.
     */
    public static OntologyRepair forRemovingEntailments(Collection<? extends OWLAxiom> axioms) {
        return new OntologyRepairWeakening(o -> axioms.stream().allMatch(axiom -> !o.isEntailed(axiom)));
    }

    /**
     * @param concept
     *            The concept that must be satisfiable in the repaired ontology.
     * @return An instance of {@code OntologyRepairWeakening} that tries to make
     *         {@code concept} satisfiable.
     */
    public static OntologyRepair forConceptSatisfiability(OWLClassExpression concept) {
        return new OntologyRepairWeakening(o -> o.isSatisfiable(concept));
    }

    /**
     * @param refOntology
     *            The reference ontology.
     * @param fullOntology
     *            The full ontology.
     * @return Weakener using the configuration of this repair.
     */
    public AxiomWeakener getWeakener(Ontology refOntology, Ontology fullOntology) {
        return new AxiomWeakener(refOntology, fullOntology, weakeningFlags);
    }

    /**
     * @param ontology
     *            The ontology to find a reference ontology for.
     * @return The set of axioms to include in the reference ontology to use for
     *         repairs.
     */
    public Stream<Set<OWLAxiom>> getRefAxioms(Ontology ontology) {
        if (refSelector == null) return Stream.of();
        return refSelector.select(ontology, isRepaired);
    }

    @Override
    public void repair(Ontology ontology) {
        var refAxioms = Utils.randomChoice(getRefAxioms(ontology));
        infoMessage("Selected a reference ontology with " + refAxioms.size() + " axioms.");
        if (enhanceRef) {
            ontology.addStaticAxioms(refAxioms);
        }
        try (var refOntology = ontology.cloneWithRefutable(refAxioms).withSeparateCache()) {
            var axiomWeakener = getWeakener(refOntology, ontology);
            while (!isRepaired(ontology)) {
                var maybeBad = badSelector.selectBest(ontology, isRepaired);
                if (maybeBad.isEmpty()) {
                    throw new IllegalStateException("Could not find a bad axiom in ontology.");
                }
                var badAxiom = maybeBad.get();
                infoMessage("Selected the bad axiom " + Utils.prettyPrintAxiom(badAxiom) + ".");
                var maybeWeaker = weakerSelector.selectBest(badAxiom, axiomWeakener, ontology.refutableAxioms().collect(Collectors.toSet()));
                if (maybeWeaker.isEmpty()) {
                    throw new IllegalStateException("Could not find a weakening for axiom: " + Utils.prettyPrintAxiomDL(badAxiom));
                }
                var weakerAxiom = maybeWeaker.get();
                infoMessage("Selected the weaker axiom " + Utils.prettyPrintAxiom(weakerAxiom) + ".");
                ontology.replaceAxiom(badAxiom, weakerAxiom);
            }
        }
    }

    @Override
    public Stream<Ontology> multiple(Ontology ontology) {
        // Optimized version that reuses the cached reasoners and axiom weakeners.
        var weakeners = new HashMap<Set<OWLAxiom>, AxiomWeakener>();
        var refOntologyBase = ontology.cloneWithSeparateCache();
        return Stream.generate(() -> {
            try {
                var refAxioms = Utils.randomChoice(getRefAxioms(ontology));
                AxiomWeakener axiomWeakener;
                synchronized (weakeners) {
                    axiomWeakener = weakeners.computeIfAbsent(refAxioms,
                            ax -> getWeakener(refOntologyBase.cloneWithRefutable(ax), ontology));
                }
                var copy = ontology.clone();
                if (enhanceRef) {
                    copy.addStaticAxioms(refAxioms);
                }
                while (!isRepaired(copy)) {
                    var maybeBad = badSelector.selectBest(copy, isRepaired);
                    if (maybeBad.isEmpty()) {
                        throw new IllegalStateException("Could not find a bad axiom in ontology.");
                    }
                    var badAxiom = maybeBad.get();
                    infoMessage("Selected the bad axiom " + Utils.prettyPrintAxiom(badAxiom) + ".");
                    var maybeWeaker = weakerSelector.selectBest(badAxiom, axiomWeakener, copy.refutableAxioms().collect(Collectors.toSet()));
                    if (maybeWeaker.isEmpty()) {
                        throw new IllegalStateException("Could not find a weakening for axiom: " + Utils.prettyPrintAxiomDL(badAxiom));
                    }
                    var weakerAxiom = maybeWeaker.get();
                    infoMessage("Selected the weaker axiom " + Utils.prettyPrintAxiom(weakerAxiom) + ".");
                    copy.replaceAxiom(badAxiom, weakerAxiom);
                 }
                infoMessage("Found repair.");
                return copy;
            } catch (OutOfMemoryError e) {
                weakeners.clear();
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(onto -> onto != null).onClose(() -> {
            refOntologyBase.closeAll();
        });
    }
}
