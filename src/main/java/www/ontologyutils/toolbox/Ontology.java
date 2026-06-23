package www.ontologyutils.toolbox;

import java.io.File;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.profiles.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.util.*;

import openllet.owlapi.OpenlletReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;

/**
 * This class represents an ontology and is used for ontologies in this package.
 * The main utility of having this class instead of using {@code OWLOntology} is
 * the ability to reuse reasoner and owl ontology as much as possible. The class
 * also provides some utility functions for loading and saving ontologies.
 *
 * The ontology object must be closed in order to free all resources associated
 * to the {@code OWLOntology} object and the {@code OWLreasoner}.
 */
public class Ontology implements AutoCloseable {
    private static final OWLOntologyManager defaultManager = OWLManager.createConcurrentOWLOntologyManager();
    private static final OWLReasonerFactory defaultFactory = new JFactFactory();
    /**
     * This is only here for statistics
     */
    public static int reasonerCalls;
    /**
     * Set this to true if you want to add axioms reflecting the original axioms,
     * before replacement.
     */
    public static boolean originAnnotation = false;

    private static class ReasonerCache {
        private OWLReasonerFactory reasonerFactory;
        private Set<Ontology> references;
        private Deque<OWLReasoner> unusedReasoners;
        private Map<OWLReasoner, Ontology> lastOntologies;
        private boolean hardRefresh = false;

        /**
         * Create a new reasoner cache using the given reasoner factory.
         *
         * @param reasonerFactory
         *            The factory to create the reasoner with if necessary.
         */
        public ReasonerCache(OWLReasonerFactory reasonerFactory) {
            this.reasonerFactory = reasonerFactory;
            this.references = new HashSet<>();
            this.unusedReasoners = new ArrayDeque<>();
            this.lastOntologies = new IdentityHashMap<>();
        }

        /**
         * @returns The set of ontologies referencing this cache.
         */
        public synchronized Set<Ontology> getReferences() {
            return Set.copyOf(references);
        }

        /**
         * @param ontology
         *            The ontology using this reasoner.
         */
        public synchronized void addReference(Ontology ontology) {
            references.add(ontology);
        }

        private void actuallyDisposeOwlReasoner(OWLReasoner reasoner) {
            var owlOntology = reasoner.getRootOntology();
            reasoner.dispose();
            owlOntology.getOWLOntologyManager().removeOntology(owlOntology);
        }

        /**
         * @param ontology
         *            The ontology, no longer using the reasoner after this call.
         */
        public synchronized void removeReference(Ontology ontology) {
            references.remove(ontology);
            if (references.isEmpty()) {
                for (var reasoner : unusedReasoners) {
                    actuallyDisposeOwlReasoner(reasoner);
                }
                unusedReasoners.clear();
            }
        }

        /**
         * If an {@code OWLReasoner} was created using {@code getOwlReasoner} it must be
         * disposed again to free associated resources.
         *
         * @param reasoner
         *            The {@code OWLReasoner} to dispose.
         */
        public synchronized void disposeOwlReasoner(OWLReasoner reasoner) {
            unusedReasoners.add(reasoner);
        }

        /**
         * @param ontology
         *            The ontology for which to create the reasoner.
         * @return A new {@code OWLReasoner} created using the factory in this cache.
         */
        public OWLReasoner getNewOwlReasoner(Ontology ontology) {
            try {
                var owlOntology = defaultManager.createOntology();
                owlOntology.addAxioms(ontology.axioms());
                return reasonerFactory.createReasoner(owlOntology);
            } catch (OWLOntologyCreationException e) {
                throw Utils.panic(e);
            }
        }

        /**
         * @param ontology
         *            The ontology for which to create the reasoner.
         * @return A new or reused {@code OWLReasoner} created using the factory in this
         *         cache.
         */
        public OWLReasoner getOwlReasoner(Ontology ontology) {
            OWLReasoner reasoner = null;
            Ontology lastOntology = null;
            synchronized (this) {
                if (!unusedReasoners.isEmpty()) {
                    reasoner = unusedReasoners.pop();
                    lastOntology = lastOntologies.get(reasoner);
                }
            }
            if (reasoner == null) {
                reasoner = getNewOwlReasoner(ontology);
            } else {
                var owlOntology = reasoner.getRootOntology();
                if ((lastOntology != ontology || ontology.changed) && ontology.applyChangesTo(owlOntology)) {
                    if (hardRefresh) {
                        // Some reasoners are not performing the flushing correctly. This is an ugly
                        // workaround. The performance impact of this depends on the reasoner.
                        reasoner.dispose();
                        reasoner = reasonerFactory.createReasoner(owlOntology);
                    } else {
                        try {
                            reasoner.flush();
                        } catch (IllegalArgumentException e) {
                            // HermitT likes to throw this and complain that it can not do incremental
                            // updates.
                            reasoner.dispose();
                            reasoner = reasonerFactory.createReasoner(owlOntology);
                        }
                    }
                }
            }
            synchronized (this) {
                reasonerCalls += 1;
                ontology.changed = false;
                lastOntologies.put(reasoner, ontology);
            }
            return reasoner;
        }

        /**
         * Use the cached reasoner in this object for executing the given action.
         *
         * @param <T>
         *            The return value of the action to be performed.
         * @param ontology
         *            The ontology on which to create the reasoner.
         * @param action
         *            The action to perform with the reasoner.
         * @return The value returned by {@code action}.
         */
        public <T> T withReasonerDo(Ontology ontology, Function<OWLReasoner, T> action) {
            if (Thread.interrupted()) {
                throw new CanceledException();
            }
            var reasoner = getOwlReasoner(ontology);
            try {
                return action.apply(reasoner);
            } catch (ReasonerInternalException ex) {
                // Nothing we can really do here, try again with a new reasoner.
                ex.printStackTrace();
                actuallyDisposeOwlReasoner(reasoner);
                reasoner = null;
                return withReasonerDo(ontology, action);
            } catch (Exception | OutOfMemoryError ex) {
                // Reusing the reasoner after an exception is not a good idea.
                actuallyDisposeOwlReasoner(reasoner);
                reasoner = null;
                throw ex;
            } finally {
                if (reasoner != null) {
                    disposeOwlReasoner(reasoner);
                }
            }
        }
    }

    private Set<OWLAxiom> staticAxioms;
    private Set<OWLAxiom> refutableAxioms;
    private ReasonerCache reasonerCache;
    private boolean changed;

    /**
     * Create a new ontology around the given static and refutable axioms. Should
     * the need arise to create a reasoner, use {@code reasonerFactory} to create
     * it.
     *
     * @param staticAxioms
     *            The set of static (i.e., not to be changed) axioms.
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @param reasonerCache
     *            The reasoner cache to be used for reasoning queries.
     */
    private Ontology(Collection<? extends OWLAxiom> staticAxioms, Collection<? extends OWLAxiom> refutableAxioms,
            ReasonerCache reasonerCache) {
        this.staticAxioms = new HashSet<>(staticAxioms);
        this.refutableAxioms = new HashSet<>(refutableAxioms);
        this.refutableAxioms.removeAll(staticAxioms);
        this.reasonerCache = reasonerCache;
        this.reasonerCache.addReference(this);
        this.changed = false;
    }

    /**
     * @param staticAxioms
     *            The set of static (i.e., not to be changed) axioms.
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology.
     */
    public static Ontology withAxioms(Collection<? extends OWLAxiom> staticAxioms,
            Collection<? extends OWLAxiom> refutableAxioms, OWLReasonerFactory reasonerFactory) {
        return new Ontology(staticAxioms, refutableAxioms, new ReasonerCache(reasonerFactory));
    }

    /**
     * @param staticAxioms
     *            The set of static (i.e., not to be changed) axioms.
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @return The new ontology.
     */
    public static Ontology withAxioms(Collection<? extends OWLAxiom> staticAxioms,
            Collection<? extends OWLAxiom> refutableAxioms) {
        return withAxioms(staticAxioms, refutableAxioms, defaultFactory);
    }

    /**
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology.
     */
    public static Ontology withAxioms(Collection<? extends OWLAxiom> refutableAxioms,
            OWLReasonerFactory reasonerFactory) {
        return withAxioms(Set.of(), refutableAxioms, reasonerFactory);
    }

    /**
     * @param refutableAxioms
     *            The set of refutable (i.e., to be repaired) axioms.
     * @return The new ontology.
     */
    public static Ontology withAxioms(Collection<? extends OWLAxiom> refutableAxioms) {
        return withAxioms(refutableAxioms, defaultFactory);
    }

    /**
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new empty ontology.
     */
    public static Ontology emptyOntology(OWLReasonerFactory reasonerFactory) {
        return withAxioms(Set.of(), reasonerFactory);
    }

    /**
     * @return The new empty ontology.
     */
    public static Ontology emptyOntology() {
        return emptyOntology(defaultFactory);
    }

    /**
     * @param ontology
     *            The {@code OWLOntology} form which to copy the axioms. Logical
     *            axiom will be refutable, other will be static.
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology.
     */
    public static Ontology withAxiomsFrom(OWLOntology ontology, OWLReasonerFactory reasonerFactory) {
        var logicalAxioms = Utils.toSet(ontology.logicalAxioms());
        var otherAxioms = Utils.toSet(ontology.axioms()
                .filter(axiom -> !logicalAxioms.contains(axiom)));
        return withAxioms(otherAxioms, logicalAxioms, reasonerFactory);
    }

    /**
     * @param ontology
     *            The {@code OWLOntology} form which to copy the axioms. Logical
     *            axiom will be refutable, other will be static.
     * @return The new ontology.
     */
    public static Ontology withAxiomsFrom(OWLOntology ontology) {
        return withAxiomsFrom(ontology, defaultFactory);
    }

    /**
     * @param filePath
     *            File containing the ontology.
     * @param reasonerFactory
     *            The reasoner factory to be used for reasoning queries.
     * @return The new ontology, loaded form the file.
     */
    public static synchronized Ontology loadOntology(String filePath, OWLReasonerFactory reasonerFactory) {
        OWLOntology ontology = null;
        try {
            var ontologyFile = new File(filePath);
            ontology = defaultManager.loadOntologyFromOntologyDocument(ontologyFile);
            return withAxiomsFrom(ontology, reasonerFactory);
        } catch (OWLOntologyCreationException e) {
            throw Utils.panic(e);
        } finally {
            if (ontology != null) {
                defaultManager.removeOntology(ontology);
            }
        }
    }

    /**
     * @param filePath
     *            The string to the file containing the ontology.
     * @return The new ontology, loaded form the file.
     */
    public static Ontology loadOntology(String filePath) {
        return loadOntology(filePath, defaultFactory);
    }

    /**
     * @param filePath
     *            The string to the file containing the ontology.
     * @return The new ontology, containing only logical axioms, loaded form the
     *         file.
     */
    public static Ontology loadOnlyLogicalAxioms(String filePath) {
        var ontology = loadOntology(filePath, defaultFactory);
        ontology.removeAxioms(Utils.toList(ontology.nonLogicalAxioms()));
        return ontology;
    }

    /**
     * @param filePath
     *            The string to the file containing the ontology.
     * @return The new ontology, with added origin annotations, loaded form the
     *         file.
     */
    public static Ontology loadOntologyWithOriginAnnotations(String filePath) {
        var ontology = loadOntology(filePath, defaultFactory);
        for (var axiom : Utils.toList(ontology.axioms())) {
            ontology.replaceAxiom(axiom, axiom);
        }
        return ontology;
    }

    /**
     * Save the ontology to the file given by the path {@code filePath}.
     *
     * @param filePath
     *            The file to which the ontology should be saved.
     */
    public void saveOntology(String filePath) {
        this.<Void>withOwlOntologyDo(ontology -> {
            var ontologyFile = new File(filePath);
            try {
                ontology.saveOntology(IRI.create(ontologyFile));
            } catch (OWLOntologyStorageException e) {
                Utils.panic(e);
            }
            return null;
        });
    }

    /**
     * Add/remove axioms form {@code ontology} such that it contains the same axioms
     * as this ontology.
     *
     * @param ontology
     *            The ontology to which we want to add changes.
     * @return true if some change was made to the ontology, false otherwise.
     */
    public boolean applyChangesTo(OWLOntology ontology) {
        var oldAxioms = Utils.toSet(ontology.axioms());
        var toRemove = Utils.toList(
                oldAxioms.stream().filter(axiom -> !refutableAxioms.contains(axiom) && !staticAxioms.contains(axiom)));
        var toAdd = Utils.toList(axioms().filter(axiom -> !oldAxioms.contains(axiom)));
        if (!toRemove.isEmpty()) {
            ontology.removeAxioms(toRemove);
        }
        if (!toAdd.isEmpty()) {
            ontology.addAxioms(toAdd);
        }
        return !toRemove.isEmpty() || !toAdd.isEmpty();
    }

    /**
     * @return The default data factory to use for creating owl api objects.
     */
    public static OWLDataFactory getDefaultDataFactory() {
        return defaultManager.getOWLDataFactory();
    }

    /**
     * @return Stream containing all static axioms of the ontology.
     */
    public Stream<OWLAxiom> staticAxioms() {
        return staticAxioms.stream();
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all static axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> staticAxioms(AxiomType<?>... types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all static axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> staticAxioms(Collection<AxiomType<?>> types) {
        return staticAxioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @return Stream containing all refutable axioms.
     */
    public Stream<OWLAxiom> refutableAxioms() {
        return refutableAxioms.stream();
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all refutable axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> refutableAxioms(AxiomType<?>... types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all refutable axioms of the ontology of the given
     *         types.
     */
    public Stream<OWLAxiom> refutableAxioms(Collection<AxiomType<?>> types) {
        return refutableAxioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @return Stream containing all axioms.
     */
    public Stream<OWLAxiom> axioms() {
        return Stream.concat(staticAxioms(), refutableAxioms());
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all axioms of the ontology of the given types.
     */
    public Stream<OWLAxiom> axioms(AxiomType<?>... types) {
        return axioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @param types
     *            The types of axioms to return.
     * @return Stream containing all axioms of the ontology of the given types.
     */
    public Stream<OWLAxiom> axioms(Collection<AxiomType<?>> types) {
        return axioms().filter(axiom -> axiom.isOfType(types));
    }

    /**
     * @return Stream containing all logical axioms.
     */
    public Stream<OWLLogicalAxiom> logicalAxioms() {
        return axioms().filter(axiom -> axiom.isLogicalAxiom()).map(axiom -> (OWLLogicalAxiom) axiom);
    }

    /**
     * @return Stream containing all non-logical axioms.
     */
    public Stream<OWLAxiom> nonLogicalAxioms() {
        return axioms().filter(axiom -> !axiom.isLogicalAxiom()).map(axiom -> (OWLAxiom) axiom);
    }

    /**
     * @return Stream containing all TBox axioms.
     */
    public Stream<OWLAxiom> tboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.TBoxAxiomTypes));
    }

    /**
     * @return Stream containing all ABox axioms.
     */
    public Stream<OWLAxiom> aboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.ABoxAxiomTypes));
    }

    /**
     * @return Stream containing all RBox axioms.
     */
    public Stream<OWLAxiom> rboxAxioms() {
        return axioms().filter(axiom -> axiom.isOfType(AxiomType.RBoxAxiomTypes));
    }

    /**
     * @param axioms
     *            The axioms to remove.
     */
    public void removeAxioms(Stream<? extends OWLAxiom> axioms) {
        changed = true;
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.remove(axiom);
        });
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addStaticAxioms(Stream<? extends OWLAxiom> axioms) {
        changed = true;
        axioms.forEach(axiom -> {
            refutableAxioms.remove(axiom);
            staticAxioms.add(axiom);
        });
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addAxioms(Stream<? extends OWLAxiom> axioms) {
        changed = true;
        axioms.forEach(axiom -> {
            staticAxioms.remove(axiom);
            refutableAxioms.add(axiom);
        });
    }

    /**
     * @param axioms
     *            The axioms to remove.
     */
    public void removeAxioms(Collection<? extends OWLAxiom> axioms) {
        removeAxioms(axioms.stream());
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addStaticAxioms(Collection<? extends OWLAxiom> axioms) {
        addStaticAxioms(axioms.stream());
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addAxioms(Collection<? extends OWLAxiom> axioms) {
        addAxioms(axioms.stream());
    }

    /**
     * @param axioms
     *            The axioms to remove.
     */
    public void removeAxioms(OWLAxiom... axioms) {
        removeAxioms(Stream.of(axioms));
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addStaticAxioms(OWLAxiom... axioms) {
        addStaticAxioms(Stream.of(axioms));
    }

    /**
     * @param axioms
     *            The axioms to add.
     */
    public void addAxioms(OWLAxiom... axioms) {
        addAxioms(Stream.of(axioms));
    }

    /**
     * @return The {@code OWLAnnotationProperty} used for the origin annotation when
     *         replacing axioms.
     */
    public static OWLAnnotationProperty getOriginAnnotationProperty() {
        return getDefaultDataFactory().getOWLAnnotationProperty("origin");
    }

    /**
     * @param origin
     *            The origin axiom.
     * @return The owl annotation for this origin axiom.
     */
    private static OWLAnnotation getNewOriginAnnotation(OWLAxiom origin) {
        var df = getDefaultDataFactory();
        return df.getOWLAnnotation(getOriginAnnotationProperty(), df.getOWLLiteral(origin.toString()));
    }

    /**
     * @param axiom
     *            The axiom for which to get axioms.
     * @return The annotations of {@code axiom}.
     */
    public static Stream<OWLAnnotation> axiomOriginAnnotations(OWLAxiom axiom) {
        if (axiom.annotations(getOriginAnnotationProperty()).count() > 0) {
            return axiom.annotations(getOriginAnnotationProperty());
        } else {
            return Stream.of(getNewOriginAnnotation(axiom));
        }
    }

    /**
     * Annotate {@code axiom} with the origin annotation indicating that the origin
     * of {@code axiom} is {@code origin}.
     *
     * @param axiom
     *            Axiom to annotate.
     * @param origin
     *            The origin axiom.
     * @return A new annotated axiom equivalent to {@code axiom}.
     */
    public static OWLAxiom getOriginAnnotatedAxiom(OWLAxiom axiom, OWLAxiom origin) {
        if (!originAnnotation || axiom.equals(origin)) {
            return axiom;
        } else {
            return axiom.getAnnotatedAxiom(axiomOriginAnnotations(origin));
        }
    }

    /**
     * Replace a axiom with a collection of other axioms. All new axioms will be
     * annotated with the original axioms as the origin.
     *
     * @param remove
     *            Axiom to remove.
     * @param replacement
     *            The axioms to add.
     */
    public void replaceAxiom(OWLAxiom remove, Stream<? extends OWLAxiom> replacement) {
        var annotated = replacement.map(a -> getOriginAnnotatedAxiom(a, remove));
        boolean isStatic = staticAxioms.contains(remove);
        removeAxioms(remove);
        if (isStatic) {
            addStaticAxioms(annotated);
        } else {
            addAxioms(annotated);
        }
    }

    /**
     * Remove all refutable axioms, and add the axioms from axioms.
     *
     * @param axioms
     *            The axioms to use.
     */
    public void setRefutableAxioms(Collection<? extends OWLAxiom> axioms) {
        changed = true;
        refutableAxioms.clear();
        refutableAxioms.addAll(axioms);
    }

    /**
     * Replace a axiom with a collection of other axioms. All new axioms will be
     * annotated with the original axioms as the origin.
     *
     * @param remove
     *            Axiom to remove.
     * @param replacement
     *            The axioms to add.
     */
    public void replaceAxiom(OWLAxiom remove, Collection<? extends OWLAxiom> replacement) {
        replaceAxiom(remove, replacement.stream());
    }

    /**
     * Replace a axiom with a collection of other axioms. All new axioms will be
     * annotated with the original axioms as the origin.
     *
     * @param remove
     *            Axiom to remove.
     * @param replacement
     *            The axioms to add.
     */
    public void replaceAxiom(OWLAxiom remove, OWLAxiom... replacement) {
        replaceAxiom(remove, Stream.of(replacement));
    }

    /**
     * @param remove
     *            The set of axioms not to include in the result.
     * @return The axioms of the ontology without those in {@code remove}.
     */
    public Set<OWLAxiom> complement(Set<OWLAxiom> remove) {
        return Utils.toSet(axioms().filter(axiom -> !remove.contains(axiom)));
    }

    /**
     * The reasoner created by this call must be disposed of again using the
     * {@code disposeOwlReasoner} method.
     *
     * @return A new reasoner for the ontology.
     */
    public OWLReasoner getOwlReasoner() {
        return reasonerCache.getOwlReasoner(this);
    }

    /**
     * Dispose of a reasoner to release all resources associated with the
     * {@code OWLOntology} and {@code OWLReasoner}.
     *
     * @param reasoner
     *            The reasoner to dispose of.
     */
    public void disposeOwlReasoner(OWLReasoner reasoner) {
        reasonerCache.disposeOwlReasoner(reasoner);
    }

    private <T> T withReasonerDo(Function<OWLReasoner, T> action) {
        return reasonerCache.withReasonerDo(this, action);
    }

    private <T> T withOwlOntologyDo(Function<OWLOntology, T> action) {
        return reasonerCache.withReasonerDo(this, reasoner -> action.apply(reasoner.getRootOntology()));
    }

    /**
     * @return true if the ontology is consistent, false otherwise.
     */
    public boolean isConsistent() {
        return withReasonerDo(reasoner -> reasoner.isConsistent());
    }

    /**
     * @return true if the ontology is coherent, false otherwise.
     */
    public boolean isCoherent() {
        return isConsistent() && withReasonerDo(reasoner -> reasoner.getBottomClassNode().isSingleton());
    }

    /**
     * @param axioms
     *            The axioms to check entailment for.
     * @return true if all of the axioms are entailed, false otherwise.
     */
    public boolean isEntailed(OWLAxiom... axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(axioms));
    }

    /**
     * @param axioms
     *            The axioms to check entailment for.
     * @return true if all of the axioms are entailed, false otherwise.
     */
    public boolean isEntailed(Stream<? extends OWLAxiom> axioms) {
        return withReasonerDo(reasoner -> reasoner.isEntailed(axioms));
    }

    /**
     * @param other
     *            The ontology to test.
     * @return true if {@code other} is entailed by this ontology, false otherwise.
     */
    public boolean isEntailed(Ontology other) {
        return isEntailed(other.logicalAxioms());
    }

    /**
     * @param concept
     *            The concept to test.
     * @return true if the concept is satisfiable.
     */
    public boolean isSatisfiable(OWLClassExpression concept) {
        return withReasonerDo(reasoner -> reasoner.isSatisfiable(concept));
    }

    /**
     * @param concepts
     *            The concepts to test.
     * @return true if the concept is satisfiable.
     */
    public boolean isSatisfiable(OWLClassExpression... concepts) {
        for (var concept : concepts) {
            if (!isSatisfiable(concept)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return A stream with all unsatisfiable atomic concept.
     */
    public Stream<OWLClass> unsatisfiableConcepts() {
        return withReasonerDo(reasoner -> Utils.toList(reasoner.unsatisfiableClasses())).stream();
    }

    /**
     * @param subClass
     *            The possible sub concept.
     * @param superClass
     *            The possible super concept.
     * @return true if the extension of {@code subClass} is a subset of the
     *         extension of {@code superClass} in every model of the ontology, false
     *         otherwise.
     */
    public boolean isSubClass(OWLClassExpression subClass, OWLClassExpression superClass) {
        var df = getDefaultDataFactory();
        return withReasonerDo(reasoner -> reasoner.isEntailed(df.getOWLSubClassOfAxiom(subClass, superClass)));
    }

    /**
     * @param subRole
     *            The possible sub role.
     * @param superRole
     *            The possible super role.
     * @return true if the extension of {@code subRole} is a subset of the
     *         extension of {@code superRole} in every model of the ontology, false
     *         otherwise.
     */
    public boolean isSubRole(OWLObjectPropertyExpression subRole, OWLObjectPropertyExpression superRole) {
        var df = getDefaultDataFactory();
        return withReasonerDo(reasoner -> reasoner.isEntailed(df.getOWLSubObjectPropertyOfAxiom(subRole, superRole)));
    }

    /**
     * @return The list of profile reports for all OWL 2 profiles.
     */
    public List<OWLProfileReport> checkOwlProfiles() {
        return withOwlOntologyDo(ontology -> Utils.toList(
                Arrays.stream(Profiles.values()).map(profile -> profile.checkOntology(ontology))));
    }

    /**
     * @return true if the ontology is in the OWL 2 DL profile.
     */
    public boolean isOwl2Dl() {
        return withOwlOntologyDo(ontology -> Profiles.OWL2_DL.checkOntology(ontology).isInProfile());
    }

    /**
     * @return The list of languages the ontology can be expressed in.
     */
    public List<Languages> checkDlExpressivity() {
        return withOwlOntologyDo(ontology -> Utils
                .toList((new DLExpressivityChecker(List.of(ontology))).expressibleInLanguages().stream()));
    }

    /**
     * @return The steam containing all maximal consistent subsets (including static
     *         axioms) of the ontologies axioms.
     */
    public Stream<Set<OWLAxiom>> maximalConsistentSubsets() {
        return minimalCorrectionSubsets().map(this::complement);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return The stream containing all maximal subsets (including static axioms)
     *         of the ontologies axioms that satisfy {@code isRepaired}.
     */
    public Stream<Set<OWLAxiom>> maximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return minimalCorrectionSubsets(isRepaired).map(this::complement);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return The stream of largest maximal subsets (including static axioms) of
     *         the ontologies axioms that satisfy {@code isRepaired}.
     */
    public Stream<Set<OWLAxiom>> largestMaximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired, true)).stream();
    }

    /**
     * @return A stream of all minimal subsets that when removed from the ontology
     *         yield an optimal classical repair for consistency of the ontology.
     */
    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets() {
        return minimalCorrectionSubsets(Ontology::isConsistent);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of all minimal subsets that when removed from the ontology
     *         yield an optimal classical repair for consistency of the ontology.
     */
    public Stream<Set<OWLAxiom>> minimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.allMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of all minimal subsets that are not repaired.
     */
    public Stream<Set<OWLAxiom>> minimalUnsatisfiableSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.allMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                return !isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return The set containing all maximal subsets (including static axioms)
     *         of the ontologies axioms that satisfy {@code isRepaired}.
     */
    public Set<Set<OWLAxiom>> getMaximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return Utils.toSet(getMinimalCorrectionSubsets(isRepaired).stream().map(this::complement));
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A set of all minimal subsets that when removed from the ontology
     *         yield an optimal classical repair for consistency of the ontology.
     */
    public Set<Set<OWLAxiom>> getMinimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getAllMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A set of all minimal subsets that are not repaired.
     */
    public Set<Set<OWLAxiom>> getMinimalUnsatisfiableSubsets(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getAllMinimalSubsets(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                return !isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of smallest minimal subsets that when removed from the
     *         ontology yield an optimal classical repair for consistency of the
     *         ontology.
     */
    public Stream<Set<OWLAxiom>> smallestMinimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return (new MaximalConsistentSubsets(this, isRepaired, true)).correctionStream();
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A single maximal consistent subset.
     */
    public Set<OWLAxiom> maximalConsistentSubset(Predicate<Ontology> isRepaired) {
        return complement(minimalCorrectionSubset(isRepaired));
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A single minimal correction subset.
     */
    public Set<OWLAxiom> minimalCorrectionSubset(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                return isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A single set with the refutable axioms of a minimal unsatisfiable
     *         subset.
     */
    public Set<OWLAxiom> minimalUnsatisfiableSubset(Predicate<Ontology> isRepaired) {
        return MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms, axioms -> {
            try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                return !isRepaired.test(ontology);
            }
        });
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of some maximal consistent subset.
     */
    public Stream<Set<OWLAxiom>> someMaximalConsistentSubsets(Predicate<Ontology> isRepaired) {
        return someMinimalCorrectionSubsets(isRepaired).map(this::complement);
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of some minimal correction subset.
     */
    public Stream<Set<OWLAxiom>> someMinimalCorrectionSubsets(Predicate<Ontology> isRepaired) {
        return IntStream.range(0, 16)
                .mapToObj(i -> MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms, axioms -> {
                    try (var ontology = new Ontology(staticAxioms, complement(axioms), reasonerCache)) {
                        return isRepaired.test(ontology);
                    }
                })).takeWhile(s -> s != null).distinct();
    }

    /**
     * @param isRepaired
     *            The monotone predicate testing that the ontology is repaired.
     * @return A stream of some set with the refutable axioms of a minimal
     *         unsatisfiable subset.
     */
    public Stream<Set<OWLAxiom>> someMinimalUnsatisfiableSubsets(Predicate<Ontology> isRepaired) {
        return IntStream.range(0, 16)
                .mapToObj(i -> MinimalSubsets.getRandomizedMinimalSubset(refutableAxioms, axioms -> {
                    try (var ontology = new Ontology(staticAxioms, axioms, reasonerCache)) {
                        return !isRepaired.test(ontology);
                    }
                })).takeWhile(s -> s != null).distinct();
    }

    /**
     * @return A stream providing all subconcepts used in the ontology.
     */
    public Stream<OWLClassExpression> subConcepts() {
        var df = getDefaultDataFactory();
        return Stream.concat(axioms().flatMap(OWLAxiom::nestedClassExpressions),
                Stream.of(df.getOWLThing(), df.getOWLNothing())).distinct();
    }

    /**
     * @return A stream containing all non-simple roles.
     */
    public Stream<OWLObjectPropertyExpression> nonSimpleRoles() {
        var df = getDefaultDataFactory();
        return Stream
                .concat(withOwlOntologyDo(ontology -> (new OWLObjectPropertyManager(ontology)).getNonSimpleProperties())
                        .stream(),
                        Stream.of(df.getOWLBottomObjectProperty(), df.getOWLTopObjectProperty()))
                .distinct();
    }

    /**
     * @return A stream containing all roles in the signature and there inverse.
     */
    public Stream<OWLObjectPropertyExpression> subRoles() {
        var df = getDefaultDataFactory();
        return Stream.concat(rolesInSignature().flatMap(r -> Stream.of(r, r.getInverseProperty())),
                Stream.of(df.getOWLBottomObjectProperty(), df.getOWLTopObjectProperty())).distinct();
    }

    /**
     * @return A stream containing all simple roles.
     */
    public Stream<OWLObjectPropertyExpression> simpleRoles() {
        var nonSimple = Utils.toSet(nonSimpleRoles());
        return subRoles().filter(role -> !nonSimple.contains(role));
    }

    /**
     * @return A pre-order for this ontology's role hierarchy or null if one does
     *         not exist. Note that the generated preorder is maximal with respect
     *         to the constraints.
     */
    public PreorderCache<OWLObjectProperty> regularPreorder() {
        var preorder = new PreorderCache<OWLObjectProperty>();
        preorder.setupDomain(Utils.toList(rolesInSignature()));
        for (var axiom : (Iterable<OWLAxiom>) axioms()::iterator) {
            if (axiom instanceof OWLSubObjectPropertyOfAxiom ax) {
                var sub = ax.getSubProperty().getNamedProperty();
                var sup = ax.getSuperProperty().getNamedProperty();
                if (!preorder.assertSuccessor(sub, sup)) {
                    return null;
                }
            } else if (axiom instanceof OWLSubPropertyChainOfAxiom ax) {
                var subs = ax.getPropertyChain();
                var sup = ax.getSuperProperty();
                List<OWLObjectPropertyExpression> preds;
                if (subs.size() == 2 && subs.get(0).equals(sup) && subs.get(1).equals(sup)) {
                    preds = List.of();
                } else if (subs.get(0).equals(sup)) {
                    preds = subs.subList(1, subs.size());
                } else if (subs.get(subs.size() - 1).equals(sup)) {
                    preds = subs.subList(0, subs.size() - 1);
                } else {
                    preds = subs;
                }
                for (var pred : preds) {
                    var subName = pred.getNamedProperty();
                    var supName = sup.getNamedProperty();
                    if (!preorder.assertSuccessor(subName, supName)) {
                        return null;
                    } else if (!preorder.denySuccessor(supName, subName)) {
                        return null;
                    }
                }
            }
        }
        return preorder;
    }

    /**
     * @return A stream providing all subconcepts used in the ontology's TBox.
     */
    public Stream<OWLClassExpression> subConceptsOfTbox() {
        var df = getDefaultDataFactory();
        return Stream.concat(tboxAxioms().flatMap(OWLAxiom::nestedClassExpressions),
                Stream.of(df.getOWLThing(), df.getOWLNothing())).distinct();
    }

    /**
     * @return A str eam containing all entities in the signature of this ontology.
     */
    public Stream<OWLEntity> signature() {
        return axioms().flatMap(OWLAxiom::signature).distinct();
    }

    /**
     * @return A stream containing all concept names in the signature of this
     *         ontology.
     */
    public Stream<OWLClass> conceptsInSignature() {
        return axioms().flatMap(OWLAxiom::classesInSignature).distinct();
    }

    /**
     * @return A stream containing all roles in the signature of this ontology.
     */
    public Stream<OWLObjectProperty> rolesInSignature() {
        return axioms().flatMap(OWLAxiom::objectPropertiesInSignature).distinct();
    }

    /**
     * @param concepts
     *            The concepts over which to build the axioms
     * @return The stream of C1 subclass C2 axioms, entailed by this ontology.
     */
    public Stream<OWLSubClassOfAxiom> inferredSubClassAxiomsOver(Set<OWLClassExpression> concepts) {
        var df = getDefaultDataFactory();
        var cache = new SubClassCache(concepts);
        return concepts.stream().flatMap(subClass -> concepts.stream()
                .filter(superClass -> cache.computeIfAbsent(subClass, superClass, this::isSubClass))
                .map(superClass -> df.getOWLSubClassOfAxiom(subClass, superClass)));
    }

    /**
     * @param roles
     *            The roles over which to build the axioms
     * @return The stream of r1 subrole r2 axioms, entailed by this ontology.
     */
    public Stream<OWLSubObjectPropertyOfAxiom> inferredSubRoleAxiomsOver(Set<OWLObjectPropertyExpression> roles) {
        var df = getDefaultDataFactory();
        var cache = new SubRoleCache(roles);
        return roles.stream().flatMap(subRole -> roles.stream()
                .filter(superRole -> cache.computeIfAbsent(subRole, superRole, this::isSubRole))
                .map(superRole -> df.getOWLSubObjectPropertyOfAxiom(subRole, superRole)));
    }

    /**
     * @return The stream of C1 subclass C2 axioms, C1 and C2 classes in the
     *         signature of this ontology, entailed by this ontology.
     */
    public Stream<OWLSubClassOfAxiom> inferredTaxonomyAxioms() {
        return inferredSubClassAxiomsOver(Utils.toSet(conceptsInSignature()));
    }

    /**
     * @param <T>
     *            The set element type.
     * @param a
     *            The first set.
     * @param b
     *            The second set.
     * @return double between 0 and 1. &gt; 0.5 if {@code a} contains more
     *         information. &lt; 0.5 if {@code b} contains more information.
     */
    public static <T> double relativeInformationContent(Set<T> a, Set<T> b) {
        var onlyA = a.stream().filter(ax -> !b.contains(ax)).count();
        var onlyB = b.stream().filter(ax -> !a.contains(ax)).count();
        if (onlyB == 0 && onlyA == 0) {
            return 0.5;
        } else {
            return ((double) onlyA) / ((double) onlyA + (double) onlyB);
        }
    }

    /**
     * @param other
     *            The other ontology.
     * @return The inferable information content of this ontology with respect to
     *         the other ontology.
     */
    public double iicWithRespectTo(Ontology other) {
        return relativeInformationContent(Utils.toSet(inferredTaxonomyAxioms()),
                Utils.toSet(other.inferredTaxonomyAxioms()));
    }

    /**
     * Some reasoners do not work if some declarations are missing. This will
     * generate all missing declarations and add them as static axioms.
     */
    public void generateDeclarationAxioms() {
        changed = true;
        var df = getDefaultDataFactory();
        for (var entity : Utils.toList(signature())) {
            if (entity.isOWLClass() || entity.isOWLObjectProperty() || entity.isOWLNamedIndividual()) {
                var newAxiom = df.getOWLDeclarationAxiom(entity);
                if (!staticAxioms.contains(newAxiom) && !refutableAxioms.contains(newAxiom)) {
                    staticAxioms.add(newAxiom);
                }
            }
        }
    }

    /**
     * Remove all declaration axioms that are otherwise not in the signature.
     */
    public void cleanUnnecessaryDeclarationAxioms() {
        var needed = Utils.toSet(logicalAxioms().flatMap(ax -> ax.signature()));
        for (var ax : Utils.toList(axioms(AxiomType.DECLARATION))) {
            if (ax instanceof OWLDeclarationAxiom axiom) {
                if (!needed.contains(axiom.getEntity())) {
                    removeAxioms(ax);
                }
            }
        }
    }

    /**
     * Clone this ontology, but give it a cache using the HermiT reasoner.
     *
     * @return The new ontology.
     */
    public Ontology cloneWithHermit() {
        var newReasonerCache = new ReasonerCache(new ReasonerFactory());
        return new Ontology(staticAxioms, refutableAxioms, newReasonerCache);
    }

    /**
     * Clone this ontology, but give it a cache using the Openllet reasoner.
     *
     * @return The new ontology.
     */
    public Ontology cloneWithOpenllet() {
        var newReasonerCache = new ReasonerCache(OpenlletReasonerFactory.getInstance());
        return new Ontology(staticAxioms, refutableAxioms, newReasonerCache);
    }

    /**
     * Clone this ontology, but give it a cache using the JFact reasoner.
     *
     * @return The new ontology.
     */
    public Ontology cloneWithJFact() {
        var newReasonerCache = new ReasonerCache(new JFactFactory());
        return new Ontology(staticAxioms, refutableAxioms, newReasonerCache);
    }

    /**
     * Clone this ontology, but only axioms in {@code axioms}.
     *
     * @param axioms
     *            The axioms that should be retained.
     * @return The new ontology.
     */
    public Ontology cloneWith(Set<? extends OWLAxiom> axioms) {
        return new Ontology(Utils.toList(staticAxioms.stream().filter(axiom -> axioms.contains(axiom))),
                Utils.toList(refutableAxioms.stream().filter(axiom -> axioms.contains(axiom))), reasonerCache);
    }

    /**
     * Clone this ontology, but only static axioms and those axioms in
     * {@code axioms}.
     *
     * @param axioms
     *            The axioms that should be retained.
     * @return The new ontology.
     */
    public Ontology cloneWithRefutable(Set<? extends OWLAxiom> axioms) {
        return new Ontology(staticAxioms,
                Utils.toList(refutableAxioms.stream().filter(axiom -> axioms.contains(axiom))), reasonerCache);
    }

    /**
     * Clone this ontology, but only static axioms.
     *
     * @return The new ontology.
     */
    public Ontology cloneOnlyStatic() {
        return new Ontology(staticAxioms, Set.of(), reasonerCache);
    }

    /**
     * Clone this ontology, but give it a separate reasoner.
     *
     * @return The new ontology.
     */
    public Ontology cloneWithSeparateCache() {
        return new Ontology(staticAxioms, refutableAxioms, new ReasonerCache(reasonerCache.reasonerFactory));
    }

    /**
     * Close this ontology and create one with a separate cache.
     *
     * @return The new ontology.
     */
    public Ontology withSeparateCache() {
        try (var current = this) {
            return this.cloneWithSeparateCache();
        }
    }

    /**
     * Close this ontology, and return a new one using the HermiT reasoner.
     *
     * @return The new ontology.
     */
    public Ontology withHermit() {
        try (var current = this) {
            return this.cloneWithHermit();
        }
    }

    /**
     * Close this ontology, and return a new one using the Openllet reasoner.
     *
     * @return The new ontology.
     */
    public Ontology withOpenllet() {
        try (var current = this) {
            return this.cloneWithOpenllet();
        }
    }

    /**
     * Close this ontology, and return a new one using the JFact reasoner.
     *
     * @return The new ontology.
     */
    public Ontology withJFact() {
        try (var current = this) {
            return this.cloneWithJFact();
        }
    }

    @Override
    public Ontology clone() {
        return new Ontology(staticAxioms, refutableAxioms, reasonerCache);
    }

    @Override
    public void close() {
        if (reasonerCache != null) {
            reasonerCache.removeReference(this);
            reasonerCache = null;
        }
    }

    /**
     * Close this ontology, and all ontologies that share a cache with this
     * ontology.
     */
    public void closeAll() {
        if (reasonerCache != null) {
            synchronized (reasonerCache) {
                for (var onto : reasonerCache.getReferences()) {
                    onto.close();
                }
            }
            reasonerCache = null;
        }
    }
}
