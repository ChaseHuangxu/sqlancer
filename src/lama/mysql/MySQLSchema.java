package lama.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lama.Main.StateToReproduce;
import lama.Randomly;
import lama.mysql.ast.MySQLConstant;
import lama.sqlite3.schema.SQLite3Schema.Column;

public class MySQLSchema {

	public static enum MySQLDataType {
		INT;
	}

	public static class MySQLColumn implements Comparable<MySQLColumn> {

		private final String name;
		private final MySQLDataType columnType;
		private final boolean isPrimaryKey;
		private MySQLTable table;

		public enum CollateSequence {
			NOCASE, RTRIM, BINARY;

			public static CollateSequence random() {
				return Randomly.fromOptions(values());
			}
		}

		public MySQLColumn(String name, MySQLDataType columnType, boolean isPrimaryKey) {
			this.name = name;
			this.columnType = columnType;
			this.isPrimaryKey = isPrimaryKey;
		}

		@Override
		public String toString() {
			return String.format("%s.%s: %s", table.getName(), name, columnType);
		}

		@Override
		public int hashCode() {
			return name.hashCode() + 11 * columnType.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Column)) {
				return false;
			} else {
				MySQLColumn c = (MySQLColumn) obj;
				return table.getName().contentEquals(getName()) && name.equals(c.name);
			}
		}

		public String getName() {
			return name;
		}

		public String getFullQualifiedName() {
			return table.getName() + "." + getName();
		}

		public MySQLDataType getColumnType() {
			return columnType;
		}

		public boolean isPrimaryKey() {
			return isPrimaryKey;
		}

		public void setTable(MySQLTable t) {
			this.table = t;
		}

		public MySQLTable getTable() {
			return table;
		}

		@Override
		public int compareTo(MySQLColumn o) {
			if (o.getTable().equals(this.getTable())) {
				return name.compareTo(o.getName());
			} else {
				return o.getTable().compareTo(table);
			}
		}

	}

	
	public static class MySQLTables {
		private final List<MySQLTable> tables;
		private final List<MySQLColumn> columns;

		public MySQLTables(List<MySQLTable> tables) {
			this.tables = tables;
			columns = new ArrayList<>();
			for (MySQLTable t : tables) {
				columns.addAll(t.getColumns());
			}
		}

		public String tableNamesAsString() {
			return tables.stream().map(t -> t.getName()).collect(Collectors.joining(", "));
		}

		public List<MySQLTable> getTables() {
			return tables;
		}

		public List<MySQLColumn> getColumns() {
			return columns;
		}

		public String columnNamesAsString() {
			return getColumns().stream().map(t -> t.getTable().getName() + "." + t.getName())
					.collect(Collectors.joining(", "));
		}

		public String columnNamesAsString(Function<MySQLColumn, String> function) {
			return getColumns().stream().map(function).collect(Collectors.joining(", "));
		}

		public MySQLRowValue getRandomRowValue(Connection con, StateToReproduce state) throws SQLException {
			String randomRow = String.format("SELECT %s FROM %s ORDER BY RAND() LIMIT 1", columnNamesAsString(
					c -> c.getTable().getName() + "." + c.getName() + " AS " + c.getTable().getName() + c.getName()),
					// columnNamesAsString(c -> "typeof(" + c.getTable().getName() + "." + c.getName() + ")")
					tableNamesAsString());
			Map<MySQLColumn, MySQLConstant> values = new HashMap<>();
			try (Statement s = con.createStatement()) {
				ResultSet randomRowValues = s.executeQuery(randomRow);
				if (!randomRowValues.next()) {
					throw new AssertionError("could not find random row! " + randomRow + "\n" + state);
				}
				for (int i = 0; i < getColumns().size(); i++) {
					MySQLColumn column = getColumns().get(i);
					Object value;
					int columnIndex = randomRowValues.findColumn(column.getTable().getName() + column.getName());
					assert columnIndex == i + 1;
//					String typeString = randomRowValues.getString(columnIndex + getColumns().size());
//					MySQLDataType valueType = getColumnType(typeString);
					MySQLConstant constant;
//					if (randomRowValues.getString(columnIndex) == null) {
//						value = null;
//						constant = MySQLConstant.createNullConstant();
//					} else {
//						switch (valueType) {
//						case INT:
					if (randomRowValues.getString(columnIndex) == null) {
						constant = MySQLConstant.createNullConstant();
					} else {
						value = randomRowValues.getInt(columnIndex);
							constant = MySQLConstant.createIntConstant((int) value);
					}
//							break;
//						default:
//							throw new AssertionError(valueType);
//						}
//					}
					values.put(column, constant);
				}
				assert (!randomRowValues.next());
				// FIXME implement/refactor
//				state.randomRowValues = values;
				return new MySQLRowValue(this, values);
			}

		}

		private MySQLDataType getColumnType(String typeString) {
			return MySQLDataType.INT;
		}
	}

	public static class MySQLRowValue {
		
		private final MySQLTables tables;
		private final Map<MySQLColumn, MySQLConstant> values;

		MySQLRowValue(MySQLTables tables, Map<MySQLColumn, MySQLConstant> values) {
			this.tables = tables;
			this.values = values;
		}

		public MySQLTables getTable() {
			return tables;
		}

		public Map<MySQLColumn, MySQLConstant> getValues() {
			return values;
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			int i = 0;
			for (MySQLColumn c : tables.getColumns()) {
				if (i++ != 0) {
					sb.append(", ");
				}
				sb.append(values.get(c));
			}
			return sb.toString();
		}

		public String getRowValuesAsString() {
			List<MySQLColumn> columnsToCheck = tables.getColumns();
			return getRowValuesAsString(columnsToCheck);
		}

		public String getRowValuesAsString(List<MySQLColumn> columnsToCheck) {
			StringBuilder sb = new StringBuilder();
			Map<MySQLColumn, MySQLConstant> expectedValues = getValues();
			for (int i = 0; i < columnsToCheck.size(); i++) {
				if (i != 0) {
					sb.append(", ");
				}
				MySQLConstant expectedColumnValue = expectedValues.get(columnsToCheck.get(i));
				MySQLToStringVisitor visitor = new MySQLToStringVisitor();
				visitor.visit(expectedColumnValue);
				sb.append(visitor.get());
			}
			return sb.toString();
		}

	}
	
	
	public static class MySQLTable implements Comparable<MySQLTable> {

		private final String tableName;
		private final List<MySQLColumn> columns;

		public MySQLTable(String tableName, List<MySQLColumn> columns) {
			this.tableName = tableName;
			this.columns = Collections.unmodifiableList(columns);
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(tableName + "\n");
			for (MySQLColumn c : columns) {
				sb.append("\t" + c + "\n");
			}
			return sb.toString();
		}

		public String getName() {
			return tableName;
		}

		public List<MySQLColumn> getColumns() {
			return columns;
		}

		public String getColumnsAsString() {
			return columns.stream().map(c -> c.getName()).collect(Collectors.joining(", "));
		}

		public String getColumnsAsString(Function<MySQLColumn, String> function) {
			return columns.stream().map(function).collect(Collectors.joining(", "));
		}

		public MySQLColumn getRandomColumn() {
			return Randomly.fromList(columns);
		}

		@Override
		public int compareTo(MySQLTable o) {
			return o.getName().compareTo(tableName);
		}

		public List<MySQLColumn> getRandomNonEmptyColumnSubset() {
			return Randomly.nonEmptySubset(getColumns());
		}
	}

	static public MySQLSchema fromConnection(Connection con, String databaseName) throws SQLException {
		List<MySQLTable> databaseTables = new ArrayList<>();
		try (Statement s = con.createStatement()) {
			try (ResultSet rs = s
					.executeQuery("select DISTINCT TABLE_NAME from information_schema.columns where table_schema = '"
							+ databaseName + "';")) {
				while (rs.next()) {
					String tableName = rs.getString(1);
					List<MySQLColumn> databaseColumns = getTableColumns(con, tableName, databaseName);
					MySQLTable t = new MySQLTable(tableName, databaseColumns);
					for (MySQLColumn c : databaseColumns) {
						c.setTable(t);
					}
					databaseTables.add(t);
				}
			}
		}
		return new MySQLSchema(databaseTables);
	}

	private static List<MySQLColumn> getTableColumns(Connection con, String tableName, String databaseName)
			throws SQLException {
		List<MySQLColumn> columns = new ArrayList<>();
		try (Statement s = con.createStatement()) {
			try (ResultSet rs = s.executeQuery("select * from information_schema.columns where table_schema = '"
					+ databaseName + "' AND TABLE_NAME='" + tableName + "'")) {
				while (rs.next()) {
					String columnName = rs.getString("COLUMN_NAME");
					String dataType = rs.getString("DATA_TYPE");
					String precision = rs.getString("NUMERIC_PRECISION");
					boolean isPrimaryKey = rs.getString("COLUMN_KEY").equals("PRI");
					MySQLColumn c = new MySQLColumn(columnName, MySQLDataType.INT, isPrimaryKey);
					columns.add(c);
				}
			}
		}
		return columns;
	}

	private final List<MySQLTable> databaseTables;

	public MySQLSchema(List<MySQLTable> databaseTables) {
		this.databaseTables = Collections.unmodifiableList(databaseTables);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (MySQLTable t : getDatabaseTables()) {
			sb.append(t + "\n");
		}
		return sb.toString();
	}

	public MySQLTable getRandomTable() {
		return Randomly.fromList(getDatabaseTables());
	}
	
	public MySQLTables getRandomTableNonEmptyTables() {
		return new MySQLTables(Randomly.nonEmptySubset(databaseTables));
	}

	public List<MySQLTable> getDatabaseTables() {
		return databaseTables;
	}

	public List<MySQLTable> getDatabaseTablesRandomSubsetNotEmpty() {
		return Randomly.nonEmptySubset(databaseTables);
	}

}