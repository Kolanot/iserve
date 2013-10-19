/*
 * Copyright (c) 2013. Knowledge Media Institute - The Open University
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

package uk.ac.open.kmi.iserve.discovery.disco.impl;

import com.google.inject.multibindings.MapBinder;
import uk.ac.open.kmi.iserve.core.ConfiguredModule;
import uk.ac.open.kmi.iserve.discovery.api.ConceptMatcher;
import uk.ac.open.kmi.iserve.discovery.api.MatcherPluginModule;
import uk.ac.open.kmi.iserve.discovery.api.OperationDiscoverer;
import uk.ac.open.kmi.iserve.discovery.api.ServiceDiscoverer;
import uk.ac.open.kmi.iserve.discovery.disco.index.ConcurrentHashMapFactory;
import uk.ac.open.kmi.iserve.discovery.disco.index.DefaultConceptMatcherIndexFactory;
import uk.ac.open.kmi.iserve.discovery.disco.index.IndexFactory;
import uk.ac.open.kmi.iserve.discovery.disco.index.MapFactory;
import uk.ac.open.kmi.iserve.sal.manager.impl.iServeManagementModule;

import javax.inject.Singleton;

/**
 * DiscoMatchersPlugin is a Guice module providing a set of Matchers implementation for concept and services matching
 *
 * @author <a href="mailto:carlos.pedrinaci@open.ac.uk">Carlos Pedrinaci</a> (KMi - The Open University)
 * @since 18/09/2013
 */
public class DiscoMatchersPlugin extends ConfiguredModule implements MatcherPluginModule {

    @Override
    protected void configure() {
        // Ensure we configure it
        super.configure();
        
        // Install iServe Management
        install(new iServeManagementModule());
        
        bind(IndexFactory.class).to(DefaultConceptMatcherIndexFactory.class);
        bind(MapFactory.class).to(ConcurrentHashMapFactory.class);

        // Bind Concept Matchers
        MapBinder<String, ConceptMatcher> conceptBinder = MapBinder.newMapBinder(binder(), String.class, ConceptMatcher.class);
        conceptBinder.addBinding(SparqlLogicConceptMatcher.class.getName()).to(SparqlLogicConceptMatcher.class);
        conceptBinder.addBinding(SparqlIndexedLogicConceptMatcher.class.getName()).to(SparqlIndexedLogicConceptMatcher.class);
        conceptBinder.addBinding(IndexedLogicConceptMatcher.class.getName()).to(IndexedLogicConceptMatcher.class).in(Singleton.class);
        
        // Bind fall back Concept Matcher
        bind(ConceptMatcher.class).to(SparqlLogicConceptMatcher.class);

        // Single bind discoverers for now
        bind(OperationDiscoverer.class).to(GenericLogicDiscoverer.class);
        bind(ServiceDiscoverer.class).to(GenericLogicDiscoverer.class);
    }
}
