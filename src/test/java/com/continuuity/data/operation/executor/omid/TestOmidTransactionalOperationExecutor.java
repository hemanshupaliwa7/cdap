/**
 * Copyright (C) 2012 Continuuity, Inc.
 */
package com.continuuity.data.operation.executor.omid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import com.continuuity.common.utils.ImmutablePair;
import com.continuuity.data.engine.memory.oracle.MemoryStrictlyMonotonicOracle;
import com.continuuity.data.operation.Increment;
import com.continuuity.data.operation.Read;
import com.continuuity.data.operation.Write;
import com.continuuity.data.operation.executor.TransactionException;
import com.continuuity.data.operation.executor.omid.memory.MemoryOracle;
import com.continuuity.data.operation.executor.omid.memory.MemoryRowSet;
import com.continuuity.data.operation.ttqueue.DequeueResult;
import com.continuuity.data.operation.ttqueue.QueueAck;
import com.continuuity.data.operation.ttqueue.QueueConfig;
import com.continuuity.data.operation.ttqueue.QueueConsumer;
import com.continuuity.data.operation.ttqueue.QueueDequeue;
import com.continuuity.data.operation.ttqueue.QueueEnqueue;
import com.continuuity.data.operation.ttqueue.QueuePartitioner;
import com.continuuity.data.operation.type.WriteOperation;
import com.continuuity.data.table.ReadPointer;
import com.continuuity.data.table.handles.SimpleOVCTableHandle;

public class TestOmidTransactionalOperationExecutor {

  private final TimestampOracle timeOracle = new MemoryStrictlyMonotonicOracle();
  private final TransactionOracle oracle = new MemoryOracle(this.timeOracle);
  private final Configuration conf = new Configuration();

  @Test
  public void testSimple() throws Exception {

    OmidTransactionalOperationExecutor executor =
        new OmidTransactionalOperationExecutor(this.oracle,
            new SimpleOVCTableHandle(this.timeOracle, this.conf));

    byte [] key = Bytes.toBytes("key");
    byte [] value = Bytes.toBytes("value");

    // start a transaction
    ImmutablePair<ReadPointer, Long> pointer = executor.startTransaction();
    RowSet rows = new MemoryRowSet();

    // write to a key
    executor.write(new Write(key, value), pointer);
    rows.addRow(key);

    // read should see nothing
    assertNull(executor.execute(new Read(key)));

    // commit
    assertTrue(executor.commitTransaction(pointer, rows));

    // read should see the write
    byte [] readValue = executor.execute(new Read(key));
    assertNotNull(readValue);
    assertTrue(Bytes.equals(readValue, value));
  }

  @Test
  public void testOverlappingConcurrentWrites() throws Exception {
    OmidTransactionalOperationExecutor executor =
        new OmidTransactionalOperationExecutor(this.oracle,
            new SimpleOVCTableHandle(this.timeOracle, this.conf));

    byte [] key = Bytes.toBytes("key");
    byte [] valueOne = Bytes.toBytes("value1");
    byte [] valueTwo = Bytes.toBytes("value2");

    // start tx one
    ImmutablePair<ReadPointer, Long> pointerOne = executor.startTransaction();
    RowSet rowsOne = new MemoryRowSet();
    System.out.println("Started transaction one : " + pointerOne);

    // write value one
    executor.write(new Write(key, valueOne), pointerOne);
    rowsOne.addRow(key);

    // read should see nothing
    assertNull(executor.execute(new Read(key)));

    // start tx two
    ImmutablePair<ReadPointer, Long> pointerTwo = executor.startTransaction();
    RowSet rowsTwo = new MemoryRowSet();
    System.out.println("Started transaction two : " + pointerTwo);
    assertTrue(pointerTwo.getSecond() > pointerOne.getSecond());

    // write value two
    executor.write(new Write(key, valueTwo), pointerTwo);
    rowsTwo.addRow(key);

    // read should see nothing
    assertNull(executor.execute(new Read(key)));

    // commit tx two, should succeed
    assertTrue(executor.commitTransaction(pointerTwo, rowsTwo));

    // even though tx one not committed, we can see two already
    byte [] readValue = executor.execute(new Read(key));
    assertNotNull(readValue);
    assertTrue(Bytes.equals(readValue, valueTwo));

    // commit tx one, should fail
    assertFalse(executor.commitTransaction(pointerOne, rowsOne));

    // should still see two
    readValue = executor.execute(new Read(key));
    assertNotNull(readValue);
    assertTrue(Bytes.equals(readValue, valueTwo));
  }

  @Test
  public void testClosedTransactionsThrowExceptions() throws Exception {
    OmidTransactionalOperationExecutor executor =
        new OmidTransactionalOperationExecutor(this.oracle,
            new SimpleOVCTableHandle(this.timeOracle, this.conf));

    byte [] key = Bytes.toBytes("testClosedTransactionsThrowExceptions");

    // start txwOne
    ImmutablePair<ReadPointer, Long> pointerOne = executor.startTransaction();
    RowSet rowsOne = new MemoryRowSet();
    System.out.println("Started transaction txwOne : " + pointerOne);

    // write and commit
    executor.write(new Write(key, Bytes.toBytes(1)), pointerOne);
    rowsOne.addRow(key);
    assertTrue(executor.commitTransaction(pointerOne, rowsOne));

    // trying to write with this tx should throw exception
    // This is no longer enforced at this level.  This test uses package
    // private methods that let it write to closed transactions.  The executor
    // itself enforces this automatically so we don't need to guard against
    // this case any longer.
    //    try {
    //      executor.write(new Write(key, Bytes.toBytes(2)), pointerOne);
    //      fail("Writing with committed transaction should throw exception");
    //    } catch (TransactionException te) {
    //      // correct
    //    }

    // trying to commit this tx should throw exception
    try {
      executor.commitTransaction(pointerOne, rowsOne);
      fail("Committing with committed transaction should throw exception");
    } catch (TransactionException te) {
      // correct
    }

    // read should see value 1 not 2
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(1)));
  }

  @Test
  public void testOverlappingConcurrentReadersAndWriters() throws Exception {
    OmidTransactionalOperationExecutor executor =
        new OmidTransactionalOperationExecutor(this.oracle,
            new SimpleOVCTableHandle(this.timeOracle, this.conf));

    byte [] key = Bytes.toBytes("testOverlappingConcurrentReadersAndWriters");

    // start txwOne
    ImmutablePair<ReadPointer, Long> pointerWOne = executor.startTransaction();
    RowSet rowsOne = new MemoryRowSet();
    System.out.println("Started transaction txwOne : " + pointerWOne);

    // write 1
    executor.write(new Write(key, Bytes.toBytes(1)), pointerWOne);
    rowsOne.addRow(key);

    // read should see nothing
    assertNull(executor.execute(new Read(key)));

    // commit write 1
    assertTrue(executor.commitTransaction(pointerWOne, rowsOne));

    // read sees 1
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(1)));

    // open long-running read
    ImmutablePair<ReadPointer, Long> pointerReadOne =
        executor.startTransaction();

    // write 2 and commit immediately
    ImmutablePair<ReadPointer, Long> pointerWTwo = executor.startTransaction();
    RowSet rowsTwo = new MemoryRowSet();
    System.out.println("Started transaction txwTwo : " + pointerWTwo);
    executor.write(new Write(key, Bytes.toBytes(2)), pointerWTwo);
    rowsTwo.addRow(key);
    assertTrue(executor.commitTransaction(pointerWTwo, rowsTwo));

    // read sees 2
    byte [] value = executor.execute(new Read(key));
    assertNotNull(value);
    System.out.println("Value is : " + value.length + ", " +
        Bytes.toInt(value));
    assertTrue("expect 2, actually " + Bytes.toInt(value),
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(2)));

    // open long-running read
    ImmutablePair<ReadPointer, Long> pointerReadTwo =
        executor.startTransaction();

    // write 3 with one transaction but don't commit
    ImmutablePair<ReadPointer, Long> pointerWThree =
        executor.startTransaction();
    RowSet rowsThree = new MemoryRowSet();
    System.out.println("Started transaction txwThree : " + pointerWThree);
    executor.write(new Write(key, Bytes.toBytes(3)), pointerWThree);
    rowsThree.addRow(key);

    // write 4 with another transaction and also don't commit
    ImmutablePair<ReadPointer, Long> pointerWFour =
        executor.startTransaction();
    RowSet rowsFour = new MemoryRowSet();
    System.out.println("Started transaction txwFour : " + pointerWFour);
    executor.write(new Write(key, Bytes.toBytes(4)), pointerWFour);
    rowsFour.addRow(key);

    // read sees 2 still
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(2)));

    // commit 4, should be successful
    assertTrue(executor.commitTransaction(pointerWFour, rowsFour));

    // read sees 4
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(4)));

    // commit 3, should fail
    assertFalse(executor.commitTransaction(pointerWThree, rowsThree));

    // read still sees 4
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(4)));

    // now read with long-running read 1, should see value = 1
    assertTrue(
        Bytes.equals(executor.read(new Read(key), pointerReadOne.getFirst()),
            Bytes.toBytes(1)));

    // now do the same thing but in reverse order of conflict

    // write 5 with one transaction but don't commit
    ImmutablePair<ReadPointer, Long> pointerWFive =
        executor.startTransaction();
    RowSet rowsFive = new MemoryRowSet();
    System.out.println("Started transaction txwFive : " + pointerWFive);
    executor.write(new Write(key, Bytes.toBytes(5)), pointerWFive);
    rowsFive.addRow(key);

    // write 6 with another transaction and also don't commit
    ImmutablePair<ReadPointer, Long> pointerWSix =
        executor.startTransaction();
    RowSet rowsSix = new MemoryRowSet();
    System.out.println("Started transaction txwSix : " + pointerWSix);
    executor.write(new Write(key, Bytes.toBytes(6)), pointerWSix);
    rowsSix.addRow(key);

    // read sees 4 still
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(4)));

    // long running reads should still see their respective values
    assertTrue(
        Bytes.equals(executor.read(new Read(key), pointerReadOne.getFirst()),
            Bytes.toBytes(1)));
    assertTrue(
        Bytes.equals(executor.read(new Read(key), pointerReadTwo.getFirst()),
            Bytes.toBytes(2)));

    // commit 5, should be successful
    assertTrue(executor.commitTransaction(pointerWFive, rowsFive));

    // read sees 5
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(5)));

    // long running reads should still see their respective values
    assertTrue(
        Bytes.equals(executor.read(new Read(key), pointerReadOne.getFirst()),
            Bytes.toBytes(1)));
    assertTrue(
        Bytes.equals(executor.read(new Read(key), pointerReadTwo.getFirst()),
            Bytes.toBytes(2)));

    // commit 6, should fail
    assertFalse(executor.commitTransaction(pointerWSix, rowsSix));

    // read still sees 5
    assertTrue(
        Bytes.equals(executor.execute(new Read(key)), Bytes.toBytes(5)));

    // long running reads should still see their respective values
    assertTrue(
        Bytes.equals(executor.read(new Read(key), pointerReadOne.getFirst()),
            Bytes.toBytes(1)));
    assertTrue(
        Bytes.equals(executor.read(new Read(key), pointerReadTwo.getFirst()),
            Bytes.toBytes(2)));
  }

  @Test
  public void testAbortedOperationsWithQueueAck() throws Exception {

    OmidTransactionalOperationExecutor executor =
        new OmidTransactionalOperationExecutor(this.oracle,
            new SimpleOVCTableHandle(this.timeOracle, this.conf));

    byte [] key = Bytes.toBytes("testAbortedAck");
    byte [] queueName = Bytes.toBytes("testAbortedAckQueue");

    // Enqueue something
    assertTrue(executor.execute(batch(new QueueEnqueue(queueName, queueName)))
        .isSuccess());

    // Dequeue it
    QueueConsumer consumer = new QueueConsumer(0, 0, 1);
    QueueConfig config = new QueueConfig(
        new QueuePartitioner.RandomPartitioner(), true);
    DequeueResult dequeueResult = executor.execute(
        new QueueDequeue(queueName, consumer, config));
    assertTrue(dequeueResult.isSuccess());

    // Start our ack operation
    ImmutablePair<ReadPointer,Long> ackPointer = executor.startTransaction();

    // Start a fake operation that will just conflict with our key
    ImmutablePair<ReadPointer,Long> fakePointer = executor.startTransaction();
    RowSet rows = new MemoryRowSet();
    rows.addRow(key);

    // Commit fake operation successfully
    assertTrue(executor.commitTransaction(fakePointer, rows));

    // Increment a counter and add our ack
    List<WriteOperation> writes = new ArrayList<WriteOperation>(2);
    writes.add(new Increment(key, 3));
    writes.add(new QueueAck(queueName,
        dequeueResult.getEntryPointer(), consumer));

    // Execute should return failure
    assertFalse(executor.execute(writes, ackPointer).isSuccess());

    // Should still be able to dequeue
    dequeueResult = executor.execute(
        new QueueDequeue(queueName, consumer, config));
    // THIS FAILS IF ACK NOT REALLY ROLLED BACK!
    assertTrue(dequeueResult.isSuccess());


    // Start new ack operation
    ackPointer = executor.startTransaction();

    // Same increment and ack
    writes = new ArrayList<WriteOperation>(2);
    writes.add(new Increment(key, 5));
    writes.add(new QueueAck(queueName,
        dequeueResult.getEntryPointer(), consumer));

    // Execute should succeed
    assertTrue(executor.execute(writes, ackPointer).isSuccess());


    // Dequeue should now return empty
    dequeueResult = executor.execute(
        new QueueDequeue(queueName, consumer, config));
    assertTrue(dequeueResult.isEmpty());

    // Incremented value should be 5
    assertEquals(5L, Bytes.toLong(executor.execute(new Read(key))));

  }

  private static List<WriteOperation> batch(WriteOperation ... ops) {
    return Arrays.asList(ops);
  }
}
