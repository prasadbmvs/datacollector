/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.destination.cassandra;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.exceptions.AuthenticationException;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.streamsets.pipeline.api.Batch;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.base.BaseTarget;
import com.streamsets.pipeline.api.impl.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Cassandra Destination for StreamSets Data Collector
 *
 * Some basic ground rules for the Cassandra Java Driver:
 * - Use one cluster instance per (physical) cluster (per application lifetime).
 * - Use at most one session instance per keyspace, or use a single Session and
 *   explicitly specify the keyspace in your queries.
 * - If you execute a statement more than once, consider using a prepared statement.
 * - You can reduce the number of network round trips and also have atomic operations by using batches.
 *
 */
public class CassandraTarget extends BaseTarget {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraTarget.class);

  private final List<String> addresses;
  private List<InetAddress> contactPoints;
  private final int port;
  private final String username;
  private final String password;

  private final String qualifiedTableName;
  private final List<CassandraFieldMappingConfig> columnNames;


  private Cluster cluster = null;
  private Session session = null;

  private PreparedStatement insertStatement = null;
  private SortedMap<String, String> columnMappings = new TreeMap<>();

  public CassandraTarget(
      final List<String> addresses,
      final int port,
      final String username,
      final String password,
      final String qualifiedTableName,
      final List<CassandraFieldMappingConfig> columnNames
  ) {
    this.addresses = addresses;
    this.port = port;
    this.username = username;
    this.password = password;
    this.qualifiedTableName = qualifiedTableName;
    this.columnNames = columnNames;
  }

  @Override
  protected void init() throws StageException {
    super.init();

    cluster = Cluster.builder()
        .addContactPoints(contactPoints)
        .withPort(port)
        // If authentication is disabled on the C* cluster, this method has no effect.
        .withCredentials(username, password)
        .build();

    try {
      session = cluster.connect();
    } catch (NoHostAvailableException | AuthenticationException | IllegalStateException e) {
      throw new StageException(Errors.CASSANDRA_03, e);
    }

    setColumnMappings();

    // The INSERT query we're going to perform (parameterized).
    final String query = String.format(
        "INSERT INTO %s (%s) VALUES (%s);",
        qualifiedTableName,
        Joiner.on(", ").join(columnMappings.keySet()).replaceAll("/", ""),
        Joiner.on(", ").join(Collections.nCopies(columnNames.size(), "?"))
    );
    LOG.info("Prepared Query: {}", query);
    insertStatement = session.prepare(query);
  }

  @Override
  public void destroy() {
    super.destroy();
    try {
      session.close();
      cluster.close();
    } catch (NullPointerException e) {
      LOG.warn("Tried to close cluster, but it was null.");
    }
  }

  @Override
  protected List<ConfigIssue> validateConfigs() throws StageException {
    List<ConfigIssue> issues = super.validateConfigs();

    Target.Context context = getContext();
    if (addresses.isEmpty()) {
      issues.add(context.createConfigIssue(Groups.CASSANDRA.name(), "contactNodes", Errors.CASSANDRA_00));
    }

    for (String address : addresses) {
      if (address.isEmpty()) {
        issues.add(context.createConfigIssue(Groups.CASSANDRA.name(), "contactNodes", Errors.CASSANDRA_01));
      }
    }

    contactPoints = new ArrayList<>(addresses.size());
    for (String address : addresses) {
      if (null == address) {
        LOG.warn("A null value was passed in as a contact point.");
        // This isn't valid but InetAddress won't complain so we skip this entry.
        continue;
      }

      try {
        contactPoints.add(InetAddress.getByName(address));
      } catch (UnknownHostException e) {
        issues.add(context.createConfigIssue(Groups.CASSANDRA.name(), "contactNodes", Errors.CASSANDRA_04, address));
      }
    }

    if (contactPoints.size() < 1) {
      issues.add(context.createConfigIssue(Groups.CASSANDRA.name(), "contactNodes", Errors.CASSANDRA_00));
    }

    if (!qualifiedTableName.contains(".")) {
      issues.add(context.createConfigIssue(Groups.CASSANDRA.name(), "qualifiedTableName", Errors.CASSANDRA_02));
    }

    checkCassandraReachable(issues);

    if (!isColumnMappingValid()) {
      issues.add(context.createConfigIssue(Groups.CASSANDRA.name(), "columnNames", Errors.CASSANDRA_08));
    }

    return issues;
  }

  private boolean isColumnMappingValid() {
    columnMappings = new TreeMap<>();
    for (CassandraFieldMappingConfig column : columnNames) {
      columnMappings.put(column.columnName, column.field);
    }

    final String[] tableNameParts = qualifiedTableName.split("\\.");
    final String keyspace = tableNameParts[0];
    final String table = tableNameParts[1];

    try (Cluster cluster = getCluster()) {
      final KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);
      final TableMetadata tableMetadata = keyspaceMetadata.getTable(table);
      final List<ColumnMetadata> columns = tableMetadata.getColumns();

      for (ColumnMetadata column : columns) {
        if (!columnMappings.keySet().contains(column.getName())) {
          return false;
        }
      }
    } catch (Exception ignored) {
      return false;
    }

    return true;
  }

  private void checkCassandraReachable(List<ConfigIssue> issues) {
    try (Cluster cluster = getCluster()) {
      Session session = cluster.connect();
      session.close();
    } catch (NoHostAvailableException | AuthenticationException | IllegalStateException e) {
      Target.Context context = getContext();
      issues.add(
              context.createConfigIssue(
              Groups.CASSANDRA.name(),
              "contactNodes",
              Errors.CASSANDRA_05, e.getMessage()
          )
      );
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void write(Batch batch) throws StageException {
    // The batch holding the current batch to INSERT.
    BatchStatement batchedStatement = new BatchStatement();

    Iterator<Record> records = batch.getRecords();

      while (records.hasNext()) {
        final Record record = records.next();

        try {
          ImmutableList.Builder<Object> values = new ImmutableList.Builder<>();
          for (String field : columnMappings.values()) {
            final Object value = record.get(field).getValue();
            // Special cases for handling SDC Lists and Maps,
            // basically unpacking them into raw types.
            if (value instanceof List) {
              List<Object> unpackedList = new ArrayList<>();
              for (Field item : (List<Field>) value) {
                unpackedList.add(item.getValue());
              }
              values.add(unpackedList);
            } else if (value instanceof Map) {
              Map<Object, Object> unpackedMap = new HashMap<>();
              for (Map.Entry<String, Field> entry : ((Map<String, Field>) value).entrySet()) {
                unpackedMap.put(entry.getKey(), entry.getValue().getValue());
              }
              values.add(unpackedMap);
            } else {
              values.add(value);
            }
          }

          // .toArray required to pass in a list to a varargs method.
          batchedStatement.add(insertStatement.bind(values.build().toArray()));
        } catch (Exception e) {
          switch (getContext().getOnErrorRecord()) {
            case DISCARD:
              break;
            case TO_ERROR:
              getContext().toError(record, Errors.CASSANDRA_06, record.getHeader().getSourceId(), e.getMessage(), e);
              break;
            case STOP_PIPELINE:
              throw new StageException(Errors.CASSANDRA_06, record.getHeader().getSourceId(), e.getMessage(), e);
            default:
              throw new IllegalStateException(
                  Utils.format("It should never happen. OnError '{}'", getContext().getOnErrorRecord(), e)
              );
          }
        }
      }

      if (batchedStatement.size() > 0) {
        try {
          session.execute(batchedStatement);
        } catch (Exception e) {
          switch (getContext().getOnErrorRecord()) {
            case DISCARD:
              break;
            case TO_ERROR:
              Iterator<Record> failedRecords = batch.getRecords();
              while (failedRecords.hasNext()) {
                final Record record = records.next();
                getContext().toError(record, Errors.CASSANDRA_09, record.getHeader().getSourceId(), e.getMessage(), e);
              }
            case STOP_PIPELINE:
              throw new StageException(Errors.CASSANDRA_07, e);
            default:
              throw new IllegalStateException(
                  Utils.format("It should never happen. OnError '{}'", getContext().getOnErrorRecord(), e)
              );
          }
        }
      }
  }

  private Cluster getCluster() {
    return Cluster.builder()
        .addContactPoints(contactPoints)
        .withPort(port)
        .build();
  }

  private void setColumnMappings() {
    columnMappings = new TreeMap<>();
    for (CassandraFieldMappingConfig column : columnNames) {
      columnMappings.put(column.columnName, column.field);
    }
  }
}