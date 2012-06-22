package com.continuuity.data.table;

import java.util.Map;

import com.continuuity.common.utils.ImmutablePair;

/**
 * A core table interface that supports columnar and versioned tables.
 *
 * Columnar implies that a row can have any number of dynamically inserted
 * columns (column-oriented).  Columns are sorted in ascending binary order.
 *
 * Versioned implies that every row+column can have multiple versions and these
 * can be used with a {@link ReadPointer} to provide visibility constraint
 * possibilities (ie. for use with a transactional system).
 */
public interface VersionedColumnarTable {

  /**
   * Writes the specified value at the specified version for the specified
   * row and column.
   * @param row
   * @param column
   * @param version
   * @param value
   */
  public void put(byte [] row, byte [] column, long version, byte [] value);

  /**
   * Writes the specified values for the specified columns at the specified
   * version for the specified row.
   * @param row
   * @param columns
   * @param version
   * @param values
   */
  public void put(byte [] row, byte [][] columns, long version,
      byte [][] values);

  /**
   * Deletes the specified version of the specified row and column.
   * @param row
   * @param column
   * @param version
   */
  public void delete(byte [] row, byte [] column, long version);

  /**
   * Deletes the specified version of the specified row and columns.
   * @param row
   * @param columns
   * @param version
   */
  public void delete(byte [] row, byte [][] columns, long version);

  /**
   * Deletes all versions of the specified row and column that have a version
   * less than or equal to the specified version.
   * @param row
   * @param column
   * @param version
   */
  public void deleteAll(byte [] row, byte [] column, long version);

  /**
   * Deletes all versions of the specified row and columns that have a version
   * less than or equal to the specified version.
   * @param row
   * @param columns
   * @param version
   */
  public void deleteAll(byte [] row, byte [][] columns, long version);

  /**
   * Undeletes (invalidates) a previously executed
   * {@link #deleteAll(byte[], byte[], long)} operation.
   * @param row
   * @param column
   * @param version
   */
  public void undeleteAll(byte [] row, byte [] column, long version);

  /**
   * Undeletes (invalidates) a previously executed
   * {@link #deleteAll(byte[], byte[][], long)} operation.
   * @param row
   * @param columns
   * @param version
   */
  public void undeleteAll(byte [] row, byte [][] columns, long version);

  /**
   * Reads the latest version of all columns in the specified row, utilizing
   * the specified read pointer to enforce visibility constraints.
   * @param row
   * @param readPointer
   * @return map of columns to values
   */
  public Map<byte [], byte []> get(byte [] row, ReadPointer readPointer);

  /**
   * Reads the latest version of the specified column in the specified row,
   * utilizing the specified read pointer to enforce visibility constraints,
   * and returns the value.
   * @param row
   * @param column
   * @param readPointer
   * @return value of the latest visible column in the specified row, or null if
   *         none exists
   */
  public byte [] get(byte [] row, byte [] column, ReadPointer readPointer);

  /**
   * Reads the latest version of the specified column in the specified row,
   * utilizing the specified read pointer to enforce visibility constraints,
   * and returns both the value as well as the version this value exists at.
   * @param row
   * @param column
   * @param readPointer
   * @return value and version of the latest visible column in the specified
   *         row, or null if none exists
   */
  public ImmutablePair<byte[],Long> getWithVersion(byte [] row, byte [] column,
      ReadPointer readPointer);

  /**
   * Reads the latest versions of all columns in the specified row that are
   * between the specified start (inclusive) and stop (exclusive) columns,
   * utilizing the specified read pointer to enforce visibility constraints.
   * @param row
   * @param startColumn beginning of range of columns, inclusive
   * @param stopColumn end of range of columns, exclusive
   * @param readPointer
   * @return map of columns to values, never null
   */
  public Map<byte [], byte []> get(byte [] row, byte [] startColumn,
      byte [] stopColumn, ReadPointer readPointer);

  /**
   * Reads the latest versions of the specified columns in the specified row,
   * utilizing the specified read pointer to enforce visibility constraints.
   * @param row
   * @param columns
   * @param readPointer
   * @return map of columns to values, never null
   */
  public Map<byte [], byte []> get(byte [] row, byte [][] columns,
      ReadPointer readPointer);

  /**
   * Increments (atomically) the specified row and column by the specified
   * amount, utilizing the specified read pointer to enforce visibility
   * constraints when performing the initial read.  The specified write version
   * will be used when performing the post-incremented write.
   * @param row
   * @param column
   * @param amount amount to increment column by
   * @param readPointer
   * @param writeVersion
   * @return value of counter after this increment is performed
   */
  public long increment(byte [] row, byte [] column, long amount,
      ReadPointer readPointer, long writeVersion);

  /**
   * Increments (atomically) the specified row and columns by the specified
   * amounts, utilizing the specified read pointer to enforce visibility
   * constraints when performing the initial reads.  The specified write version
   * will be used when performing the post-incremented writes.
   * @param row
   * @param columns
   * @param amounts amounts to increment columns by
   * @param readPointer
   * @param writeVersion
   * @return values of counters after the increments are performed, never null
   */
  public Map<byte[],Long> increment(byte [] row, byte [][] columns, long [] amounts,
      ReadPointer readPointer, long writeVersion);

  /**
   * Compares-and-swaps (atomically) the value of the specified row and column
   * by looking for the specified expected value and if found, replacing with
   * the specified new value.  Utilizes the specified read pointer to enforce
   * visibility constraints on the read, utilizes the specified write version
   * to perform 
   * @param row
   * @param column
   * @param expectedValue
   * @param newValue
   * @param readPointer
   * @param writeVersion
   * @return true if atomic CAS was successful, false if not
   */
  public boolean compareAndSwap(byte [] row, byte [] column,
      byte [] expectedValue, byte [] newValue, ReadPointer readPointer,
      long writeVersion);
}
