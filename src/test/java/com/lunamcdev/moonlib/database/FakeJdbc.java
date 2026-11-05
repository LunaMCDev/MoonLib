package com.lunamcdev.moonlib.database;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A tiny scripted JDBC fake: records every statement and its parameters, answers queries from a
 * function, and keeps an in-memory migrations table so {@link Migrations} can be tested.
 */
final class FakeJdbc {

    final List<String> log = new CopyOnWriteArrayList<>();
    final AtomicInteger opened = new AtomicInteger();
    final AtomicInteger physicallyClosed = new AtomicInteger();
    final List<Object[]> migrationRows = new CopyOnWriteArrayList<>();
    Function<String, List<Map<String, Object>>> answers = sql -> Collections.emptyList();
    String failOn;

    Connection open() {
        opened.incrementAndGet();
        boolean[] autoCommit = {true};
        boolean[] closed = {false};
        List<Object[]> pendingMigrations = new ArrayList<>();
        return (Connection) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "prepareStatement":
                            return statement((String) a[0], autoCommit, pendingMigrations);
                        case "createStatement":
                            return statement(null, autoCommit, pendingMigrations);
                        case "setAutoCommit":
                            autoCommit[0] = (Boolean) a[0];
                            log.add("autoCommit=" + a[0]);
                            return null;
                        case "getAutoCommit":
                            return autoCommit[0];
                        case "commit":
                            log.add("commit");
                            migrationRows.addAll(pendingMigrations);
                            pendingMigrations.clear();
                            return null;
                        case "rollback":
                            log.add(a == null ? "rollback" : "rollback-savepoint");
                            if (a == null) {
                                pendingMigrations.clear();
                            }
                            return null;
                        case "setSavepoint":
                            log.add("savepoint");
                            return Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{Savepoint.class},
                                    (sp, sm, sa) -> null);
                        case "isValid":
                            return !closed[0];
                        case "isClosed":
                            return closed[0];
                        case "close":
                            closed[0] = true;
                            physicallyClosed.incrementAndGet();
                            return null;
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == a[0];
                        case "toString":
                            return "FakeConnection";
                        default:
                            return null;
                    }
                });
    }

    private Object statement(String preparedSql, boolean[] autoCommit, List<Object[]> pendingMigrations) {
        Map<Integer, Object> params = new LinkedHashMap<>();
        List<String> batch = new ArrayList<>();
        Class<?> type = preparedSql == null ? Statement.class : PreparedStatement.class;
        return Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            switch (m.getName()) {
                case "setObject":
                    params.put((Integer) a[0], a[1]);
                    return null;
                case "execute":
                    log.add("exec " + a[0]);
                    return false;
                case "executeUpdate": {
                    String line = render(preparedSql, params);
                    if (failOn != null && preparedSql.contains(failOn)) {
                        throw new SQLException("boom");
                    }
                    log.add(line);
                    if (preparedSql.startsWith("INSERT INTO " + Migrations.TABLE)) {
                        Object[] row = {params.get(1), params.get(2)};
                        if (autoCommit[0]) {
                            migrationRows.add(row);
                        } else {
                            pendingMigrations.add(row);
                        }
                    }
                    return 1;
                }
                case "executeQuery": {
                    log.add(render(preparedSql, params));
                    if (preparedSql.startsWith("SELECT version FROM " + Migrations.TABLE)) {
                        List<Map<String, Object>> rows = new ArrayList<>();
                        for (Object[] row : migrationRows) {
                            if (row[0].equals(params.get(1))) {
                                Map<String, Object> r = new LinkedHashMap<>();
                                r.put("version", row[1]);
                                rows.add(r);
                            }
                        }
                        return resultSet(rows);
                    }
                    return resultSet(answers.apply(preparedSql));
                }
                case "addBatch":
                    batch.add(render(preparedSql, params));
                    params.clear();
                    return null;
                case "executeBatch":
                    log.addAll(batch);
                    int[] counts = new int[batch.size()];
                    Arrays.fill(counts, 1);
                    batch.clear();
                    return counts;
                case "getGeneratedKeys": {
                    Map<String, Object> key = new LinkedHashMap<>();
                    key.put("1", 42L);
                    return resultSet(Collections.singletonList(key));
                }
                default:
                    return null;
            }
        });
    }

    private static String render(String sql, Map<Integer, Object> params) {
        if (params.isEmpty()) {
            return sql;
        }
        List<String> values = new ArrayList<>();
        for (Object v : params.values()) {
            values.add(v == null ? "null" : v.getClass().getSimpleName() + ":" + v);
        }
        return sql + " " + values;
    }

    static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] index = {-1};
        Object[] last = {null};
        return (ResultSet) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "next":
                            return ++index[0] < rows.size();
                        case "close":
                            return null;
                        case "wasNull":
                            return last[0] == null;
                        default:
                            break;
                    }
                    if (!m.getName().startsWith("get") || a == null || a.length != 1) {
                        return null;
                    }
                    Map<String, Object> row = rows.get(index[0]);
                    Object v = a[0] instanceof Integer ? new ArrayList<>(row.values()).get((Integer) a[0] - 1) : row.get(a[0]);
                    last[0] = v;
                    switch (m.getName()) {
                        case "getInt":
                            return v == null ? 0 : ((Number) v).intValue();
                        case "getLong":
                            return v == null ? 0L : ((Number) v).longValue();
                        case "getDouble":
                            return v == null ? 0d : ((Number) v).doubleValue();
                        case "getBoolean":
                            return v != null && (v instanceof Boolean ? (Boolean) v : ((Number) v).intValue() != 0);
                        case "getString":
                            return v == null ? null : v.toString();
                        default:
                            return v;
                    }
                });
    }
}
