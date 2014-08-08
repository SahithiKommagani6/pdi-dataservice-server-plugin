/*!
 * PENTAHO CORPORATION PROPRIETARY AND CONFIDENTIAL
 *
 * Copyright 2002 - 2014 Pentaho Corporation (Pentaho). All rights reserved.
 *
 * NOTICE: All information including source code contained herein is, and
 * remains the sole property of Pentaho and its licensors. The intellectual
 * and technical concepts contained herein are proprietary and confidential
 * to, and are trade secrets of Pentaho and may be covered by U.S. and foreign
 * patents, or patents in process, and are protected by trade secret and
 * copyright laws. The receipt or possession of this source code and/or related
 * information does not convey or imply any rights to reproduce, disclose or
 * distribute its contents, or to manufacture, use, or sell anything that it
 * may describe, in whole or in part. Any reproduction, modification, distribution,
 * or public display of this information without the express written authorization
 * from Pentaho is strictly prohibited and in violation of applicable laws and
 * international treaties. Access to the source code contained herein is strictly
 * prohibited to anyone except those individuals and entities who have executed
 * confidentiality and non-disclosure agreements or other agreements with Pentaho,
 * explicitly covering such access.
 */

package org.pentaho.di.repository.pur;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.pentaho.di.core.util.ExecutorUtil;

public class ActiveCache<Key, Value> {
  private final Map<Key, ActiveCacheResult<Value>> valueMap;
  private final Map<Key, Future<ActiveCacheResult<Value>>> loadingMap;
  private final ActiveCacheLoader<Key, Value> loader;
  private final long timeout;
  private final ExecutorServiceGetter executorServiceGetter;

  public static interface ExecutorServiceGetter {
    ExecutorService getExecutor();
  }

  public ActiveCache( ActiveCacheLoader<Key, Value> loader, long timeout ) {
    this( loader, timeout, new ExecutorServiceGetter() {

      @Override
      public ExecutorService getExecutor() {
        return ExecutorUtil.getExecutor();
      }
    } );
  }

  public ActiveCache( ActiveCacheLoader<Key, Value> loader, long timeout, ExecutorServiceGetter executorServiceGetter ) {
    this( loader, new HashMap<Key, ActiveCacheResult<Value>>(), new HashMap<Key, Future<ActiveCacheResult<Value>>>(),
        timeout, executorServiceGetter );
  }

  public ActiveCache( ActiveCacheLoader<Key, Value> loader, Map<Key, ActiveCacheResult<Value>> valueMap,
      Map<Key, Future<ActiveCacheResult<Value>>> loadingMap, long timeout, ExecutorServiceGetter executorServiceGetter ) {
    this.valueMap = valueMap;
    this.loadingMap = loadingMap;
    this.loader = loader;
    this.timeout = timeout;
    this.executorServiceGetter = executorServiceGetter;
  }

  public Value get( Key key ) throws Exception {
    ActiveCacheResult<Value> result = null;
    Future<ActiveCacheResult<Value>> futureResult = null;
    synchronized ( this ) {
      result = valueMap.get( key );
      boolean shouldReload = false;
      long time = System.currentTimeMillis();
      if ( result == null || result.getTimeLoaded() + timeout < time ) {
        // Expired, we need to wait on reload
        result = null;
        shouldReload = true;
      } else if ( result.getTimeLoaded() + ( timeout / 2.0 ) < time ) {
        // Preemptively reload
        shouldReload = true;
      }
      if ( shouldReload ) {
        futureResult = loadingMap.get( key );
        if ( futureResult == null ) {
          futureResult =
              executorServiceGetter.getExecutor().submit(
                  new ActiveCacheCallable<Key, Value>( this, valueMap, loadingMap, key, loader ) );
          loadingMap.put( key, futureResult );
        }
      }
    }
    if ( result == null ) {
      result = futureResult.get();
    }
    Exception exception = result.getException();
    if ( exception != null ) {
      throw exception;
    }
    return result.getValue();
  }
}
