package www.ontologyutils.toolbox;

import java.util.*;
import java.util.stream.*;

import org.semanticweb.owlapi.model.*;

/**
 * Utility class that contains static constants and methods that don't neatly
 * fit anywhere else.
 */
public final class Utils {
    private static final ThreadLocal<Random> random = new ThreadLocal<>() {
        protected Random initialValue() {
            return new Random();
        };
    };

    /**
     * Prevents instantiation.
     */
    private Utils() {
    }

    /**
     * Used for exceptions that we don't want to or can't handle. This function is
     * guaranteed to never return.
     *
     * @param e
     *            The exception that caused the panic.
     * @return Return a runtime exception it is given, so that it can be thrown
     *         again to avoid control flow checks failing.
     */
    public static RuntimeException panic(Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
    }

    /**
     * Set the seed for the currently used random instance to {@code seed}.
     *
     * @param seed
     *            The value with which to seed the random number generator.
     */
    public static void randomSeed(long seed) {
        random.get().setSeed(seed);
    }

    /**
     * Select a random from a finite stream uniformly at random.
     *
     * @param <T>
     *            The element type of the stream.
     * @param stream
     *            The stream containing the elements to select from.
     * @return A random element in {@code stream}.
     */
    public static <T> T randomChoice(Stream<? extends T> stream) {
        var flatList = Utils.toList(stream);
        int randomIdx = random.get().nextInt(flatList.size());
        return flatList.get(randomIdx);
    }

    /**
     * Select a random element for the collection uniformly ar random.
     *
     * @param <T>
     *            The element type of the collection.
     * @param collection
     *            The collection from which to select a random element.
     * @return A random element in {@code collection}.
     */
    public static <T> T randomChoice(Collection<? extends T> collection) {
        return randomChoice(collection.stream());
    }

    /**
     * Return a random order from the finite stream.
     *
     * @param <T>
     *            The element type of the stream.
     * @param stream
     *            The stream we want to reorder.
     * @return A random order of {@code stream}.
     */
    public static <T> List<T> randomOrder(Stream<? extends T> stream) {
        var flatList = Utils.toList(stream.map(t -> (T) t));
        Collections.shuffle(flatList, random.get());
        return flatList;
    }

    /**
     * Return a random order if the collection.
     *
     * @param <T>
     *            The element type of the collection.
     * @param collection
     *            The collection we want to reorder.
     * @return A random order of {@code collection}.
     */
    public static <T> List<T> randomOrder(Collection<? extends T> collection) {
        return randomOrder(collection.stream());
    }

    /**
     * @param <T>
     *            The element type of the list.
     * @param list
     *            The list we want to manipulate.
     * @param idx
     *            The index to change.
     * @param value
     *            The new value to place at the index.
     * @return A stream that contains all elements in {@code list} but the one at
     *         {@code idx} which is replace by {@code value}.
     */
    public static <T> List<T> replaceInList(List<T> list, int idx, T value) {
        return toList(IntStream.range(0, list.size()).mapToObj(j -> j)
                .map(j -> idx == j ? value : list.get(j)));
    }

    /**
     * @param <T>
     *            The element type of the list.
     * @param list
     *            Tne list we want to manipulate.
     * @param idx
     *            The index of the element to remove.
     * @return A stream that contains all elements in {@code list} except the one at
     *         {@code idx}.
     */
    public static <T> Stream<T> removeFromList(List<T> list, int idx) {
        return IntStream.range(0, list.size()).filter(j -> idx != j).mapToObj(list::get);
    }

    /**
     * Compute the power set of the given collection. The implementation here is
     * only able to handle up to 63 elements in {@code set}.
     *
     * @param <T>
     *            The element type of the list.
     * @param set
     *            The set for which to compute the power set.
     * @return The power set of {@code set}
     * @throws IllegalArgumentException
     *             If {@code set} contains more than 63
     *             elements.
     */
    public static <T> Stream<Set<T>> powerSet(Collection<? extends T> set) throws IllegalArgumentException {
        if (set.size() > 63) {
            throw new IllegalArgumentException("Set is too large to compute the power set.");
        }
        var flatList = List.copyOf(set);
        int resultLength = 1 << flatList.size();
        return IntStream.range(0, resultLength).mapToObj(binarySet -> {
            var subset = new HashSet<T>();
            for (int bit = 0; bit < flatList.size(); bit++) {
                if (((binarySet >> bit) & 1) != 0) {
                    subset.add(flatList.get(bit));
                }
            }
            return subset;
        });
    }

    /**
     * E.g., C1 = exists p. (A or B) is the same as C2 = exists p. (A or B), even if
     * the representing objects are different, that is they are the same even if
     * C1 != C2 or !C1.equals(C2). On the other hand, we say that A and B is the
     * same as B and A.
     *
     * @param c1
     *            The first concept.
     * @param c2
     *            The second concept.
     * @return true when {@code c1} and {@code c2} are the same concept at the
     *         syntactic level.
     */
    public static boolean sameConcept(OWLClassExpression c1, OWLClassExpression c2) {
        if (c1.equals(c2)) {
            return true;
        } else if (c1.getClassExpressionType() != c2.getClassExpressionType()) {
            return false;
        } else {
            switch (c1.getClassExpressionType()) {
                case OWL_CLASS: {
                    return c1.equals(c2);
                }
                case OBJECT_COMPLEMENT_OF: {
                    var n1 = (OWLObjectComplementOf) c1;
                    var n2 = (OWLObjectComplementOf) c2;
                    return sameConcept(n1.getOperand(), n2.getOperand());
                }
                case OBJECT_UNION_OF:
                case OBJECT_INTERSECTION_OF: {
                    var u1 = (OWLNaryBooleanClassExpression) c1;
                    var u2 = (OWLNaryBooleanClassExpression) c2;
                    return u1.operands().allMatch(d1 -> u2.operands().anyMatch(d2 -> sameConcept(d1, d2)));
                }
                case OBJECT_MIN_CARDINALITY:
                case OBJECT_MAX_CARDINALITY:
                case OBJECT_EXACT_CARDINALITY:
                case OBJECT_ALL_VALUES_FROM:
                case OBJECT_SOME_VALUES_FROM: {
                    var q1 = (OWLQuantifiedObjectRestriction) c1;
                    var q2 = (OWLQuantifiedObjectRestriction) c2;
                    return q1.getProperty().equals(q2.getProperty()) && sameConcept(q1.getFiller(), q2.getFiller());
                }
                default:
                    throw new IllegalArgumentException(
                            "Concept type " + c1.getClassExpressionType() + " not supported for comparison.");
            }
        }
    }

    /**
     * @param owlString
     *            The string we want to clean up.
     * @return A cleaned up version of {@code owlString}.
     */
    public static String pretty(String owlString) {
        return owlString.replaceAll("<http.*?#", "").replaceAll(">", "").replaceAll("<", "");
    }

    /**
     * @param ax
     *            The axiom to convert.
     * @return a pretty string representing {@code ax}, without its annotations and
     *         without namespaces.
     */
    public static String prettyPrintAxiom(OWLAxiom ax) {
        return ax.getAxiomWithoutAnnotations().toString()
                .replaceAll("<http.*?#", "").replaceAll(">", "").replaceAll("<", "")
                .replaceFirst("Annotation(.*?) ", "");
    }

    public static String prettyPrintAxiomDL(OWLAxiom ax) {
        var rendered = AlcPrinter.print(ax.getAxiomWithoutAnnotations());
        if (rendered == null || rendered.isBlank()) {
            return ax.getAxiomWithoutAnnotations().toString()
                    .replaceAll("<http.*?#", "").replaceAll(">", "").replaceAll("<", "")
                    .replaceFirst("Annotation(.*?) ", "");
        }
        return rendered;
    }

    /**
     * @param axioms
     *            The set of axioms we want to test consistency for.
     * @return True iff {@code axioms} are consistent together.
     */
    public static boolean isConsistent(Collection<? extends OWLAxiom> axioms) {
        try (var ontology = Ontology.withAxioms(axioms)) {
            return ontology.isConsistent();
        }
    }

    /**
     * Utility method that creates an array with the appropriate type.
     *
     * @param <T>
     *            The element type of the collection.
     * @param collection
     *            The collection to convert.
     * @return The array with the elements of the collection.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Collection<T> collection) {
        return (T[]) collection.toArray();
    }

    /**
     * @param <T>
     *            The element type of the stream.
     * @param stream
     *            The stream to convert.
     * @return The list with the elements of the stream.
     */
    public static <T> List<T> toList(Stream<? extends T> stream) {
        return stream.collect(Collectors.toList());
    }

    /**
     * @param <T>
     *            The element type of the stream.
     * @param stream
     *            The stream to convert.
     * @return The set with the elements of the stream.
     */
    public static <T> Set<T> toSet(Stream<? extends T> stream) {
        return stream.collect(Collectors.toSet());
    }

    /**
     * Formats a table of axiom scores for display, sorting by score.
     *
     * @param title
     *            The title to display above the table.
     * @param entries
     *            A stream of axiom-score pairs to display.
     * @param ascending
     *            If true, sort scores in ascending order; otherwise descending.
     * @return A formatted string representation of the table.
     */
    public static String formatPowerIndexTable(String title, Stream<Map.Entry<OWLAxiom, Double>> entries,
                                               boolean ascending) {
        var sb = new StringBuilder();
        sb.append("\n").append(title).append("\n");
        sb.append(String.format("%-12s | %s%n", "Value", "Axiom"));
        sb.append(String.format("%s%n", "-------------|----------------------------------------------"));

        entries.sorted((e1, e2) -> {
            var cmp = Double.compare(e1.getValue(), e2.getValue());
            if (!ascending) {
                cmp = -cmp;
            }
            if (cmp != 0) {
                return cmp;
            }
            var axiomCmp = prettyPrintAxiomDL(e1.getKey()).compareTo(prettyPrintAxiomDL(e2.getKey()));
            return ascending ? axiomCmp : -axiomCmp;
        }).forEach(e -> sb.append(String.format("%-12.6f | %s%n", e.getValue(), prettyPrintAxiomDL(e.getKey()))));
        return sb.toString();
    }

    /**
     * Formats the current state of an ontology for display.
     *
     * @param title
     *            The title to display above the state.
     * @param ontology
     *            The ontology to format.
     * @return A formatted string representation of the ontology state.
     */
    public static String formatOntologyState(String title, Ontology ontology) {
        var sb = new StringBuilder();
        sb.append("\n").append(title).append("\n");
        sb.append(formatAxiomSet("Refutable axioms:", ontology.refutableAxioms().collect(Collectors.toSet())));
        return sb.toString();
    }

    /**
     * Formats a set of axioms for display.
     *
     * @param title
     *            The title to display above the axiom set.
     * @param axioms
     *            The set of axioms to format.
     * @return A formatted string representation of the axiom set.
     */
    public static String formatAxiomSet(String title, Set<OWLAxiom> axioms) {
        var sb = new StringBuilder();
        var printableAxioms = axioms.stream()
                .map(Utils::prettyPrintAxiomDL)
                .filter(rendered -> !rendered.isBlank())
                .sorted()
                .toList();

        sb.append(title).append("\n");
        sb.append("Total axioms: ").append(printableAxioms.size()).append("\n");
        printableAxioms.forEach(axiom -> sb.append("  ").append(axiom).append("\n"));
        return sb.toString();
    }
}
