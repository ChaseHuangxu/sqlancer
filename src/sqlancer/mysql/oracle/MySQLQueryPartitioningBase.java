package sqlancer.mysql.oracle;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.TernaryLogicPartitioningOracleBase;
import sqlancer.TestOracle;
import sqlancer.gen.ExpressionGenerator;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLSchema.MySQLTables;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

public abstract class MySQLQueryPartitioningBase
        extends TernaryLogicPartitioningOracleBase<MySQLExpression, MySQLGlobalState> implements TestOracle {

    MySQLSchema s;
    MySQLTables targetTables;
    MySQLExpressionGenerator gen;
    MySQLSelect select;

    public MySQLQueryPartitioningBase(MySQLGlobalState state) {
        super(state);
        MySQLErrors.addExpressionErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        gen = new MySQLExpressionGenerator(state).setColumns(targetTables.getColumns());
        initializeTernaryPredicateVariants();
        select = new MySQLSelect();
        select.setFetchColumns(generateFetchColumns());
        List<MySQLTable> tables = targetTables.getTables();
        List<MySQLExpression> tableList = tables.stream().map(t -> new MySQLTableReference(t))
                .collect(Collectors.toList());
        // List<MySQLExpression> joins = MySQLJoin.getJoins(tableList, state);
        select.setFromList(tableList);
        select.setWhereClause(null);
        // select.setJoins(joins);
    }

    List<MySQLExpression> generateFetchColumns() {
        return Arrays.asList(MySQLColumnReference.create(targetTables.getColumns().get(0), null));
    }

    MySQLExpression generatePredicate() {
        return gen.generateExpression();
    }

    @Override
    protected ExpressionGenerator<MySQLExpression> getGen() {
        return gen;
    }

}
