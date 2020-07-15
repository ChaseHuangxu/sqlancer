package sqlancer;

import java.util.HashSet;
import java.util.Set;

import sqlancer.gen.ExpressionGenerator;

/**
 * This is the base class of the Ternary Logic Partitioning (TLP) oracles. The core idea of TLP is to partition a given
 * so-called original query to three so-called partitioning queries, each of which computes a partition of the original
 * query's result.
 *
 * @param <E>
 *            the expression type
 */
public abstract class TernaryLogicPartitioningOracleBase<E, S> implements TestOracle {

    protected E predicate;
    protected E negatedPredicate;
    protected E isNullPredicate;

    protected final S state;
    protected final Set<String> errors = new HashSet<>();

    protected TernaryLogicPartitioningOracleBase(S state) {
        this.state = state;
    }

    protected void initializeTernaryPredicateVariants() {
        ExpressionGenerator<E> gen = getGen();
        if (gen == null) {
            throw new IllegalStateException();
        }
        predicate = gen.generatePredicate();
        if (predicate == null) {
            throw new IllegalStateException();
        }
        negatedPredicate = gen.negatePredicate(predicate);
        if (negatedPredicate == null) {
            throw new IllegalStateException();
        }
        isNullPredicate = gen.isNull(predicate);
        if (isNullPredicate == null) {
            throw new IllegalStateException();
        }
    }

    protected abstract ExpressionGenerator<E> getGen();

}
