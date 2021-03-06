/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Tests locking behavior under transaction. Subclass tests with no transaction.
 */
@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestSQLRepositoryLocking {

    protected static final Log log = LogFactory.getLog(TestSQLRepositoryJTAJCA.class);

    @SuppressWarnings("deprecation")
    private static final String ADMINISTRATOR = SecurityConstants.ADMINISTRATOR;

    @Inject
    protected CoreSession session;

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testLocking() throws Exception {
        DocumentModel root = session.getRootDocument();

        DocumentModel doc = new DocumentModelImpl("/", "doc", "File");
        doc = session.createDocument(doc);
        assertNull(doc.getLock()); // old
        assertNull(doc.getLockInfo());
        assertFalse(doc.isLocked());
        session.save();

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        doc.setLock();

        assertEquals(ADMINISTRATOR, doc.getLockInfo().getOwner());
        assertNotNull(doc.getLockInfo().getCreated());
        assertTrue(doc.getLock().startsWith(ADMINISTRATOR + ':')); // old
        assertTrue(doc.isLocked());

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");

        assertEquals(ADMINISTRATOR, doc.getLockInfo().getOwner());
        assertNotNull(doc.getLockInfo().getCreated());
        assertTrue(doc.getLock().startsWith(ADMINISTRATOR + ':')); // old
        assertTrue(doc.isLocked());

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        doc.removeLock();

        assertNull(doc.getLockInfo());
        assertFalse(doc.isLocked());

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");

        assertFalse(doc.isLocked());
    }

    @Test
    public void testLockingBeforeSave() throws Exception {
        DocumentModel root = session.getRootDocument();
        DocumentModel doc = new DocumentModelImpl("/", "doc", "File");
        doc = session.createDocument(doc);
        doc.setLock();
        session.save();

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        assertTrue(doc.isLocked());
    }

    // check we don't have a SQL-level locking error due to the lock manager
    // connection reading a row that was written but not yet committed by the
    // main connection
    @Test
    public void testGetLockAfterCreate() throws Exception {
        DocumentModel doc1 = new DocumentModelImpl("/", "doc1", "File");
        doc1 = session.createDocument(doc1);
        session.save();
        // read lock after save (SQL INSERT)
        assertNull(doc1.getLockInfo());

        DocumentModel doc2 = new DocumentModelImpl("/", "doc2", "File");
        doc2 = session.createDocument(doc2);
        session.save();
        // set lock after save (SQL INSERT)
        doc2.setLock();
    }

    protected CountDownLatch threadStartLatch;

    protected CountDownLatch lockingLatch;

    protected volatile boolean locked;

    @Test
    public void testLockingWithMultipleThreads() throws Exception {
        final String repositoryName = session.getRepositoryName();
        DocumentModel root = session.getRootDocument();
        DocumentModel doc = new DocumentModelImpl("/", "doc", "File");
        doc = session.createDocument(doc);
        session.save();

        nextTransaction();

        doc = session.getChild(root.getRef(), "doc");
        assertFalse(doc.isLocked());

        // start other thread
        threadStartLatch = new CountDownLatch(1);
        lockingLatch = new CountDownLatch(1);
        Thread t = new Thread() {
            @Override
            public void run() {
                TransactionHelper.startTransaction();
                try {
                    try (CoreSession session2 = CoreInstance.openCoreSession(repositoryName, ADMINISTRATOR)) {
                        DocumentModel root2 = session2.getRootDocument();
                        DocumentModel doc2 = session2.getChild(root2.getRef(), "doc");
                        // let main thread continue
                        threadStartLatch.countDown();
                        // wait main thread trigger
                        lockingLatch.await();
                        locked = doc2.isLocked();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    threadStartLatch.countDown(); // error recovery
                    TransactionHelper.commitOrRollbackTransaction();
                }
            }
        };
        t.start();
        threadStartLatch.await();

        doc.setLock();
        assertTrue(doc.isLocked());

        // trigger other thread check
        lockingLatch.countDown();
        t.join();

        assertTrue(locked);
    }

}
