package de.greenrobot.dao.query;


import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import de.greenrobot.dao.AbstractDao;

abstract class AbstractQueryData<T, Q extends AbstractQuery<T>> {
    final String sql;
    final AbstractDao<T, ?> dao;
    final String[] initialValues;
    final Map<Long, WeakReference<Q>> queriesForThreads;

    AbstractQueryData(AbstractDao<T, ?> dao, String sql, String[] initialValues) {
        this.dao = dao;
        this.sql = sql;
        this.initialValues = initialValues;
        queriesForThreads = new HashMap<Long, WeakReference<Q>>();
    }

    /** Just an optimized version, which performs faster if the current thread is already the query's owner thread. */
    Q forCurrentThread(Q query) {
        if (Thread.currentThread() == query.ownerThread) {
            System.arraycopy(initialValues, 0, query.parameters, 0, initialValues.length);
            return query;
        } else {
            return forCurrentThread();
        }
    }

    Q forCurrentThread() {
        // Process.myTid() seems to have issues on some devices (see Github #376) and Robolectric (#171):
        // We use currentThread().getId() instead (unfortunately return a long, can not use SparseArray).
        // PS.: thread ID may be reused, which should be fine because old thread will be gone anyway.
        long threadId = Thread.currentThread().getId();
        synchronized (queriesForThreads) {
            WeakReference<Q> queryRef = queriesForThreads.get(threadId);
            Q query = queryRef != null ? queryRef.get() : null;
            if (query == null) {
                gc();
                query = createQuery();
                queriesForThreads.put(threadId, new WeakReference<Q>(query));
            } else {
                System.arraycopy(initialValues, 0, query.parameters, 0, initialValues.length);
            }
            return query;
        }
    }

    abstract protected Q createQuery();

    void gc() {
        synchronized (queriesForThreads) {
            Iterator<Entry<Long, WeakReference<Q>>> iterator = queriesForThreads.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<Long, WeakReference<Q>> entry = iterator.next();
                if (entry.getValue() == null) {
                    iterator.remove();
                }
            }
        }
    }

}
