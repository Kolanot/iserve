/*
 * Created by IntelliJ IDEA.
 * User: cp3982
 * Date: 30/10/2013
 * Time: 13:50
 */
package uk.ac.open.kmi.iserve.discovery.engine.rest;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.servlet.ServletModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.kmi.iserve.core.PluginModuleLoader;
import uk.ac.open.kmi.iserve.discovery.api.MatcherPluginModule;
import uk.ac.open.kmi.iserve.discovery.api.freetextsearch.FreeTextSearchProvider;
import uk.ac.open.kmi.iserve.discovery.api.ranking.*;
import uk.ac.open.kmi.iserve.discovery.api.ranking.impl.BasicScoreComposer;
import uk.ac.open.kmi.iserve.sal.manager.impl.RegistryManagementModule;

public class DiscoveryRestModule extends ServletModule {

    private Logger logger = LoggerFactory.getLogger(DiscoveryRestModule.class);

    @Override
    protected void configureServlets() {
        logger.debug("Loading Discovery Rest module...");

        logger.debug("Loading Discovery iServe components...");


        install(new RegistryManagementModule());
        // Load all matcher plugins
        install(PluginModuleLoader.of(MatcherPluginModule.class));

        // ServletModule defines the "request" scope. GuiceFilter creates/destroys the scope on each incoming request.
        install(new ServletModule());

        install(PluginModuleLoader.of(RecommendationPluginModule.class));

//        //Scorers configuration
        Multibinder<Filter> filterBinder = Multibinder.newSetBinder(binder(), Filter.class);
        Multibinder<AtomicFilter> atomicFilterBinder = Multibinder.newSetBinder(binder(), AtomicFilter.class);

//        //Scorers configuration
        Multibinder<Scorer> scorerBinder = Multibinder.newSetBinder(binder(), Scorer.class);
        Multibinder<AtomicScorer> atomicScorerBinder = Multibinder.newSetBinder(binder(), AtomicScorer.class);

        bind(ScoreComposer.class).to(BasicScoreComposer.class);
        bind(DiscoveryResultsBuilderPlugin.class).to(DiscoveryResultsBuilder.class);

        install(new FreeTextSearchProvider());

    }

}
