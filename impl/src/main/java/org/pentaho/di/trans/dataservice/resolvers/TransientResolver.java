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


package org.pentaho.di.trans.dataservice.resolvers;

import com.google.common.annotations.VisibleForTesting;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.logging.LogChannel;
import org.pentaho.di.core.logging.LogLevel;
import org.pentaho.di.core.service.PluginServiceLoader;
import org.pentaho.di.core.sql.SQL;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.RepositoryDirectoryInterface;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.dataservice.DataServiceContext;
import org.pentaho.di.trans.dataservice.DataServiceExecutor;
import org.pentaho.di.trans.dataservice.DataServiceMeta;
import org.pentaho.di.trans.dataservice.optimization.PushDownOptimizationMeta;
import org.pentaho.di.trans.dataservice.optimization.cache.ServiceCacheFactory;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.kettle.repository.locator.api.KettleRepositoryLocator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Created by bmorrise on 8/30/16.
 */
public class TransientResolver implements DataServiceResolver {

  public static final String DELIMITER = ":";
  public static final String PREFIX = "transient:";
  public static final String LOCAL = "local:";
  public static final String STREAMING = "streaming:";
  private KettleRepositoryLocator repositoryLocator;
  private DataServiceContext context;
  private ServiceCacheFactory cacheFactory;
  private LogLevel logLevel;
  private Supplier<Spoon> spoonSupplier;

  // OSGi blueprint constructor
  public TransientResolver( DataServiceContext context,
                            ServiceCacheFactory cacheFactory, final LogLevel logLevel ) {
    this( null, context, cacheFactory, logLevel, Spoon::getInstance );
    try {
      Collection<KettleRepositoryLocator> repositoryLocators = PluginServiceLoader.loadServices( KettleRepositoryLocator.class );
      repositoryLocator = repositoryLocators.stream().findFirst().get();
    } catch ( Exception e ) {
      LogChannel.GENERAL.logError( "Error getting MetastoreLocator", e );
      throw new IllegalStateException( e );
    }
  }

  @VisibleForTesting
  TransientResolver( KettleRepositoryLocator repositoryLocator, DataServiceContext context,
                            ServiceCacheFactory cacheFactory, final LogLevel logLevel, Supplier<Spoon> spoonSupplier ) {
    this.repositoryLocator = repositoryLocator;
    this.context = context;
    this.cacheFactory = cacheFactory;
    this.logLevel = logLevel;
    this.spoonSupplier = spoonSupplier;
  }


  @Override
  public DataServiceMeta getDataService( String dataServiceName ) {
    if ( !isTransient( dataServiceName ) ) {
      return null;
    }
    return createDataServiceMeta( dataServiceName );
  }

  @Override public List<String> getDataServiceNames( String dataServiceName ) {
    List<String> dataServiceNames = new ArrayList<>();
    if ( isTransient( dataServiceName ) ) {
      dataServiceNames.add( dataServiceName );
    }
    return dataServiceNames;
  }

  @Override public List<DataServiceMeta> getDataServices( String dataServiceName,
                                                          com.google.common.base.Function<Exception, Void> logger ) {
    List<DataServiceMeta> dataServiceMetas = new ArrayList<>();
    if ( isTransient( dataServiceName ) ) {
      dataServiceMetas.add( createDataServiceMeta( dataServiceName ) );
    }
    return dataServiceMetas;
  }

  @Override public DataServiceExecutor.Builder createBuilder( SQL sql ) {
    DataServiceMeta dataServiceMeta = getDataService( sql.getServiceName() );
    if ( dataServiceMeta != null ) {
      if ( dataServiceMeta.isStreaming() ) {
        return new DataServiceExecutor.Builder( sql, dataServiceMeta, context )
          .rowLimit( dataServiceMeta.getRowLimit() ).timeLimit( dataServiceMeta.getTimeLimit() );
      }
      return new DataServiceExecutor.Builder( sql, dataServiceMeta, context ).logLevel( logLevel );
    }
    return null;
  }

  private DataServiceMeta createDataServiceMeta( String dataServiceName ) {
    final String fileAndPath, rowLimit;
    String stepName;
    boolean local = false;
    boolean streaming = false;
    try {
      String[] parts = splitTransient( dataServiceName );
      fileAndPath = decode( parts[ 0 ].trim() );
      stepName = decode( parts[ 1 ].trim() );
      if ( stepName.startsWith( LOCAL + STREAMING ) || stepName.startsWith( STREAMING + LOCAL ) ) {
        local = true;
        streaming = true;
        stepName = stepName.replace( LOCAL, "" );
        stepName = stepName.replace( STREAMING, "" );
      } else if ( stepName.startsWith( LOCAL ) ) {
        local = true;
        stepName = stepName.replace( LOCAL, "" );
      } else if ( stepName.startsWith( STREAMING ) ) {
        streaming = true;
        stepName = stepName.replace( STREAMING, "" );
      }
      rowLimit = parts.length >= 3 ? decode( parts[ 2 ].trim() ) : null;
    } catch ( Exception ignored ) {
      return null;
    }

    Optional<TransMeta> transMeta;
    if ( local && spoonSupplier.get() != null && spoonSupplier.get().getActiveTransformation() != null ) {
      transMeta = Optional.of( (TransMeta) spoonSupplier.get().getActiveTransformation().realClone( false ) );
    } else {
      // Try to locate the transformation, repository first
      transMeta = Stream.of( loadFromRepository(), TransMeta::new )
        .map( loader -> loader.tryLoad( fileAndPath ).orElse( null ) )
        .filter( Objects::nonNull )
        .findFirst();
    }

    // Create a temporary Data Service
    Optional<DataServiceMeta> dataServiceMeta = transMeta.map( DataServiceMeta::new );
    if ( rowLimit != null && dataServiceMeta.isPresent() ) {
      dataServiceMeta.get().setRowLimit( Integer.parseInt( rowLimit ) );
    }
    if ( streaming && dataServiceMeta.isPresent() ) {
      dataServiceMeta.get().setStreaming( streaming );
    }
    dataServiceMeta.ifPresent( configure( dataServiceName, stepName, streaming ) );

    return dataServiceMeta.orElse( null );
  }

  private Consumer<DataServiceMeta> configure( String name, String step, boolean streaming ) {
    return dataServiceMeta -> {
      dataServiceMeta.setStepname( step );
      dataServiceMeta.setName( name );

      // In streaming there's no push down optimizations
      if ( !streaming ) {
        PushDownOptimizationMeta pushDownMeta = new PushDownOptimizationMeta();
        pushDownMeta.setStepName( step );
        pushDownMeta.setType( cacheFactory.createPushDown() );
        dataServiceMeta.setPushDownOptimizationMeta( Collections.singletonList( pushDownMeta ) );
      }
      dataServiceMeta.setUserDefined( false );
    };
  }

  private TransMetaLoader loadFromRepository() {
    return Optional.ofNullable( repositoryLocator )
      // Try to load repository
      .map( KettleRepositoryLocator::getRepository ).flatMap( Optional::ofNullable )
      // If available, attempt to load transformation
      .map( repository -> (TransMetaLoader) fileAndPath -> loadFromRepository( repository, fileAndPath ) )
      // Otherwise defer
      .orElse( fileAndPath -> null );
  }

  private static TransMeta loadFromRepository( Repository repository, String filePath ) throws KettleException {
    // this code assumes that filePath always begins with '/' or '\', and we use this as a file separator
    char fileSeparator = filePath.charAt( 0 );
    String name = filePath.substring( filePath.lastIndexOf( fileSeparator ) + 1, filePath.length() );
    String path = filePath.substring( 0, filePath.lastIndexOf( fileSeparator ) );

    RepositoryDirectoryInterface root = repository.loadRepositoryDirectoryTree();
    RepositoryDirectoryInterface rd = root.findDirectory( path );
    if ( rd == null ) {
      rd = root; // root
    }
    return repository.loadTransformation( repository.getTransformationID( name, rd ), null );
  }

  public static boolean isTransient( String dataServiceName ) {
    return dataServiceName.startsWith( PREFIX );
  }

  public static String buildTransient( String filePath, String stepName ) {
    return buildTransient( filePath, stepName, null );
  }

  public static String buildTransient( String filePath, String stepName, Integer rowLimit ) {
    return PREFIX + encode( filePath ) + DELIMITER + encode( stepName ) + ( rowLimit == null ? ""
      : DELIMITER + encode( rowLimit.toString() ) );
  }

  private static String encode( String value ) {
    return Base64.getEncoder().encodeToString( value.getBytes( StandardCharsets.UTF_8 ) );
  }

  private static String decode( String value ) {
    return new String( Base64.getDecoder().decode( value ), StandardCharsets.UTF_8 );
  }

  public String[] splitTransient( String dataServiceName ) {
    return dataServiceName.replace( PREFIX, "" ).split( DELIMITER );
  }

  @Override public List<String> getDataServiceNames() {
    return new ArrayList<>();
  }

  @Override public List<DataServiceMeta> getDataServices( com.google.common.base.Function<Exception, Void> logger ) {
    return new ArrayList<>();
  }

  private interface TransMetaLoader {
    TransMeta load( String pathAndName ) throws KettleException;

    default Optional<TransMeta> tryLoad( String pathAndName ) {
      try {
        return Optional.ofNullable( load( pathAndName ) );
      } catch ( KettleException e ) {
        return Optional.empty();
      }
    }
  }
}
