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


package org.pentaho.di.trans.dataservice.serialization;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.FluentIterable;
import org.pentaho.di.core.listeners.ContentChangedListener;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.dataservice.DataServiceMeta;
import org.pentaho.di.trans.dataservice.ui.DataServiceDelegate;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaChangeListenerInterface;
import org.pentaho.metastore.api.exceptions.MetaStoreException;

import java.util.List;

public class SynchronizationListener implements ContentChangedListener, StepMetaChangeListenerInterface {
  private static Class<TransOpenedExtensionPointPlugin> PKG = TransOpenedExtensionPointPlugin.class;
  private final DataServiceDelegate delegate;
  private final DataServiceReferenceSynchronizer synchronizer;
  private boolean prompt = true;

  public SynchronizationListener( DataServiceDelegate delegate,
                                  DataServiceReferenceSynchronizer synchronizer ) {
    this.delegate = delegate;
    this.synchronizer = synchronizer;
  }

  @Override public void contentChanged( Object parentObject ) {
  }

  @Override public void contentSafe( Object parentObject ) {
    synchronizer.sync( (TransMeta) parentObject, ( e ) -> {
      String message = e.getMessage();
      if ( prompt ) {
        if ( e instanceof DataServiceAlreadyExistsException ) {
          DataServiceMeta dataService = ( (DataServiceAlreadyExistsException) e ).getDataServiceMeta();
          delegate.syncExec( suggestEdit( dataService, message ) );
        }
        if ( e instanceof UndefinedDataServiceException ) {
          DataServiceMeta dataService = ( (UndefinedDataServiceException) e ).getDataServiceMeta();
          delegate.syncExec( suggestRemove( dataService, message ) );
        }
      }

      delegate.getLogChannel().logError( message, e );
      return null;
    } );
  }

  @Override public void onStepChange( TransMeta transMeta, StepMeta oldMeta, StepMeta newMeta ) {
    if ( Objects.equal( oldMeta.getName(), newMeta.getName() ) ) {
      return;
    }

    try {
      DataServiceMeta dataService = delegate.getDataServiceByStepName( transMeta, oldMeta.getName() );
      if ( dataService != null ) {
        dataService.setStepname( newMeta.getName() );
        delegate.save( dataService );
      }
    } catch ( MetaStoreException e ) {
      delegate.getLogChannel().logError( e.getMessage(), e );
    }
  }

  private Runnable suggestEdit( final DataServiceMeta dataService, final String message ) {
    return new Runnable() {
      @Override public void run() {
        delegate.suggestEdit( dataService, BaseMessages.getString( PKG, "Messages.SaveError.Title" ),
          message + "\n" + BaseMessages.getString( PKG, "Messages.SaveError.Edit" ) );
      }
    };
  }

  private Runnable suggestRemove( final DataServiceMeta dataService, final String message ) {
    return new Runnable() {
      @Override public void run() {
        boolean remove = delegate.showPrompt( BaseMessages.getString( PKG, "Messages.SaveError.Title" ),
          message + "\n" + BaseMessages.getString( PKG, "Messages.SaveError.Remove" ) );

        if ( remove ) {
          delegate.removeDataService( dataService );
        }
      }
    };
  }

  public void install( TransMeta transMeta ) {
    List<ContentChangedListener> listeners = transMeta.getContentChangedListeners();
    if ( FluentIterable.from( listeners ).filter( SynchronizationListener.class ).isEmpty() ) {
      transMeta.addContentChangedListener( this );
      transMeta.addStepChangeListener( this );
    }
  }

  public boolean isPrompt() {
    return prompt;
  }

  public void setPrompt( boolean prompt ) {
    this.prompt = prompt;
  }
}
