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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.metrics.MetricsDuration;
import org.pentaho.di.core.metrics.MetricsUtil;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.dataservice.DataServiceContext;
import org.pentaho.di.trans.dataservice.DataServiceMeta;
import org.pentaho.di.trans.dataservice.ui.controller.DataServiceTestController;
import org.pentaho.di.trans.dataservice.ui.model.DataServiceTestModel;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.xul.KettleXulLoader;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulRunner;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.swt.SwtXulLoader;
import org.pentaho.ui.xul.swt.SwtXulRunner;
import org.pentaho.ui.xul.swt.tags.SwtDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;

public class DataServiceTestDialog {
  private static final Logger LOG = LoggerFactory.getLogger( DataServiceTestDialog.class );
  private static final Class<?> PKG = DataServiceTestDialog.class;
  private static final String XUL_PATH = "org/pentaho/di/trans/dataservice/ui/xul/dataservice-test-dialog.xul";
  private static final String XUL_DIALOG_ID = "dataservice-test-dialog";
  private static final String GENTRANS_LOG_XUL_ID = "genTrans-log-tab";
  private static final String GENTRANS_METRICS_XUL_ID = "genTrans-metrics-tab";
  private static final String SVCTRANS_LOG_XUL_ID = "serviceTrans-log-tab";
  private static final String SVCTRANS_METRICS_XUL_ID = "serviceTrans-metrics-tab";
  private static final String QUERY_RESULTS_XUL_ID = "results-tab";

  private final DataServiceTestModel model = new DataServiceTestModel();
  private final DataServiceTestController dataServiceTestController;
  private final DataServiceTestResults resultsView;
  private final DataServiceTestLogBrowser serviceTransLogBrowser;
  private final DataServiceTestLogBrowser genTransLogBrowser;
  private final DataServiceTestMetrics serviceTransMetrics;
  private final DataServiceTestMetrics genTransMetrics;
  private final Document xulDocument;
  private final SwtDialog dialog;

  private static final Class<?> CLZ = DataServiceTestDialog.class;
  private final ResourceBundle resourceBundle = new ResourceBundle() {
    @Override
    public Enumeration<String> getKeys() {
      return Collections.emptyEnumeration();
    }

    @Override
    protected Object handleGetObject( String key ) {
      return BaseMessages.getString( CLZ, key );
    }
  };

  public DataServiceTestDialog( Shell parent, DataServiceMeta dataService, DataServiceContext context )
    throws KettleException {
    try {
      dataServiceTestController = new DataServiceTestController( model, dataService, context );
    } catch ( KettleException ke ) {
      new ErrorDialog( parent, BaseMessages.getString( PKG, "DataServiceTest.TestDataServiceError.Title" ),
        BaseMessages.getString( PKG, "DataServiceTest.TestDataServiceError.Message" ), ke );
      throw ke;
    }
    xulDocument = initXul( parent );
    resultsView = initDataServiceResultsView( dataService );
    serviceTransLogBrowser = new DataServiceTestLogBrowser( getComposite( SVCTRANS_LOG_XUL_ID ) );
    serviceTransMetrics = new DataServiceTestMetrics( getComposite( SVCTRANS_METRICS_XUL_ID ) );
    genTransLogBrowser = new DataServiceTestLogBrowser( getComposite( GENTRANS_LOG_XUL_ID ) );
    genTransMetrics = new DataServiceTestMetrics( getComposite( GENTRANS_METRICS_XUL_ID ) );
    dialog = (SwtDialog) xulDocument.getElementById( XUL_DIALOG_ID );

    // Add the Ctrl-Enter listener to the main UI components
    Text textbox = (Text) ( xulDocument.getElementById( "sql-textbox" ).getManagedObject() );
    if ( textbox != null ) {
      textbox.addListener( SWT.KeyDown, new SqlKeyListener( dataServiceTestController, true ) );
    }

    CTabFolder tabFolder = (CTabFolder) ( xulDocument.getElementById( "tabbox" ).getManagedObject() );
    if ( tabFolder != null ) {
      tabFolder.addListener( SWT.KeyDown, new SqlKeyListener( dataServiceTestController, false ) );
    }

    attachCallback();
  }

  private Composite getComposite( String elementId ) {
    return (Composite) xulDocument.getElementById( elementId ).getManagedObject();
  }

  public void open() {
    dialog.show();
  }

  public void close() {
    genTransLogBrowser.dispose();
    genTransMetrics.dispose();
    serviceTransLogBrowser.dispose();
    serviceTransMetrics.dispose();
    dialog.dispose();
  }

  private DataServiceTestResults initDataServiceResultsView( DataServiceMeta dataService ) {

    Composite results = getComposite( QUERY_RESULTS_XUL_ID );
    results.setLayout( new FillLayout() );

    return new DataServiceTestResults( dataService, results );
  }

  private void attachCallback() {
    dataServiceTestController.setCallback(
      new DataServiceTestCallback() {
        @Override
        public void onExecuteComplete() {
          resultsView.setRowMeta( model.getResultRowMeta() );
          resultsView.load( model.getResultRows(), new SqlKeyListener( dataServiceTestController, false ) );
          updateMetrics();
        }

        @Override
        public void onLogChannelUpdate() {
          updateLogChannel();
        }

        @Override
        public void onClose() {
          close();
        }

        @Override
        public void onUpdate( List<Object[]> rows ) {
          resultsView.updateTableView( model.getResultRows() );
          updateMetrics();
        }

        private void updateMetrics() {
          List<MetricsDuration> metrics =
            MetricsUtil.getAllDurations( model.getGenTransLogChannel().getLogChannelId() );
          DataServiceTestDialog.this.genTransMetrics.display( metrics );
          LogChannelInterface serviceTransLogChannel = model.getServiceTransLogChannel();
          if ( serviceTransLogChannel != null ) {
            serviceTransMetrics.display( MetricsUtil.getAllDurations( serviceTransLogChannel.getLogChannelId() ) );
          }
        }
      }
    );
  }

  private void updateLogChannel() {
    if ( model.getServiceTransLogChannel() != null ) {
      serviceTransLogBrowser.attachToLogBrowser( model.getServiceTransLogChannel() );
    }
    if ( model.getGenTransLogChannel() != null ) {
      genTransLogBrowser.attachToLogBrowser( model.getGenTransLogChannel() );
    }
  }

  private Document initXul( Composite parent ) throws KettleException {
    try {
      SwtXulLoader swtLoader = new KettleXulLoader();
      swtLoader.setOuterContext( parent );
      swtLoader.registerClassLoader( getClass().getClassLoader() );
      XulDomContainer container = swtLoader.loadXul( XUL_PATH, resourceBundle );
      container.addEventHandler( dataServiceTestController );

      final XulRunner runner = new SwtXulRunner();
      runner.addContainer( container );
      runner.initialize();
      return container.getDocumentRoot();
    } catch ( XulException xulException ) {
      throw new KettleException( "Failed to initialize DataServicesTestDialog.",
        xulException );
    }
  }

  static class SqlKeyListener implements Listener {

    DataServiceTestController dataServiceTestController;
    boolean consume = false;

    public SqlKeyListener( DataServiceTestController dataServiceTestController, boolean consume ) {
      this.dataServiceTestController = dataServiceTestController;
      this.consume = consume;
    }

    @Override
    public void handleEvent( Event e ) {
      if ( ( e.keyCode == SWT.CR && ( e.stateMask & SWT.CONTROL ) != 0 ) && dataServiceTestController != null ) {
        try {
          dataServiceTestController.executeSql();
        } catch ( KettleException e1 ) {
          LOG.error( "Unexpected error trying to execute SQL statement: {}", e1.getMessage(), e1 );
        }
        e.doit = !consume;
      }
    }
  }
}
