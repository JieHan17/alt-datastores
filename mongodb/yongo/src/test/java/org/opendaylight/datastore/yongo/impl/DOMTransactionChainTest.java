/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.opendaylight.datastore.yongo.impl.util.TestModel;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

public class DOMTransactionChainTest extends AbstractDataStoreTest {


    public DOMTransactionChainTest() throws Exception {
    }

    @Test
    public void testTransactionChainNoConflict() throws InterruptedException, ExecutionException, TimeoutException {
        final BlockingTransactionChainListener listener = new BlockingTransactionChainListener();
        final DOMTransactionChain txChain = getDomDataBroker().createTransactionChain(listener);
        assertNotNull(txChain);

        /**
         * We alocate new read-write transaction and write /test.
         */
        final DOMDataTreeWriteTransaction firstTx = allocateAndWrite(txChain);

        /**
         * First transaction is marked as ready, we are able to allocate chained
         * transactions.
         */
        final ListenableFuture<? extends CommitInfo> firstWriteTxFuture = firstTx.commit();

        /**
         * We alocate chained transaction - read transaction.
         */
        final DOMDataTreeReadTransaction secondReadTx = txChain.newReadOnlyTransaction();

        /**
         *
         * We test if we are able to read data from tx, read should not fail
         * since we are using chained transaction.
         *
         *
         */
        assertTestContainerExists(secondReadTx);

        /**
         * We alocate next transaction, which is still based on first one, but
         * is read-write.
         *
         */
        final DOMDataTreeWriteTransaction thirdDeleteTx = allocateAndDelete(txChain);

        /**
         * We commit first transaction
         *
         */
        assertCommitSuccessful(firstWriteTxFuture);

        /**
         * Allocates transaction from data store.
         *
         */
        final DOMDataTreeReadTransaction storeReadTx = getDomDataBroker().newReadOnlyTransaction();

        /**
         * We verify transaction is commited to store, container should exists
         * in datastore.
         */
        assertTestContainerExists(storeReadTx);

        /**
         * third transaction is sealed and commited.
         */
        assertCommitSuccessful(thirdDeleteTx.commit());

        /**
         * We close transaction chain.
         */
        txChain.close();

        listener.getSuccessFuture().get(1000, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Test
    public void testTransactionChainNotSealed() throws InterruptedException, ExecutionException, TimeoutException {
        final BlockingTransactionChainListener listener = new BlockingTransactionChainListener();
        final DOMTransactionChain txChain = getDomDataBroker().createTransactionChain(listener);
        assertNotNull(txChain);

        /**
         * We alocate new read-write transaction and write /test
         */
        allocateAndWrite(txChain);

        /**
         * We alocate chained transaction - read transaction, note first one is
         * still not commited to datastore, so this allocation should fail with
         * IllegalStateException.
         */
        try {
            txChain.newReadOnlyTransaction();
            fail("Allocation of secondReadTx should fail with IllegalStateException");
        } catch (final Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
        // close it so the next test case would not wait for timeout.
        txChain.close();
    }

    private static DOMDataTreeWriteTransaction allocateAndDelete(final DOMTransactionChain txChain)
            throws InterruptedException, ExecutionException {
        final DOMDataTreeWriteTransaction tx = txChain.newWriteOnlyTransaction();
        /**
         * We delete node in third transaction
         */
        tx.delete(LogicalDatastoreType.OPERATIONAL, TestModel.TEST_PATH);
        return tx;
    }

    private static DOMDataTreeWriteTransaction allocateAndWrite(final DOMTransactionChain txChain)
            throws InterruptedException, ExecutionException {
        final DOMDataTreeWriteTransaction tx = txChain.newWriteOnlyTransaction();
        writeTestContainer(tx);
        return tx;
    }

    private static void assertCommitSuccessful(final ListenableFuture<? extends CommitInfo> firstWriteTxFuture)
            throws InterruptedException, ExecutionException {
        firstWriteTxFuture.get();
    }

    private static void assertTestContainerExists(final DOMDataTreeReadTransaction readTx)
            throws InterruptedException, ExecutionException {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> readFuture =
                readTx.read(OPERATIONAL, TestModel.TEST_PATH);
        final Optional<NormalizedNode<?, ?>> readedData = readFuture.get();
        assertTrue(readedData.isPresent());
    }

    private static void writeTestContainer(final DOMDataTreeWriteTransaction tx) throws InterruptedException,
            ExecutionException {
        tx.put(OPERATIONAL, TestModel.TEST_PATH, ImmutableNodes.containerNode(TestModel.TEST_QNAME));
    }
}
