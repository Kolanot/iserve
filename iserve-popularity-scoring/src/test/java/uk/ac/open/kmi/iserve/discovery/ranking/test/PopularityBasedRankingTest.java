/*
 * Copyright (c) 2014. Knowledge Media Institute - The Open University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.open.kmi.iserve.discovery.ranking.test;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonParser;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;
import junit.framework.Assert;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.kmi.iserve.core.ConfigurationProperty;
import uk.ac.open.kmi.iserve.core.PluginModuleLoader;
import uk.ac.open.kmi.iserve.core.iServeProperty;
import uk.ac.open.kmi.iserve.discovery.api.DiscoveryEngine;
import uk.ac.open.kmi.iserve.discovery.api.DiscoveryEngineImpl;
import uk.ac.open.kmi.iserve.discovery.api.MatchResult;
import uk.ac.open.kmi.iserve.discovery.api.MatcherPluginModule;
import uk.ac.open.kmi.iserve.discovery.api.freetextsearch.FreeTextSearchModule;
import uk.ac.open.kmi.iserve.discovery.api.ranking.*;
import uk.ac.open.kmi.iserve.discovery.api.ranking.impl.BasicScoreComposer;
import uk.ac.open.kmi.iserve.discovery.util.Pair;
import uk.ac.open.kmi.iserve.sal.exception.ServiceException;
import uk.ac.open.kmi.iserve.sal.manager.RegistryManager;
import uk.ac.open.kmi.iserve.sal.manager.impl.RegistryManagementModule;
import uk.ac.open.kmi.msm4j.Service;
import uk.ac.open.kmi.msm4j.io.ServiceReader;
import uk.ac.open.kmi.msm4j.io.Syntax;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.net.URI;
import java.util.*;

/**
 * Created by Luca Panziera on 01/05/2014.
 */
@RunWith(JukitoRunner.class)
public class PopularityBasedRankingTest {
    private static Logger logger = LoggerFactory.getLogger(PopularityBasedRankingTest.class);

    @BeforeClass
    public static void setUp() throws Exception {


        Injector injector = Guice.createInjector(new RegistryManagementModule());

        RegistryManager registryManager = injector.getInstance(RegistryManager.class);
        ServiceReader serviceReader = injector.getInstance(ServiceReader.class);

        registryManager.clearRegistry();

        importPWapis(registryManager, serviceReader);

    }

    private static void importPWapis(RegistryManager registryManager, ServiceReader serviceReader) {


        Set<File> descriptions = new TreeSet<File>();
        File ontoDir = new File(PopularityBasedRankingTest.class.getResource("/pw-example").getFile());
        if (ontoDir.exists() && ontoDir.isDirectory() && ontoDir.listFiles().length > 0) {
            descriptions.addAll(Arrays.asList(ontoDir.listFiles(new Notation3ExtFilter())));
        }

        for (File desc : descriptions) {
            try {
                List<Service> services = serviceReader.parse(new FileInputStream(desc), "http://" + desc.getName(), Syntax.N3);
                for (Service service : services) {
                    logger.info("Loading {}", service.getUri());
                    registryManager.getServiceManager().addService(service);
                }
            } catch (ServiceException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    @Test
    public void discoveryTest(DiscoveryEngine discoveryEngine, @iServeProperty(ConfigurationProperty.SERVICES_SPARQL_UPDATE) String updateEndpoint) {

        logger.info("Free text search indexing...");
        StringBuilder updateBuilder = new StringBuilder();
        updateBuilder.append("PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ")
                .append("INSERT DATA {")
                .append("luc:include luc:setParam \"literal uri\" . ")
                .append("luc:index luc:setParam \"literals, uri\" . ")
                .append("luc:moleculeSize luc:setParam \"1\" . ")
                .append("luc:entityIndex luc:createIndex \"true\" . }");
        UpdateRequest request = UpdateFactory.create();
        request.add(updateBuilder.toString());
        UpdateProcessor processor = UpdateExecutionFactory.createRemoteForm(request, updateEndpoint);
        processor.execute();

        String query = "{\n" +
                "    \"discovery\": { \"query\": \"search\" , \"type\": \"svc\"}," +
                "    \"scoring\": [\n" +
                "        {\n" +
                "            \"scorerClass\": \"uk.ac.open.kmi.iserve.discovery.ranking.impl.ProviderPopularityScorer\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"scorerClass\": \"uk.ac.open.kmi.iserve.discovery.ranking.impl.CommunityVitalityScorer\"\n" +
                "        }\n" +
                "    ]" +
                "}";

        Stopwatch stopwatch = new Stopwatch().start();
        Map<URI, Pair<Double, MatchResult>> result = discoveryEngine.discover(new JsonParser().parse(query));
        stopwatch.stop();

        logger.info("Discovery complete in {}", stopwatch);
        logger.info("Result contains {} resources", result.size());
        ImmutableList<URI> services = ImmutableList.copyOf(result.keySet());
        Assert.assertTrue(services.get(0).toString().contains("google-book-search-book-viewability"));

        query = "{\n" +
                "    \"discovery\": {\n" +
                "        \"io-rdfs\": {\n" +
                "            \"expression\": {\n" +
                "                \"and\": {\n" +
                "                    \"input\": { \"or\": [ {\"and\": [\"http://schema.org/Action\", \"http://schema.org/DiscocverAction\"]}, \"http://schema.org/CreateAction\" ] },\n" +
                "                    \"output\": { \"and\": [ \"http://schema.org/PippoAction\", \"http://schema.org/PlutoAction\", \"http://schema.org/PaperAction\"]}\n" +
                "                }\n" +
                "            }," +
                "            \"type\": \"svc\"\n" +
                "        }\n" +
                "    }," +
                "    \"scoring\": [\n" +
                "        {\n" +
                "            \"scorerClass\": \"uk.ac.open.kmi.iserve.discovery.ranking.impl.ProviderPopularityScorer\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"scorerClass\": \"uk.ac.open.kmi.iserve.discovery.ranking.impl.CommunityVitalityScorer\"\n" +
                "        }\n" +
                "    ]" +
                "}";

        stopwatch = new Stopwatch().start();
        result = discoveryEngine.discover(new JsonParser().parse(query));
        stopwatch.stop();

        logger.info("Discovery complete in {}", stopwatch);
        logger.info("Result contains {} resources", result.size());

        Assert.assertTrue(result.isEmpty());

    }

    /**
     * JukitoModule.
     */
    public static class InnerModule extends JukitoModule {
        @Override
        protected void configureTest() {

            RegistryManagementModule rmm = new RegistryManagementModule();

            install(rmm);

            // Load all matcher plugins
            install(PluginModuleLoader.of(MatcherPluginModule.class));

            install(PluginModuleLoader.of(RecommendationPluginModule.class));

            // Scorers configuration
            Multibinder<Filter> filterBinder = Multibinder.newSetBinder(binder(), Filter.class);
            Multibinder<AtomicFilter> atomicFilterBinder = Multibinder.newSetBinder(binder(), AtomicFilter.class);

            // Scorers configuration
            Multibinder<Scorer> scorerBinder = Multibinder.newSetBinder(binder(), Scorer.class);
            Multibinder<AtomicScorer> atomicScorerBinder = Multibinder.newSetBinder(binder(), AtomicScorer.class);

            bind(ScoreComposer.class).to(BasicScoreComposer.class);

            Injector injector = Guice.createInjector(rmm);

            install(injector.getInstance(FreeTextSearchModule.class));

            bind(DiscoveryEngine.class).to(DiscoveryEngineImpl.class);

        }

    }

    public static class Notation3ExtFilter implements FilenameFilter {

        public boolean accept(File dir, String name) {
            return (name.endsWith(".n3"));
        }
    }

}