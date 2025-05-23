/*******************************************************************************
 * Copyright (c) 2017 Istio Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package application.rest;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.StringReader;

@Path("/")
public class LibertyRestEndpoint extends Application {

    private final static Boolean ratings_enabled = Boolean.valueOf(System.getenv("ENABLE_RATINGS"));
    private final static String star_color = System.getenv("STAR_COLOR") == null ? "black" : System.getenv("STAR_COLOR");
    private final static String services_domain = System.getenv("SERVICES_DOMAIN") == null ? "" : ("." + System.getenv("SERVICES_DOMAIN"));
    private final static String ratings_hostname = System.getenv("RATINGS_HOSTNAME") == null ? "ratings" : System.getenv("RATINGS_HOSTNAME");
    private final static String ratings_port = System.getenv("RATINGS_SERVICE_PORT") == null ? "9080" : System.getenv("RATINGS_SERVICE_PORT");
    private final static String ratings_service = String.format("http://%s%s:%s/ratings", ratings_hostname, services_domain, ratings_port);
    private final static String pod_hostname = System.getenv("HOSTNAME");
    private final static String clustername = System.getenv("CLUSTER_NAME");
    // HTTP headers to propagate for distributed tracing are documented at
    // https://istio.io/docs/tasks/telemetry/distributed-tracing/overview/#trace-context-propagation
    private final static String[] headers_to_propagate = {
        // All applications should propagate x-request-id. This header is
        // included in access log statements and is used for consistent trace
        // sampling and log sampling decisions in Istio.
        "x-request-id",

        // Lightstep tracing header. Propagate this if you use lightstep tracing
        // in Istio (see
        // https://istio.io/latest/docs/tasks/observability/distributed-tracing/lightstep/)
        // Note: this should probably be changed to use B3 or W3C TRACE_CONTEXT.
        // Lightstep recommends using B3 or TRACE_CONTEXT and most application
        // libraries from lightstep do not support x-ot-span-context.
        "x-ot-span-context",

        // Datadog tracing header. Propagate these headers if you use Datadog
        // tracing.
        "x-datadog-trace-id",
        "x-datadog-parent-id",
        "x-datadog-sampling-priority",

        // W3C Trace Context. Compatible with OpenCensusAgent and Stackdriver Istio
        // configurations.
        "traceparent",
        "tracestate",

        // Cloud trace context. Compatible with OpenCensusAgent and Stackdriver Istio
        // configurations.
        "x-cloud-trace-context",

        // Grpc binary trace context. Compatible with OpenCensusAgent nad
        // Stackdriver Istio configurations.
        "grpc-trace-bin",

        // b3 trace headers. Compatible with Zipkin, OpenCensusAgent, and
        // Stackdriver Istio configurations. Commented out since they are
        // propagated by the OpenTracing tracer above.
        "x-b3-traceid",
        "x-b3-spanid",
        "x-b3-parentspanid",
        "x-b3-sampled",
        "x-b3-flags",

        // SkyWalking trace headers.
        "sw8",

        // Application-specific headers to forward.
        "end-user",
        "user-agent",

        // Context and session specific headers
        "cookie",
        "authorization",
        "jwt",
    };

    private String getJsonResponse(String productId, int starsReviewer1, int starsReviewer2) {
    	String result = "{";
    	result += "\"id\": \"" + productId + "\",";
        result += "\"podname\": \"" + pod_hostname + "\",";
        result += "\"clustername\": \"" + clustername + "\",";
    	result += "\"reviews\": [";

    	// reviewer 1:
    	result += "{";
    	result += "  \"reviewer\": \"Reviewer1\",";
    	result += "  \"text\": \"A science-fiction masterpiece. Nolan executes a marvelous direction that slowly but efficiently puts in place a dark world creating a necessity to save humanity. Add to that great performances from Nolan and Hathaway plus a great score from Hans Zimmer. The result is on the best science-fiction movies of all time.\"";
      if (ratings_enabled) {
        if (starsReviewer1 != -1) {
          result += ", \"rating\": {\"stars\": " + starsReviewer1 + ", \"color\": \"" + star_color + "\"}";
        }
        else {
          result += ", \"rating\": {\"error\": \"Ratings service is currently unavailable\"}";
        }
      }
    	result += "},";

    	// reviewer 2:
    	result += "{";
    	result += "  \"reviewer\": \"Reviewer2\",";
    	result += "  \"text\": \"This is first time ever that i claped to a movie, and i mean EVER. This is just incredible. There's no other movie as close. It could easily be the best I've ever seen. Just wow....\"";
      if (ratings_enabled) {
        if (starsReviewer2 != -1) {
          result += ", \"rating\": {\"stars\": " + starsReviewer2 + ", \"color\": \"" + star_color + "\"}";
        }
        else {
          result += ", \"rating\": {\"error\": \"Ratings service is currently unavailable\"}";
        }
      }
    	result += "}";

    	result += "]";
    	result += "}";

    	return result;
    }

    private JsonObject getRatings(String productId, HttpHeaders requestHeaders) {
      ClientBuilder cb = ClientBuilder.newBuilder();
      Integer timeout = star_color.equals("black") ? 10000 : 2500;
      cb.property("com.ibm.ws.jaxrs.client.connection.timeout", timeout);
      cb.property("com.ibm.ws.jaxrs.client.receive.timeout", timeout);
      Client client = cb.build();
      WebTarget ratingsTarget = client.target(ratings_service + "/" + productId);
      Invocation.Builder builder = ratingsTarget.request(MediaType.APPLICATION_JSON);
      for (String header : headers_to_propagate) {
        String value = requestHeaders.getHeaderString(header);
        if (value != null) {
          builder.header(header,value);
        }
      }
      try {
        Response r = builder.get();

        int statusCode = r.getStatusInfo().getStatusCode();
        if (statusCode == Response.Status.OK.getStatusCode()) {
          try (StringReader stringReader = new StringReader(r.readEntity(String.class));
               JsonReader jsonReader = Json.createReader(stringReader)) {
            return jsonReader.readObject();
          }
        } else {
          System.out.println("Error: unable to contact " + ratings_service + " got status of " + statusCode);
          return null;
        }
      } catch (ProcessingException e) {
        System.err.println("Error: unable to contact " + ratings_service + " got exception " + e);
        return null;
      }
    }

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok().type(MediaType.APPLICATION_JSON).entity("{\"status\": \"Reviews is healthy\"}").build();
    }

    @GET
    @Path("/reviews/{productId}")
    public Response bookReviewsById(@PathParam("productId") int productId, @Context HttpHeaders requestHeaders) {
      int starsReviewer1 = -1;
      int starsReviewer2 = -1;

      if (ratings_enabled) {
        JsonObject ratingsResponse = getRatings(Integer.toString(productId), requestHeaders);
        if (ratingsResponse != null) {
          if (ratingsResponse.containsKey("ratings")) {
            JsonObject ratings = ratingsResponse.getJsonObject("ratings");
            if (ratings.containsKey("Reviewer1")){
          	  starsReviewer1 = ratings.getInt("Reviewer1");
            }
            if (ratings.containsKey("Reviewer2")){
              starsReviewer2 = ratings.getInt("Reviewer2");
            }
          }
        }
      }

      String jsonResStr = getJsonResponse(Integer.toString(productId), starsReviewer1, starsReviewer2);
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(jsonResStr).build();
    }
}
