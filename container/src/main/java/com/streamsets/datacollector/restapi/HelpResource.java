/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.datacollector.restapi;

import com.streamsets.datacollector.main.RuntimeInfo;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

@Path("/v1/helpref")
@Api(value = "helpref")
@DenyAll
public class HelpResource {
  private final RuntimeInfo runtimeInfo;

  @Inject
  public HelpResource(RuntimeInfo runtimeInfo) {
    this.runtimeInfo = runtimeInfo;
  }

  @GET
  @ApiOperation(value = "Returns HELP Reference", response = Map.class, authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  public Response getHelpRefs() throws IOException {
    File helpRefFile = new File(runtimeInfo.getConfigDir(), "helpref.properties");
    try (Reader reader = new FileReader(helpRefFile)) {
      Properties helpRefs = new Properties();
      helpRefs.load(reader);
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(helpRefs).build();
    }
  }


}