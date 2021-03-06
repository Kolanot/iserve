package uk.ac.open.kmi.iserve.rest.discovery;

/**
 * Created by Luca Panziera on 29/05/2014.
 */

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.gson.*;
import com.wordnik.swagger.annotations.*;
import org.apache.abdera.model.Feed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.kmi.iserve.discovery.api.DiscoveryEngine;
import uk.ac.open.kmi.iserve.discovery.api.MatchResult;
import uk.ac.open.kmi.iserve.discovery.util.CallbackEvent;
import uk.ac.open.kmi.iserve.discovery.util.Pair;
import uk.ac.open.kmi.iserve.rest.util.AbderaAtomFeedProvider;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

@Path("/")
@Api(value = "/discovery", description = "Service discovery operations", basePath = "discovery")
@Produces({"application/atom+xml", "application/json"})
public class DiscoveryEngineResource {

    /**
     * the default page size
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;
    private static EventBus callbackBus;
    @Context
    UriInfo uriInfo;
    @Context
    HttpServletRequest request;
    private Logger logger = LoggerFactory.getLogger(DiscoveryEngineResource.class);
    private DiscoveryEngine discoveryEngine;
    private DiscoveryResultsBuilderPlugin discoveryResultsBuilder;

    @Inject
    DiscoveryEngineResource(DiscoveryEngine discoveryEngine,
                            DiscoveryResultsBuilderPlugin discoveryResultsBuilder) {
        this.discoveryEngine = discoveryEngine;
        this.discoveryResultsBuilder = discoveryResultsBuilder;
        if (callbackBus == null) {
            callbackBus = discoveryEngine.getCallbackBus();
            callbackBus.register(this);
        }
    }

    @GET
    @Path("{type}/func-rdfs")
    @ApiOperation(
            value = "Get services or operations classified by specific RDF classes.",
            notes = "The search is performed through RDFS-based reasoning.",
            response = DiscoveryResult.class
    )
    @ApiResponses(
            value = {@ApiResponse(code = 200, message = "Discovery successful"),
                    @ApiResponse(code = 500, message = "Internal error")})
    @Produces({"application/atom+xml", "application/json"})
    public Response classificationBasedDiscovery(
            @ApiParam(value = "Parameter indicating the type of item to discover. The only values accepted are \"op\" for discovering operations, and \"svc\" for discovering services.", required = true, allowableValues = "svc,op")
            @PathParam("type") String type,
            @ApiParam(value = "Type of matching. The value should be either \"and\" or \"or\". The result should be the set- based conjunction or disjunction depending on the classes selected.", allowableValues = "and,or")
            @QueryParam("f") String function,
            @ApiParam(value = "Multivalued parameter indicating the functional classifications to match. The class should be the URL of the concept to match. This URL should be URL encoded.", required = true, allowMultiple = true)
            @QueryParam("class") List<String> resources,
            @ApiParam(value = "Matching type: \"subsume\" returns all the entities that are subclasses of the requested class; \"plugin\" returns all the entities that are superclasses; \"exact\" returns all the entities that are exactly tha same class", allowableValues = "subsume,plugin,exact")
            @DefaultValue("subsume") @QueryParam("matching") String matchingType,
            @ApiParam(value = "Filtering according to specific criteria.", allowableValues = "disabled,enabled")
            @DefaultValue("disabled") @QueryParam("filtering") String filtering,
            @ApiParam(value = "Popularity-based ranking. The value should be \"standard\" to rank the results according the popularity of the provider.", allowableValues = "standard,inverse")
            @QueryParam("ranking") String rankingType,
            @ApiParam(value = "Number of result page. By default, it will return the first page. Page numbering starts from 0.")
            @QueryParam("page") Integer page,
            @ApiParam(value = "Number of results per page. The default value is " + DEFAULT_PAGE_SIZE + " and the maximum value is " + MAX_PAGE_SIZE + ".")
            @QueryParam("pagesize") Integer pageSize

    ) throws
            WebApplicationException {

        String query = buildClassQuery(type, function, resources, matchingType, rankingType, filtering);
        if (request.getHeader("Accept") != null && request.getHeader("Accept").equals("application/json")) {
            return transformAsJson(invokeDiscoveryEngine(query, rankingType, page, pageSize, null));
        }
        return transformAsAtom(invokeDiscoveryEngine(query, rankingType, page, pageSize, null));

    }

    private Response transformAsAtom(Map<URI, DiscoveryResult> discoveryResultMap) {
        Feed feed = new AbderaAtomFeedProvider().generateDiscoveryFeed(uriInfo.getRequestUri().toASCIIString(), discoveryEngine.toString(), discoveryResultMap);
        return Response.ok(feed).build();
    }

    private Response transformAsJson(Map<URI, DiscoveryResult> discoveryResultMap) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(discoveryResultMap);

        return Response.ok(json).build();
    }

    private Map<URI, DiscoveryResult> invokeDiscoveryEngine(String query, String rankingType, Integer page, Integer pageSize, URL callback) {
        // Check of paging parameters consistency
        if (page == null || page < 0) {
            page = 0;
        }
        if (pageSize == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        // Invoke discovery engine
        Map<URI, Pair<Double, MatchResult>> result;
        if (callback != null) {
            result = discoveryEngine.discover(query, callback);
        } else {
            result = discoveryEngine.discover(query);
        }

        //Create paging
        List<URI> keys = Lists.newArrayList(result.keySet());
        List<List<URI>> pages = Lists.partition(keys, pageSize);
        if (page < pages.size()) {
            List<URI> pageKeys = pages.get(page);
            Map<URI, Pair<Double, MatchResult>> subResult = Maps.toMap(pageKeys, new SubMapFunction(result));
            return discoveryResultsBuilder.build(subResult, rankingType);
        } else {
            return ImmutableMap.of();
        }

    }


    @GET
    @Path("{type}/io-rdfs")
    @ApiOperation(
            value = "Get services or operations that have inputs or/and outputs classified by specific RDF classes.",
            notes = "The search is performed through RDFS-based reasoning.",
            response = DiscoveryResult.class
    )
    @ApiResponses(
            value = {@ApiResponse(code = 200, message = "Discovery successful"),
                    @ApiResponse(code = 500, message = "Internal error")})
    @Produces({"application/atom+xml", "application/json"})
    public Response ioDiscovery(
            @ApiParam(value = "Parameter indicating the type of item to discover. The only values accepted are \"op\" for discovering operations, and \"svc\" for discovering services.", required = true, allowableValues = "svc,op")
            @PathParam("type") String type,
            @ApiParam(value = "Operator function. The value should be either \"and\" or \"or\". The result should be the set- based conjunction or disjunction depending on the value selected between the services matching the inputs and those matching the outputs.", allowableValues = "and,or")
            @QueryParam("f") String function,
            @ApiParam(value = "Matching type: \"subsume\" returns all the entities that are subclasses of the requested class; \"plugin\" returns all the entities that are superclasses; \"exact\" returns all the entities that are exactly tha same class", allowableValues = "subsume,plugin,exact")
            @DefaultValue("plugin") @QueryParam("matching") String matchingType,
            @ApiParam(value = "Filtering according to specific criteria.", allowableValues = "disabled,enabled")
            @DefaultValue("disabled") @QueryParam("filtering") String filtering,
            @ApiParam(value = "Popularity-based ranking. The value should be \"standard\" to rank the results according the popularity of the provider.", allowableValues = "standard,inverse")
            @QueryParam("ranking") String rankingType,
            @ApiParam(value = "Multivalued parameter indicating the classes that the input of the service should match to. The classes are indicated with the URL of the concept to match. This URL should be URL encoded.", allowMultiple = true)
            @QueryParam("i") List<String> inputs,
            @ApiParam(value = "Multivalued parameter indicating the classes that the output of the service should match to. The classes are indicated with the URL of the concept to match. This URL should be URL encoded.", allowMultiple = true)
            @QueryParam("o") List<String> outputs,
            @ApiParam(value = "Number of result page. By default, it will return the first page. Page numbering starts from 0.")
            @QueryParam("page") Integer page,
            @ApiParam(value = "Number of results per page. The default value is " + DEFAULT_PAGE_SIZE + " and the maximum value is " + MAX_PAGE_SIZE + ".")
            @QueryParam("pagesize") Integer pageSize
    ) throws
            WebApplicationException {

        String query = buildIOQuery(type, function, matchingType, rankingType, filtering, inputs, outputs);

        if (request.getHeader("Accept") != null && request.getHeader("Accept").equals("application/json")) {
            return transformAsJson(invokeDiscoveryEngine(query, rankingType, page, pageSize, null));
        }
        return transformAsAtom(invokeDiscoveryEngine(query, rankingType, page, pageSize, null));
    }


    private String buildClassQuery(String type, String operator, List<String> resources, String matchingType, String rankingType, String filtering) {

        JsonObject query = new JsonObject();
        JsonArray parameters = new JsonArray();
        for (String resource : resources) {
            parameters.add(new JsonPrimitive(resource));
        }
        JsonObject operatorObject = new JsonObject();
        if (operator == null || operator.equals("")) {
            operator = "or";
        }
        operatorObject.add(operator, parameters);

        JsonObject functionDiscoveryObject = new JsonObject();
        functionDiscoveryObject.add("classes", operatorObject);
        functionDiscoveryObject.add("type", new JsonPrimitive(type));
        if (matchingType != null) {
            functionDiscoveryObject.add("matching", new JsonPrimitive(matchingType));
        }

        JsonObject discoveryObject = new JsonObject();

        discoveryObject.add("func-rdfs", functionDiscoveryObject);

        query.add("discovery", discoveryObject);

        if (filtering.equals("enabled")) {
            query.add("filtering", new JsonArray());
        }
        if (rankingType != null && !rankingType.equals("")) {
            query.add("ranking", new JsonPrimitive(rankingType));
        }

        logger.debug("Query: {}", query);

        return query.toString();
    }

    private String buildIOQuery(String type, String operator, String matchingType, String rankingType, String filtering, List<String> inputs, List<String> outputs) {

        JsonObject query = new JsonObject();
        JsonElement inputParameters;
        if (inputs.size() == 1) {
            inputParameters = new JsonPrimitive(inputs.get(0));
        } else {
            JsonArray array = new JsonArray();
            for (String resource : inputs) {
                array.add(new JsonPrimitive(resource));
            }
            inputParameters = array;
        }
        JsonElement outputParameters;
        if (outputs.size() == 1) {
            outputParameters = new JsonPrimitive(outputs.get(0));
        } else {
            JsonArray array = new JsonArray();
            for (String resource : outputs) {
                array.add(new JsonPrimitive(resource));
            }
            outputParameters = array;
        }

        JsonObject inputObject = null;
        if (!inputs.isEmpty() && operator != null) {
            inputObject = new JsonObject();
            inputObject.add(operator, inputParameters);
        }
        JsonObject outputObject = null;
        if (!outputs.isEmpty() && operator != null) {
            outputObject = new JsonObject();
            outputObject.add(operator, outputParameters);
        }

        JsonObject functionObject = new JsonObject();
        if (inputObject != null) {
            functionObject.add("input", inputObject);
        } else if (!inputs.isEmpty()) {
            functionObject.add("input", inputParameters);
        }

        if (outputObject != null) {
            functionObject.add("output", outputObject);
        } else if (!outputs.isEmpty()) {
            functionObject.add("output", outputParameters);
        }

        JsonObject functionDiscoveryObject = new JsonObject();
        if (!inputs.isEmpty() && !outputs.isEmpty()) {
            if (operator == null || operator.equals("")) {
                operator = "or";
            }

            functionDiscoveryObject.add(operator, functionObject);
        } else {
            functionDiscoveryObject = functionObject;
        }

        functionDiscoveryObject.add("type", new JsonPrimitive(type));
        if (matchingType != null) {
            functionDiscoveryObject.add("matching", new JsonPrimitive(matchingType));
        }

        JsonObject discoveryObject = new JsonObject();

        discoveryObject.add("io-rdfs", functionDiscoveryObject);

        query.add("discovery", discoveryObject);

        if (filtering.equals("enabled")) {
            query.add("filtering", new JsonArray());
        }
        if (rankingType != null && !rankingType.equals("")) {
            query.add("ranking", new JsonPrimitive(rankingType));
        }

        logger.debug("Query: {}", query);

        return query.toString();
    }


    @GET
    @Path("{type}/search")
    @ApiOperation(
            value = "Free text-based search of services, operations or service parts",
            response = DiscoveryResult.class
    )
    @ApiResponses(
            value = {@ApiResponse(code = 200, message = "Discovery successful"),
                    @ApiResponse(code = 500, message = "Internal error")})
    @Produces({"application/atom+xml", "application/json"})
    public Response search(
            @ApiParam(value = "Parameter indicating the type of item to discover. The only values accepted are \"op\" for discovering operations, \"svc\" for discovering services and \"all\" for any kind of service component", required = true, allowableValues = "svc,op,i,o,all")
            @PathParam("type") String type,
            @ApiParam(value = "Parameter indicating a query that specifies keywords to search. Regular expressions are allowed.", required = true)
            @QueryParam("q") String query,
            @ApiParam(value = "Number of result page. By default, it will return the first page. Page numbering starts from 0.")
            @QueryParam("page") Integer page,
            @ApiParam(value = "Number of results per page. The default value is " + DEFAULT_PAGE_SIZE + " and the maximum value is " + MAX_PAGE_SIZE + ".")
            @QueryParam("pagesize") Integer pageSize
    ) {
        logger.info("Searching {} by keywords: {}", type, query);

        //building discovery Query
        JsonObject discoveryRequest = new JsonObject();
        JsonObject functionObject = new JsonObject();
        functionObject.add("query", new JsonPrimitive(query));
        functionObject.add("type", new JsonPrimitive(type));
        discoveryRequest.add("discovery", functionObject);

        // Run discovery
        if (request.getHeader("Accept") != null && request.getHeader("Accept").equals("application/json")) {
            return transformAsJson(invokeDiscoveryEngine(discoveryRequest.toString(), null, page, pageSize, null));
        }
        return transformAsAtom(invokeDiscoveryEngine(discoveryRequest.toString(), null, page, pageSize, null));
    }

    @POST
    @ApiOperation(
            value = "Advanced discovery",
            notes = "Discovery performed by submitting a complete discovery request as JSON",
            response = DiscoveryResult.class
    )
    @ApiResponses(
            value = {@ApiResponse(code = 200, message = "Discovery successful"),
                    @ApiResponse(code = 500, message = "Internal error")})
    @Consumes("application/json")
    @Produces({"application/atom+xml", "application/json"})
    public Response advancedDiscovery(
            @ApiParam(value = "JSON document that specifies the discovery strategy", required = true)
            String discoveryRequest,
            @ApiParam(value = "Number of result page. By default, it will return the first page. Page numbering starts from 0.")
            @QueryParam("page") Integer page,
            @ApiParam(value = "Number of results per page. The default value is " + DEFAULT_PAGE_SIZE + " and the maximum value is " + MAX_PAGE_SIZE + ".")
            @QueryParam("pagesize") Integer pageSize,
            @ApiParam(value = "It will send a callback notification for changes in the discovery results to the submitted URL")
            @QueryParam("callback") String callback
    ) {
        try {
            logger.debug("Advanced discovery");
            String rankingType;
            if (discoveryRequest.contains("\"ranking\"") || discoveryRequest.contains("\"scoring\"")) {
                rankingType = "standard";
            } else {
                rankingType = "";
            }
            URL callbackUrl = null;
            if (callback != null && !callback.equals("")) {
                callbackUrl = new URL(callback);
            }
            if (request.getHeader("Accept") != null && request.getHeader("Accept").equals("application/json")) {
                return transformAsJson(invokeDiscoveryEngine(discoveryRequest, rankingType, page, pageSize, callbackUrl));
            }
            return transformAsAtom(invokeDiscoveryEngine(discoveryRequest, rankingType, page, pageSize, callbackUrl));

        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
            return Response.status(500).entity(e.getMessage()).build();
        }

    }

    @Subscribe
    @AllowConcurrentEvents
    public void sendCallback(CallbackEvent e) {
        try {
            URL callbackUrl = e.getCallback();
            Date updatedDate = e.getDate();
            logger.debug("Sending callback to {}", callbackUrl);
            HttpURLConnection connection = (HttpURLConnection) callbackUrl.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());

            // Create message
            JsonObject message = new JsonObject();
            message.add("message", new JsonPrimitive("Discovery results updated"));
            GregorianCalendar c = new GregorianCalendar();
            c.setTime(updatedDate);
            XMLGregorianCalendar xmlDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
            message.add("updated", new JsonPrimitive(xmlDate.toString()));

            //send message
            osw.write(message.toString());
            osw.flush();
            osw.close();
            logger.error(String.valueOf(connection.getResponseCode()));
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (DatatypeConfigurationException e1) {
            e1.printStackTrace();
        }
    }

    private class SubMapFunction implements Function<URI, Pair<Double, MatchResult>> {
        private Map<URI, Pair<Double, MatchResult>> map;

        public SubMapFunction(Map<URI, Pair<Double, MatchResult>> map) {
            this.map = map;
        }

        @Nullable
        @Override
        public Pair<Double, MatchResult> apply(@Nullable URI uri) {
            return map.get(uri);
        }
    }


}
