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


package org.pentaho.di.trans.dataservice.ui;

import java.util.Collections;
import java.util.Enumeration;
import java.util.ResourceBundle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.dataservice.ui.controller.DataServiceRemapConfirmationDialogController;
import org.pentaho.di.ui.xul.KettleXulLoader;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulLoader;
import org.pentaho.ui.xul.XulRunner;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.swt.SwtXulRunner;
import org.pentaho.ui.xul.swt.tags.SwtDialog;

public class DataServiceRemapConfirmationDialog {
  public enum Action {
    REMAP,
    DELETE,
    CANCEL
  }

  public static final String XUL_DIALOG_ID = "dataservice-remap-confirmation-dialog";
  private static final String
      XUL_DIALOG_PATH =
      "org/pentaho/di/trans/dataservice/ui/xul/dataservice-remap-confirmation-dialog.xul";

  private final DataServiceRemapConfirmationDialogController controller;
  private final Shell parent;
  private static final Class<?> PKG = DataServiceRemapConfirmationDialog.class;

  public DataServiceRemapConfirmationDialog( Shell parent, DataServiceRemapConfirmationDialogController controller ) {
    this.controller = controller;
    this.parent = parent;
  }

  Document initXul( Composite parent, XulLoader xulLoader, XulRunner xulRunner ) throws XulException {
    xulLoader.setOuterContext( parent );
    xulLoader.registerClassLoader( getClass().getClassLoader() );
    XulDomContainer container = xulLoader.loadXul( XUL_DIALOG_PATH, createResourceBundle() );
    container.addEventHandler( controller );
    xulRunner.addContainer( container );
    xulRunner.initialize();
    return container.getDocumentRoot();
  }

  void open() throws KettleException {
    Document xulDocument;
    try {
      xulDocument = initXul( parent, new KettleXulLoader(), new SwtXulRunner() );
    } catch ( XulException e ) {
      throw new KettleException( "Failed to create data service remap confirmation dialog", e );
    }
    ( (SwtDialog) xulDocument.getElementById( XUL_DIALOG_ID ) ).show();
  }

  ResourceBundle createResourceBundle() {
    return new ResourceBundle() {
      @Override
      public Enumeration<String> getKeys() {
        return Collections.emptyEnumeration();
      }

      @Override
      protected Object handleGetObject( String key ) {
        return BaseMessages.getString( PKG, key );
      }
    };
  }

  public Action getAction() {
    return controller.getAction();
  }
}
