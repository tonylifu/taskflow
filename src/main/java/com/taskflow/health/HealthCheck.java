package com.taskflow.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
public class HealthCheck {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response metrics() {
        return Response.ok("UP").build();
    }
}