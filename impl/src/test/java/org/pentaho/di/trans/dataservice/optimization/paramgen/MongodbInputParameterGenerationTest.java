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


package org.pentaho.di.trans.dataservice.optimization.paramgen;

import org.pentaho.di.trans.dataservice.optimization.OptimizationImpactInfo;
import org.pentaho.di.trans.dataservice.optimization.PushDownOptimizationException;
import org.pentaho.di.trans.dataservice.optimization.mongod.MongodbPredicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pentaho.di.core.Condition;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MongodbInputParameterGenerationTest extends MongodbInputParameterGeneration {

  private static final String TEST_JSON_QUERY = "{$limit : 3}, {$match : ${paramName}}";
  private static final java.lang.String TEST_STEP_XML =
    "  <step>\n"
      + "    <name>Test Step</name>"
      + "    <json_query>" + TEST_JSON_QUERY + "</json_query>"
      + "</step>\n";
  private static final java.lang.String MOCK_STEPNAME = "MOCK_STEPNAME";

  @Mock Condition condition;

  @Mock ParameterGeneration parameterGeneration;

  @Mock StepInterface stepInterface;

  @Mock MongodbPredicate mongodbPredicate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks( this );
    when( stepInterface.getStepname() ).thenReturn( MOCK_STEPNAME );
    when( stepInterface.getStepMeta() ).thenReturn( mock( StepMeta.class ) );
    when( stepInterface.getStepMeta().getTypeId() ).thenReturn( "MongoDbInput" );
    when( stepInterface.getStepMeta().getXML() ).thenReturn( TEST_STEP_XML );
    when( mongodbPredicate.asFilterCriteria() ).thenReturn(  "mockedFilter" );
    when( parameterGeneration.getParameterName() ).thenReturn( "paramName" );
    when( parameterGeneration.setQueryParameter(
      any( String.class ), any( String.class ) ) ).thenReturn( "after optimization" );
    log = mock( LogChannelInterface.class );
  }

  @Test( expected = PushDownOptimizationException.class )
  public void testPushDownThrowsWithInvalidType() throws Exception {
    when( stepInterface.getStepMeta().getTypeId() ).thenReturn( "InvalidType" );
    pushDown( condition, parameterGeneration, stepInterface );
  }

  @Test
  public void testPushDownSetsParameter() throws Exception {
    pushDown( condition, parameterGeneration, stepInterface );
    verify( stepInterface ).setVariable( "paramName", "mockedFilter" );
  }

  @Test
  public void testPreviewWithModification() {
    OptimizationImpactInfo impact = preview( condition, parameterGeneration, stepInterface );
    assertThat( impact.getStepName(), equalTo( MOCK_STEPNAME ) );
    assertThat( impact.getQueryBeforeOptimization(),
      equalTo( TEST_JSON_QUERY ) );
    assertThat( impact.getQueryAfterOptimization(),
      equalTo( "after optimization" ) );
    assertTrue( impact.isModified() );
    assertThat( impact.getErrorMsg(), equalTo( "" ) );
  }

  @Test
  public void testPreviewWithoutModification() {
    when( parameterGeneration.getParameterName() ).thenReturn( "DifferentParamName" );
    when( parameterGeneration.setQueryParameter( TEST_JSON_QUERY, "mockedFilter" ) )
      .thenReturn( TEST_JSON_QUERY );

    OptimizationImpactInfo impact = preview( condition, parameterGeneration, stepInterface );
    assertThat( impact.getStepName(), equalTo( MOCK_STEPNAME ) );
    assertThat( impact.getQueryBeforeOptimization(),
      equalTo( TEST_JSON_QUERY ) );


    assertThat( impact.getQueryAfterOptimization(),
      equalTo( "" ) );
    assertFalse( impact.isModified() );
    assertThat( impact.getErrorMsg(), equalTo( "" ) );
  }

  @Test
  public void testPreviewWithNoQuery() throws KettleException {
    when( stepInterface.getStepMeta().getXML() ).thenReturn( "<empty/>" );
    when( parameterGeneration.setQueryParameter( "", "mockedFilter" ) )
      .thenReturn( "" );
    when( parameterGeneration.getParameterName() ).thenReturn( "paramName" );
    OptimizationImpactInfo impact = preview( condition, parameterGeneration, stepInterface );
    assertThat( impact.getStepName(), equalTo( MOCK_STEPNAME ) );
    assertThat( impact.getQueryBeforeOptimization(),
      equalTo( "<no query>" ) );
    assertThat( impact.getQueryAfterOptimization(),
      equalTo( "" ) );
    assertFalse( impact.isModified() );
    assertThat( impact.getErrorMsg(), equalTo( "" ) );
  }

  @Test
  public void testPreviewWithConversionFailure() throws KettleException {
    when( mongodbPredicate.asFilterCriteria() ).thenThrow( new PushDownOptimizationException( "FAILURE" ) );
    OptimizationImpactInfo impact = preview( condition, parameterGeneration, stepInterface );
    assertThat( impact.getStepName(), equalTo( MOCK_STEPNAME ) );
    assertThat( impact.getQueryBeforeOptimization(),
      equalTo( TEST_JSON_QUERY ) );
    assertFalse( impact.isModified() );
    assertThat( impact.getErrorMsg(), containsString( "FAILURE" )  );
  }

  @Test
  public void testQueryBeforeOptimizationWithConversionFailure() throws KettleException {
    when( mongodbPredicate.asFilterCriteria() ).thenThrow( new PushDownOptimizationException( "FAILURE" ) );
    OptimizationImpactInfo impact = preview( condition, parameterGeneration, stepInterface );
    assertThat( impact.getQueryBeforeOptimization(),
      equalTo( TEST_JSON_QUERY ) );
  }

  @Override
  protected MongodbPredicate getMongodbPredicate( Condition condition, Map<String, String> fieldMappings ) {
    return mongodbPredicate;
  }

  @Override
  protected Map<String, String> getFieldMappings( StepInterface stepInterface ) {
    return new HashMap<String, String>();
  }

}
