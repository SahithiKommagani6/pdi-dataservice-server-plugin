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
import org.pentaho.di.trans.dataservice.ui.controller.DataServiceRemapStepChooserDialogController;
import org.pentaho.di.ui.xul.KettleXulLoader;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulLoader;
import org.pentaho.ui.xul.XulRunner;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.swt.SwtXulRunner;
import org.pentaho.ui.xul.swt.tags.SwtDialog;

public class DataServiceRemapStepChooserDialog {
  public enum Action {
    REMAP,
    CANCEL
  }

  public static final String XUL_DIALOG_ID = "dataservice-remap-step-chooser-dialog";
  private static final String
      XUL_DIALOG_PATH =
      "org/pentaho/di/trans/dataservice/ui/xul/dataservice-remap-step-chooser-dialog.xul";

  private final DataServiceRemapStepChooserDialogController controller;
  private final Shell parent;
  private static final Class<?> PKG = DataServiceRemapStepChooserDialog.class;

  public DataServiceRemapStepChooserDialog( Shell parent, DataServiceRemapStepChooserDialogController controller )
      throws KettleException {
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

  void open() throws KettleException {
    SwtDialog dialog;
    try {
      dialog = (SwtDialog) initXul( parent, new KettleXulLoader(), new SwtXulRunner() ).getElementById( XUL_DIALOG_ID );
    } catch ( XulException xulException ) {
      throw new KettleException( "Failed to initialize DataServiceRemapStepChooserDialog.", xulException );
    }

    dialog.show();
  }

  public Action getAction() {
    return controller.getAction();
  }
}
