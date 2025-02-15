/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.di.trans.dataservice.optimization.cache;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.dataservice.DataServiceExecutor;
import org.pentaho.di.trans.dataservice.execution.DefaultTransWiring;
import org.pentaho.di.trans.dataservice.execution.TransStarter;
import org.pentaho.di.core.RowMetaAndData;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.trans.RowProducer;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.step.StepMetaDataCombi;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author nhudak
 */
class CachedServiceLoader {
  private final Executor executor;
  private final Supplier<Iterator<RowMetaAndData>> rowSupplier;

  CachedServiceLoader( Executor executor, Supplier<Iterator<RowMetaAndData>> rowSupplier ) {
    this.executor = executor;
    this.rowSupplier = rowSupplier;
  }

  CachedServiceLoader( CachedService cachedService, Executor executor ) {
    this( executor, () -> cachedService.getRowMetaAndData().iterator() );
  }

  ListenableFuture<Integer> replay( DataServiceExecutor dataServiceExecutor ) throws KettleException {
    final Trans serviceTrans = dataServiceExecutor.getServiceTrans(), genTrans = dataServiceExecutor.getGenTrans();
    final CountDownLatch startReplay = new CountDownLatch( 1 );
    final RowProducer rowProducer = dataServiceExecutor.addRowProducer();

    List<Runnable> startTrans = dataServiceExecutor.getListenerMap().get( DataServiceExecutor.ExecutionPoint.START ),
      postOptimization = dataServiceExecutor.getListenerMap().get( DataServiceExecutor.ExecutionPoint.READY );

    Iterables.removeIf( postOptimization, Predicates.instanceOf( DefaultTransWiring.class ) );
    Iterables.removeIf( startTrans,
      new Predicate<Runnable>() {
        @Override public boolean apply( Runnable runnable ) {
          return runnable instanceof TransStarter && ( (TransStarter) runnable ).getTrans().equals( serviceTrans );
        }
      }
    );

    postOptimization.add( new Runnable() {
      @Override public void run() {
        serviceTrans.stopAll();
        for ( StepMetaDataCombi stepMetaDataCombi : serviceTrans.getSteps() ) {
          stepMetaDataCombi.step.setOutputDone();
          stepMetaDataCombi.step.dispose( stepMetaDataCombi.meta, stepMetaDataCombi.data );
          stepMetaDataCombi.step.markStop();
        }
      }
    } );

    startTrans.add( new Runnable() {
      @Override public void run() {
        startReplay.countDown();
      }
    } );

    ListenableFutureTask<Integer> replay = ListenableFutureTask.create( new Callable<Integer>() {
      @Override public Integer call() throws Exception {
        Preconditions.checkState( startReplay.await( 30, TimeUnit.SECONDS ), "Cache replay did not start" );
        int rowCount = 0;
        for ( Iterator<RowMetaAndData> iterator = rowSupplier.get();
              iterator.hasNext() && genTrans.isRunning(); ) {
          RowMetaAndData metaAndData = iterator.next();
          boolean rowAdded = false;
          RowMetaInterface rowMeta = metaAndData.getRowMeta();
          Object[] rowData = rowMeta.cloneRow( metaAndData.getData() );
          while ( !rowAdded && genTrans.isRunning() ) {
            rowAdded = rowProducer.putRowWait( rowMeta, rowData, 10, TimeUnit.SECONDS );
          }
          if ( rowAdded ) {
            rowCount += 1;
          }
        }
        rowProducer.finished();
        return rowCount;
      }
    } );
    executor.execute( replay );
    return replay;
  }
}
