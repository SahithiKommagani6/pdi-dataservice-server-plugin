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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.pentaho.di.cluster.SlaveServer;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.NotePadMeta;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.logging.LogTableInterface;
import org.pentaho.di.core.plugins.JobEntryPluginType;
import org.pentaho.di.core.plugins.PluginInterface;
import org.pentaho.di.core.plugins.PluginRegistry;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.JobHopMeta;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.job.entry.JobEntryBase;
import org.pentaho.di.job.entry.JobEntryCopy;
import org.pentaho.di.job.entry.JobEntryInterface;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.RepositoryAttributeInterface;
import org.pentaho.di.repository.RepositoryElementInterface;
import org.pentaho.di.repository.RepositoryObjectType;
import org.pentaho.di.repository.StringObjectId;
import org.pentaho.di.shared.SharedObjectInterface;
import org.pentaho.di.shared.SharedObjects;
import org.pentaho.di.ui.repository.pur.services.IConnectionAclService;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFilePermission;
import org.pentaho.platform.api.repository2.unified.data.node.DataNode;
import org.pentaho.platform.api.repository2.unified.data.node.DataNodeRef;

public class JobDelegate extends AbstractDelegate implements ISharedObjectsTransformer, java.io.Serializable {

  private static final long serialVersionUID = -1006715561242639895L; /* EESOURCE: UPDATE SERIALVERUID */

  private static Class<?> PKG = JobDelegate.class; // for i18n purposes, needed by Translator2!! $NON-NLS-1$

  private static final String PROP_SHARED_FILE = "SHARED_FILE";

  public static final String PROP_USE_LOGFIELD = "USE_LOGFIELD";

  public static final String PROP_PASS_BATCH_ID = "PASS_BATCH_ID";

  public static final String PROP_USE_BATCH_ID = "USE_BATCH_ID";

  private static final String PROP_MODIFIED_DATE = "MODIFIED_DATE";

  private static final String PROP_MODIFIED_USER = "MODIFIED_USER";

  private static final String PROP_CREATED_DATE = "CREATED_DATE";

  private static final String PROP_CREATED_USER = "CREATED_USER";

  private static final String PROP_TABLE_NAME_LOG = "TABLE_NAME_LOG";

  private static final String PROP_DATABASE_LOG = "DATABASE_LOG";

  public static final String PROP_JOB_STATUS = "JOB_STATUS";

  private static final String PROP_JOB_VERSION = "JOB_VERSION";

  private static final String PROP_EXTENDED_DESCRIPTION = "EXTENDED_DESCRIPTION";

  public static final String NODE_PARAMETERS = "parameters";

  public static final String PROP_NR_PARAMETERS = "NR_PARAMETERS";

  public static final String PROP_NR_HOPS = "NR_HOPS";

  public static final String NODE_HOPS = "hops";

  public static final String NODE_CUSTOM = "custom";

  public static final String PROP_JOBENTRY_TYPE = "JOBENTRY_TYPE";

  public static final String PROP_PARALLEL = "PARALLEL";

  public static final String PROP_GUI_DRAW = "GUI_DRAW";

  public static final String PROP_GUI_LOCATION_Y = "GUI_LOCATION_Y";

  public static final String PROP_GUI_LOCATION_X = "GUI_LOCATION_X";

  // ~ Static fields/initializers ======================================================================================

  public static final String PROP_NR = "NR";

  public static final String PROP_NR_JOB_ENTRY_COPIES = "NR_JOB_ENTRY_COPIES";

  public static final String PROP_NR_NOTES = "NR_NOTES";

  private static final String NODE_JOB = "job";

  public static final String NODE_NOTES = "notes";

  private static final String NOTE_PREFIX = "__NOTE__#";

  private static final String PROP_XML = "XML";

  public static final String NODE_ENTRIES = "entries";

  private static final String EXT_JOB_ENTRY_COPY = ".kjc";

  private static final String JOB_HOP_FROM = "JOB_HOP_FROM";

  private static final String JOB_HOP_FROM_NR = "JOB_HOP_FROM_NR";

  private static final String JOB_HOP_TO = "JOB_HOP_TO";

  private static final String JOB_HOP_TO_NR = "JOB_HOP_TO_NR";

  private static final String JOB_HOP_ENABLED = "JOB_HOP_ENABLED";

  private static final String JOB_HOP_EVALUATION = "JOB_HOP_EVALUATION";

  private static final String JOB_HOP_UNCONDITIONAL = "JOB_HOP_UNCONDITIONAL";

  private static final String JOB_HOP_PREFIX = "__JOB_HOP__#";

  private static final String PARAM_PREFIX = "__PARAM_#";

  private static final String PARAM_KEY = "KEY";

  private static final String PARAM_DESC = "DESC";

  private static final String PARAM_DEFAULT = "DEFAULT";

  private static final String PROP_LOG_SIZE_LIMIT = "LOG_SIZE_LIMIT";

  // private static Class<?> PKG = JobDelegate.class; // for i18n purposes, needed by Translator2!! $NON-NLS-1$

  // ~ Instance fields =================================================================================================

  private final Repository repo;

  private final IConnectionAclService unifiedRepositoryConnectionAclService;

  // ~ Constructors ====================================================================================================

  public JobDelegate( final Repository repo, final IUnifiedRepository pur ) {
    super();
    this.repo = repo;
    unifiedRepositoryConnectionAclService = new UnifiedRepositoryConnectionAclService( pur );
  }

  // ~ Methods =========================================================================================================
  @SuppressWarnings( "unchecked" )
  public SharedObjects loadSharedObjects( final RepositoryElementInterface element,
      final Map<RepositoryObjectType, List<? extends SharedObjectInterface>> sharedObjectsByType )
    throws KettleException {
    JobMeta jobMeta = (JobMeta) element;
    jobMeta.setSharedObjects( jobMeta.readSharedObjects() );

    // Repository objects take priority so let's overwrite them...
    //
    readDatabases( jobMeta, true, (List<DatabaseMeta>) sharedObjectsByType.get( RepositoryObjectType.DATABASE ) );
    readSlaves( jobMeta, true, (List<SlaveServer>) sharedObjectsByType.get( RepositoryObjectType.SLAVE_SERVER ) );

    return jobMeta.getSharedObjects();
  }

  public void saveSharedObjects( final RepositoryElementInterface element, final String versionComment )
    throws KettleException {
    JobMeta jobMeta = (JobMeta) element;
    // Now store the databases in the job.
    // Only store if the database has actually changed or doesn't have an object ID (imported)
    //
    for ( DatabaseMeta databaseMeta : jobMeta.getDatabases() ) {
      if ( databaseMeta.hasChanged() || databaseMeta.getObjectId() == null ) {
        if ( databaseMeta.getObjectId() == null
            || unifiedRepositoryConnectionAclService.hasAccess( databaseMeta.getObjectId(),
                RepositoryFilePermission.WRITE ) ) {
          repo.save( databaseMeta, versionComment, null );
        } else {
          log.logError( BaseMessages.getString( PKG, "PurRepository.ERROR_0004_DATABASE_UPDATE_ACCESS_DENIED",
              databaseMeta.getName() ) );
        }
      }
    }

    // Store the slave server
    //
    for ( SlaveServer slaveServer : jobMeta.getSlaveServers() ) {
      if ( slaveServer.hasChanged() || slaveServer.getObjectId() == null ) {
        repo.save( slaveServer, versionComment, null );
      }
    }

  }

  public RepositoryElementInterface dataNodeToElement( final DataNode rootNode ) throws KettleException {
    JobMeta jobMeta = new JobMeta();
    dataNodeToElement( rootNode, jobMeta );
    return jobMeta;
  }

  public void dataNodeToElement( final DataNode rootNode, final RepositoryElementInterface element )
    throws KettleException {

    JobMeta jobMeta = (JobMeta) element;

    jobMeta.setSharedObjectsFile( getString( rootNode, PROP_SHARED_FILE ) );

    // Keep a unique list of job entries to facilitate in the loading.
    //
    List<JobEntryInterface> jobentries = new ArrayList<JobEntryInterface>();

    // Read the job entry copies
    //
    DataNode entriesNode = rootNode.getNode( NODE_ENTRIES );
    int nrCopies = (int) entriesNode.getProperty( PROP_NR_JOB_ENTRY_COPIES ).getLong();

    // read the copies...
    //
    for ( DataNode copyNode : entriesNode.getNodes() ) {
      // String name = copyNode.getName();

      // Read the entry...
      //
      JobEntryInterface jobEntry = readJobEntry( copyNode, jobMeta, jobentries );

      JobEntryCopy copy = new JobEntryCopy( jobEntry );

      copy.setName( getString( copyNode, PROP_NAME ) );
      copy.setDescription( getString( copyNode, PROP_DESCRIPTION ) );
      copy.setObjectId( new StringObjectId( copyNode.getId().toString() ) );

      copy.setNr( (int) copyNode.getProperty( PROP_NR ).getLong() );
      int x = (int) copyNode.getProperty( PROP_GUI_LOCATION_X ).getLong();
      int y = (int) copyNode.getProperty( PROP_GUI_LOCATION_Y ).getLong();
      copy.setLocation( x, y );
      copy.setDrawn( copyNode.getProperty( PROP_GUI_DRAW ).getBoolean() );
      copy.setLaunchingInParallel( copyNode.getProperty( PROP_PARALLEL ).getBoolean() );

      // Also save the step group attributes map
      //
      if ( jobEntry instanceof JobEntryBase ) {
        AttributesMapUtil.loadAttributesMap( copyNode, (JobEntryBase) jobEntry );
      }
      AttributesMapUtil.loadAttributesMap( copyNode, copy );

      jobMeta.getJobCopies().add( copy );
    }

    if ( jobMeta.getJobCopies().size() != nrCopies ) {
      throw new KettleException( "The number of job entry copies read [" + jobMeta.getJobCopies().size()
          + "] was not the number we expected [" + nrCopies + "]" );
    }

    // Read the notes...
    //
    DataNode notesNode = rootNode.getNode( NODE_NOTES );
    int nrNotes = (int) notesNode.getProperty( PROP_NR_NOTES ).getLong();
    for ( DataNode noteNode : notesNode.getNodes() ) {
      // String name = noteNode.getName();
      String xml = getString( noteNode, PROP_XML );
      jobMeta
          .addNote( new NotePadMeta( XMLHandler.getSubNode( XMLHandler.loadXMLString( xml ), NotePadMeta.XML_TAG ) ) );
    }
    if ( jobMeta.nrNotes() != nrNotes ) {
      throw new KettleException( "The number of notes read [" + jobMeta.nrNotes()
          + "] was not the number we expected [" + nrNotes + "]" );
    }

    // Read the hops...
    //
    DataNode hopsNode = rootNode.getNode( NODE_HOPS );
    int nrHops = (int) hopsNode.getProperty( PROP_NR_HOPS ).getLong();
    for ( DataNode hopNode : hopsNode.getNodes() ) {
      // String name = hopNode.getName();
      String copyFromName = getString( hopNode, JOB_HOP_FROM );
      int copyFromNr = (int) hopNode.getProperty( JOB_HOP_FROM_NR ).getLong();
      String copyToName = getString( hopNode, JOB_HOP_TO );
      int copyToNr = (int) hopNode.getProperty( JOB_HOP_TO_NR ).getLong();

      boolean enabled = true;
      if ( hopNode.hasProperty( JOB_HOP_ENABLED ) ) {
        enabled = hopNode.getProperty( JOB_HOP_ENABLED ).getBoolean();
      }

      boolean evaluation = true;
      if ( hopNode.hasProperty( JOB_HOP_EVALUATION ) ) {
        evaluation = hopNode.getProperty( JOB_HOP_EVALUATION ).getBoolean();
      }

      boolean unconditional = true;
      if ( hopNode.hasProperty( JOB_HOP_UNCONDITIONAL ) ) {
        unconditional = hopNode.getProperty( JOB_HOP_UNCONDITIONAL ).getBoolean();
      }

      JobEntryCopy copyFrom = jobMeta.findJobEntry( copyFromName, copyFromNr, true );
      JobEntryCopy copyTo = jobMeta.findJobEntry( copyToName, copyToNr, true );

      JobHopMeta jobHopMeta = new JobHopMeta( copyFrom, copyTo );
      jobHopMeta.setEnabled( enabled );
      jobHopMeta.setEvaluation( evaluation );
      jobHopMeta.setUnconditional( unconditional );
      jobMeta.addJobHop( jobHopMeta );

    }
    if ( jobMeta.nrJobHops() != nrHops ) {
      throw new KettleException( "The number of hops read [" + jobMeta.nrJobHops()
          + "] was not the number we expected [" + nrHops + "]" );
    }

    // Load the details at the end, to make sure we reference the databases correctly, etc.
    //
    loadJobMetaDetails( rootNode, jobMeta );

    jobMeta.eraseParameters();
    DataNode paramsNode = rootNode.getNode( NODE_PARAMETERS );
    int count = (int) paramsNode.getProperty( PROP_NR_PARAMETERS ).getLong();
    for ( int idx = 0; idx < count; idx++ ) {
      DataNode paramNode = paramsNode.getNode( PARAM_PREFIX + idx );
      String key = getString( paramNode, PARAM_KEY );
      String def = getString( paramNode, PARAM_DEFAULT );
      String desc = getString( paramNode, PARAM_DESC );
      jobMeta.addParameterDefinition( key, def, desc );
    }
  }

  protected void loadJobMetaDetails( DataNode rootNode, JobMeta jobMeta ) throws KettleException {
    try {
      jobMeta.setExtendedDescription( getString( rootNode, PROP_EXTENDED_DESCRIPTION ) );
      jobMeta.setJobversion( getString( rootNode, PROP_JOB_VERSION ) );
      jobMeta.setJobstatus( (int) rootNode.getProperty( PROP_JOB_STATUS ).getLong() );
      jobMeta.getJobLogTable().setTableName( getString( rootNode, PROP_TABLE_NAME_LOG ) );

      jobMeta.setCreatedUser( getString( rootNode, PROP_CREATED_USER ) );
      jobMeta.setCreatedDate( getDate( rootNode, PROP_CREATED_DATE ) );

      jobMeta.setModifiedUser( getString( rootNode, PROP_MODIFIED_USER ) );
      jobMeta.setModifiedDate( getDate( rootNode, PROP_MODIFIED_DATE ) );

      if ( rootNode.hasProperty( PROP_DATABASE_LOG ) ) {
        String id = rootNode.getProperty( PROP_DATABASE_LOG ).getRef().getId().toString();
        DatabaseMeta conn = ( DatabaseMeta.findDatabase( jobMeta.getDatabases(), new StringObjectId( id ) ) );
        jobMeta.getJobLogTable().setConnectionName( conn.getName() );
      }

      jobMeta.getJobLogTable().setBatchIdUsed( rootNode.getProperty( PROP_USE_BATCH_ID ).getBoolean() );
      jobMeta.setBatchIdPassed( rootNode.getProperty( PROP_PASS_BATCH_ID ).getBoolean() );
      jobMeta.getJobLogTable().setLogFieldUsed( rootNode.getProperty( PROP_USE_LOGFIELD ).getBoolean() );

      jobMeta.getJobLogTable().setLogSizeLimit( getString( rootNode, PROP_LOG_SIZE_LIMIT ) );

      // Load the logging tables too..
      //
      RepositoryAttributeInterface attributeInterface = new PurRepositoryAttribute( rootNode, jobMeta.getDatabases() );
      for ( LogTableInterface logTable : jobMeta.getLogTables() ) {
        logTable.loadFromRepository( attributeInterface );
      }

      // Load the attributes map
      //
      AttributesMapUtil.loadAttributesMap( rootNode, jobMeta );

    } catch ( Exception e ) {
      throw new KettleException( "Error loading job details", e );
    }

  }

  protected JobEntryInterface readJobEntry( DataNode copyNode, JobMeta jobMeta, List<JobEntryInterface> jobentries )
    throws KettleException {
    try {
      String name = getString( copyNode, PROP_NAME );
      for ( JobEntryInterface entry : jobentries ) {
        if ( entry.getName().equalsIgnoreCase( name ) ) {
          return entry; // already loaded!
        }
      }

      // load the entry from the node
      //
      String typeId = getString( copyNode, PROP_JOBENTRY_TYPE );

      PluginRegistry registry = PluginRegistry.getInstance();
      PluginInterface jobPlugin = registry.findPluginWithId( JobEntryPluginType.class, typeId );
      JobEntryInterface entry = (JobEntryInterface) registry.loadClass( jobPlugin );
      entry.setName( name );
      entry.setDescription( getString( copyNode, PROP_DESCRIPTION ) );
      entry.setObjectId( new StringObjectId( copyNode.getId().toString() ) );
      RepositoryProxy proxy = new RepositoryProxy( copyNode.getNode( NODE_CUSTOM ) );

      compatibleJobEntryLoadRep( entry, proxy, null, jobMeta.getDatabases(), jobMeta.getSlaveServers() );
      entry.loadRep( proxy, proxy.getMetaStore(), null, jobMeta.getDatabases(), jobMeta.getSlaveServers() );
      jobentries.add( entry );
      return entry;
    } catch ( Exception e ) {
      throw new KettleException( "Unable to read job entry interface information from repository", e );
    }
  }

  @SuppressWarnings( "deprecation" )
  private void compatibleJobEntryLoadRep( JobEntryInterface jobEntry, Repository repository, ObjectId id_jobentry_type,
      List<DatabaseMeta> databases, List<SlaveServer> slaveServers ) throws KettleException {

    jobEntry.loadRep( repository, id_jobentry_type, databases, slaveServers );

  }

  public DataNode elementToDataNode( final RepositoryElementInterface element ) throws KettleException {
    JobMeta jobMeta = (JobMeta) element;
    DataNode rootNode = new DataNode( NODE_JOB );

    // Save the notes
    //
    DataNode notesNode = rootNode.addNode( NODE_NOTES );

    notesNode.setProperty( PROP_NR_NOTES, jobMeta.nrNotes() );
    for ( int i = 0; i < jobMeta.nrNotes(); i++ ) {
      NotePadMeta note = jobMeta.getNote( i );
      DataNode noteNode = notesNode.addNode( NOTE_PREFIX + i );
      noteNode.setProperty( PROP_XML, note.getXML() );
    }

    //
    // Save the job entry copies
    //
    if ( log.isDetailed() ) {
      log.logDetailed( toString(), "Saving " + jobMeta.nrJobEntries() + " Job enty copies to repository..." ); //$NON-NLS-1$ //$NON-NLS-2$
    }
    DataNode entriesNode = rootNode.addNode( NODE_ENTRIES );
    entriesNode.setProperty( PROP_NR_JOB_ENTRY_COPIES, jobMeta.nrJobEntries() );
    for ( int i = 0; i < jobMeta.nrJobEntries(); i++ ) {

      JobEntryCopy copy = jobMeta.getJobEntry( i );
      JobEntryInterface entry = copy.getEntry();

      // Create a new node for each entry...
      //
      DataNode copyNode = entriesNode.addNode( sanitizeNodeName( copy.getName() ) + "_" + ( i + 1 ) //$NON-NLS-1$
          + EXT_JOB_ENTRY_COPY );

      copyNode.setProperty( PROP_NAME, copy.getName() );
      copyNode.setProperty( PROP_DESCRIPTION, copy.getDescription() );

      copyNode.setProperty( PROP_NR, copy.getNr() );
      copyNode.setProperty( PROP_GUI_LOCATION_X, copy.getLocation().x );
      copyNode.setProperty( PROP_GUI_LOCATION_Y, copy.getLocation().y );
      copyNode.setProperty( PROP_GUI_DRAW, copy.isDrawn() );
      copyNode.setProperty( PROP_PARALLEL, copy.isLaunchingInParallel() );

      // Also save the step group attributes map
      //
      if ( entry instanceof JobEntryBase ) {
        AttributesMapUtil.saveAttributesMap( copyNode, (JobEntryBase) entry );
      }
      AttributesMapUtil.saveAttributesMap( copyNode, copy );

      // Save the entry information here as well, for completeness.
      // TODO: since this slightly stores duplicate information, figure out how to store this separately.
      //
      copyNode.setProperty( PROP_JOBENTRY_TYPE, entry.getPluginId() );
      DataNode customNode = new DataNode( NODE_CUSTOM );
      RepositoryProxy proxy = new RepositoryProxy( customNode );
      entry.saveRep( proxy, proxy.getMetaStore(), null );
      compatibleEntrySaveRep( entry, proxy, null );

      copyNode.addNode( customNode );
    }

    // Finally, save the hops
    //
    DataNode hopsNode = rootNode.addNode( NODE_HOPS );
    hopsNode.setProperty( PROP_NR_HOPS, jobMeta.nrJobHops() );
    for ( int i = 0; i < jobMeta.nrJobHops(); i++ ) {
      JobHopMeta hop = jobMeta.getJobHop( i );
      DataNode hopNode = hopsNode.addNode( JOB_HOP_PREFIX + i );

      hopNode.setProperty( JOB_HOP_FROM, hop.getFromEntry().getName() );
      hopNode.setProperty( JOB_HOP_FROM_NR, hop.getFromEntry().getNr() );
      hopNode.setProperty( JOB_HOP_TO, hop.getToEntry().getName() );
      hopNode.setProperty( JOB_HOP_TO_NR, hop.getToEntry().getNr() );
      hopNode.setProperty( JOB_HOP_ENABLED, hop.isEnabled() );
      hopNode.setProperty( JOB_HOP_EVALUATION, hop.getEvaluation() );
      hopNode.setProperty( JOB_HOP_UNCONDITIONAL, hop.isUnconditional() );
    }

    String[] paramKeys = jobMeta.listParameters();
    DataNode paramsNode = rootNode.addNode( NODE_PARAMETERS );
    paramsNode.setProperty( PROP_NR_PARAMETERS, paramKeys == null ? 0 : paramKeys.length );

    for ( int idx = 0; idx < paramKeys.length; idx++ ) {
      DataNode paramNode = paramsNode.addNode( PARAM_PREFIX + idx );
      String key = paramKeys[idx];
      String description = jobMeta.getParameterDescription( paramKeys[idx] );
      String defaultValue = jobMeta.getParameterDefault( paramKeys[idx] );

      paramNode.setProperty( PARAM_KEY, key != null ? key : "" ); //$NON-NLS-1$
      paramNode.setProperty( PARAM_DEFAULT, defaultValue != null ? defaultValue : "" ); //$NON-NLS-1$
      paramNode.setProperty( PARAM_DESC, description != null ? description : "" ); //$NON-NLS-1$
    }

    // Let's not forget to save the details of the transformation itself.
    // This includes logging information, parameters, etc.
    //
    saveJobDetails( rootNode, jobMeta );

    return rootNode;
  }

  @SuppressWarnings( "deprecation" )
  private void compatibleEntrySaveRep( JobEntryInterface entry, Repository repository, ObjectId id_job )
    throws KettleException {
    entry.saveRep( repository, id_job );
  }

  private void saveJobDetails( DataNode rootNode, JobMeta jobMeta ) throws KettleException {
    rootNode.setProperty( PROP_EXTENDED_DESCRIPTION, jobMeta.getExtendedDescription() );
    rootNode.setProperty( PROP_JOB_VERSION, jobMeta.getJobversion() );
    rootNode.setProperty( PROP_JOB_STATUS, jobMeta.getJobstatus() < 0 ? -1L : jobMeta.getJobstatus() );

    if ( jobMeta.getJobLogTable().getDatabaseMeta() != null ) {
      DataNodeRef ref = new DataNodeRef( jobMeta.getJobLogTable().getDatabaseMeta().getObjectId().getId() );
      rootNode.setProperty( PROP_DATABASE_LOG, ref );
    }
    rootNode.setProperty( PROP_TABLE_NAME_LOG, jobMeta.getJobLogTable().getTableName() );

    rootNode.setProperty( PROP_CREATED_USER, jobMeta.getCreatedUser() );
    rootNode.setProperty( PROP_CREATED_DATE, jobMeta.getCreatedDate() );
    rootNode.setProperty( PROP_MODIFIED_USER, jobMeta.getModifiedUser() );
    rootNode.setProperty( PROP_MODIFIED_DATE, jobMeta.getModifiedDate() );
    rootNode.setProperty( PROP_USE_BATCH_ID, jobMeta.getJobLogTable().isBatchIdUsed() );
    rootNode.setProperty( PROP_PASS_BATCH_ID, jobMeta.isBatchIdPassed() );
    rootNode.setProperty( PROP_USE_LOGFIELD, jobMeta.getJobLogTable().isLogFieldUsed() );
    rootNode.setProperty( PROP_SHARED_FILE, jobMeta.getSharedObjectsFile() );

    rootNode.setProperty( PROP_LOG_SIZE_LIMIT, jobMeta.getJobLogTable().getLogSizeLimit() );

    // Save the logging tables too..
    //
    RepositoryAttributeInterface attributeInterface = new PurRepositoryAttribute( rootNode, jobMeta.getDatabases() );
    for ( LogTableInterface logTable : jobMeta.getLogTables() ) {
      logTable.saveToRepository( attributeInterface );
    }

    // Load the attributes map
    //
    AttributesMapUtil.saveAttributesMap( rootNode, jobMeta );
  }

  /**
   * Insert all the databases from the repository into the JobMeta object, overwriting optionally
   * 
   * @param JobMeta
   *          The transformation to load into.
   * @param overWriteShared
   *          if an object with the same name exists, overwrite
   */
  protected void readDatabases( JobMeta jobMeta, boolean overWriteShared, List<DatabaseMeta> databaseMetas ) {
    for ( DatabaseMeta databaseMeta : databaseMetas ) {
      if ( overWriteShared || jobMeta.findDatabase( databaseMeta.getName() ) == null ) {
        if ( databaseMeta.getName() != null ) {
          databaseMeta.shareVariablesWith( jobMeta );
          jobMeta.addOrReplaceDatabase( databaseMeta );
          if ( !overWriteShared )
            databaseMeta.setChanged( false );
        }
      }
    }
    jobMeta.clearChanged();
  }

  /**
   * Add the slave servers in the repository to this job if they are not yet present.
   * 
   * @param JobMeta
   *          The job to load into.
   * @param overWriteShared
   *          if an object with the same name exists, overwrite
   */
  protected void readSlaves( JobMeta jobMeta, boolean overWriteShared, List<SlaveServer> slaveServers ) {
    for ( SlaveServer slaveServer : slaveServers ) {
      if ( overWriteShared || jobMeta.findSlaveServer( slaveServer.getName() ) == null ) {
        if ( !Const.isEmpty( slaveServer.getName() ) ) {
          slaveServer.shareVariablesWith( jobMeta );
          jobMeta.addOrReplaceSlaveServer( slaveServer );
          if ( !overWriteShared )
            slaveServer.setChanged( false );
        }
      }
    }
  }

}
